package services;

import beans.Drone;
import com.example.grpc.DroneDeliveryGrpc;
import com.example.grpc.EndElectionGrpc;
import com.example.grpc.EndElectionGrpc.*;
import com.example.grpc.Hello;
import com.example.grpc.Hello.*;
import com.example.grpc.InfoUpdatedGrpc;
import com.example.grpc.InfoUpdatedGrpc.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dronazon.Delivery;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.eclipse.paho.client.mqttv3.*;
import process.Queue;

public class EndElectionImpl extends EndElectionImplBase {
  private final Drone drone;
  private final List<Drone> list;

  public EndElectionImpl(Drone drone, List<Drone> list) {
    this.drone = drone;
    this.list = list;
  }

  @Override
  public void end(Elected request, StreamObserver<Empty> responseObserver) {
    drone.setIdMaster(request.getMaster());
    if (drone.getId() != request.getMaster()) {
      forwardElectionMessage(drone, list);
      responseObserver.onNext(Empty.newBuilder().build());
      responseObserver.onCompleted();
    } else {
      responseObserver.onNext(Empty.newBuilder().build());
      responseObserver.onCompleted();
    }
  }

  private void sendInfoToNewMaster(Drone drone, List<Drone> list) {
    Drone master = getMaster(drone, list);

    final ManagedChannel channel =
        ManagedChannelBuilder.forTarget(master.getAddress() + ":" + master.getPort())
            .usePlaintext()
            .build();

    InfoUpdatedStub stub = InfoUpdatedGrpc.newStub(channel);

    Point point = drone.getPoint();
    DroneInfo info =
        DroneInfo.newBuilder()
            .setId(drone.getId())
            .setTimestamp(drone.getTimestamp())
            .setKm(drone.getTot_km())
            .setBattery(drone.getBattery())
            .setX((int) point.getX())
            .setY((int) point.getY())
            .setTotDelivery(drone.getTot_delivery())
            .build();

    stub.message(
        info,
        new StreamObserver<Empty>() {
          @Override
          public void onNext(Empty value) {}

          @Override
          public void onError(Throwable t) {
            try {
              if (t instanceof StatusRuntimeException
                  && ((StatusRuntimeException) t).getStatus().getCode()
                      == Status.UNAVAILABLE.getCode()) {
                drone.setElection(false);
                channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                channel.shutdownNow();
              } else {
                t.printStackTrace();
              }

            } catch (Exception e) {
              e.printStackTrace();
            }
          }

          @Override
          public void onCompleted() {
            try {
              channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
            channel.shutdownNow();
          }
        });
  }

  private Drone getMaster(Drone drone, List<Drone> list) {
    return list.stream().filter(d -> d.getId() == drone.getIdMaster()).findFirst().orElse(null);
  }

  private void forwardElectionMessage(Drone drone, List<Drone> list) {
    Drone next = nextDrone(drone, list);

    Context.current()
        .fork()
        .run(
            () -> {
              final ManagedChannel channel =
                  ManagedChannelBuilder.forTarget(next.getAddress() + ":" + next.getPort())
                      .usePlaintext()
                      .build();

              EndElectionStub stub = EndElectionGrpc.newStub(channel);

              Elected elected = Elected.newBuilder().setMaster(drone.getIdMaster()).build();

              stub.end(
                  elected,
                  new StreamObserver<Empty>() {
                    @Override
                    public void onNext(Empty value) {}

                    @Override
                    public void onError(Throwable t) {
                      try {
                        if (t instanceof StatusRuntimeException
                            && ((StatusRuntimeException) t).getStatus().getCode()
                                == Status.UNAVAILABLE.getCode()) {
                          list.remove(next);
                          forwardElectionMessage(drone, list);
                          channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                        }
                      } catch (Exception e) {
                        e.printStackTrace();
                        channel.shutdownNow();
                      }
                    }

                    @Override
                    public void onCompleted() {
                      try {
                        drone.setElection(false);
                        sendInfoToNewMaster(drone, list);
                        channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                      } catch (InterruptedException e) {
                        e.printStackTrace();
                        channel.shutdownNow();
                      }
                    }
                  });
            });
  }

  private Drone nextDrone(Drone drone, List<Drone> list) {
    int index = list.indexOf(searchDroneInList(drone, list));
    return list.get((index + 1) % list.size());
  }

  private Drone searchDroneInList(Drone drone, List<Drone> list) {
    return list.stream().filter(d -> d.getId() == drone.getId()).findFirst().orElse(null);
  }

  private void startDelivery(List<Drone> list, Queue buffer) {
    new Thread(
            () -> {
              while (true) {
                try {
                  Delivery delivery = buffer.pop(list);
                  if (available(list)) {
                    Drone driver = defineDroneOfDelivery(list, delivery.getStart());
                    driver.setAvailable(false);
                    sendDelivery(delivery, driver, list);
                  } else {
                    buffer.push(delivery);
                  }

                } catch (InterruptedException e) {
                  e.printStackTrace();
                }
              }
            })
        .start();
  }

  private boolean available(List<Drone> list) {
    return list.stream().anyMatch(Drone::getAvailable);
  }

  private Drone defineDroneOfDelivery(List<Drone> list, Point start) {
    return list.stream()
        .filter(Drone::getAvailable)
        .min(Comparator.comparing(d -> d.getPoint().distance(start)))
        .orElse(null);
  }

  private void takeDelivery(Queue buffer, MqttClient client) {
    try {
      String topic = "dronazon/smartcity/orders";
      int qos = 0;

      client.setCallback(
          new MqttCallback() {

            public void messageArrived(String topic, MqttMessage message) {
              String receivedMessage = new String(message.getPayload());
              Gson gson = new GsonBuilder().create();

              Delivery delivery = gson.fromJson(receivedMessage, Delivery.class);
              buffer.push(delivery);
            }

            public void connectionLost(Throwable cause) {
              cause.printStackTrace();
            }

            public void deliveryComplete(IMqttDeliveryToken token) {}
          });

      client.subscribe(topic, qos);

    } catch (MqttException me) {
      me.printStackTrace();
    }
  }

  private void sendDelivery(Delivery delivery, Drone driver, List<Drone> list)
      throws InterruptedException {

    drone.setSafe(false);
    Drone next = nextDrone(drone, list);

    Context.current()
        .fork()
        .run(
            () -> {
              try {
                final ManagedChannel channel =
                    ManagedChannelBuilder.forTarget(next.getAddress() + ":" + next.getPort())
                        .usePlaintext()
                        .build();

                DroneDeliveryGrpc.DroneDeliveryStub stub = DroneDeliveryGrpc.newStub(channel);
                Point start = delivery.getStart();
                Point end = delivery.getEnd();

                Hello.Delivery request =
                    Hello.Delivery.newBuilder()
                        .setId(delivery.getId())
                        .setStartX(start.x)
                        .setStartY(start.y)
                        .setEndX(end.x)
                        .setEndY(end.y)
                        .setIdDriver(driver.getId())
                        .build();

                stub.delivery(
                    request,
                    new StreamObserver<Empty>() {
                      public void onNext(Empty response) {}

                      public void onError(Throwable throwable) {
                        try {
                          list.remove(next);
                          sendDelivery(delivery, driver, list);
                          channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                          channel.shutdownNow();
                        }
                      }

                      public void onCompleted() {
                        drone.setSafe(true);
                        channel.shutdown();
                      }
                    });
                channel.awaitTermination(1, TimeUnit.MINUTES);
              } catch (Exception e) {
                e.printStackTrace();
              }
            });
  }
}
