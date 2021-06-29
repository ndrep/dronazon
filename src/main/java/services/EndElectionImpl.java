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
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
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
  private final Client client;

  public EndElectionImpl(Drone drone, List<Drone> list, Client client) {
    this.drone = drone;
    this.list = list;
    this.client = client;
  }

  @Override
  public void end(Elected request, StreamObserver<Empty> responseObserver) {
    if (drone.getId() == drone.getIdMaster() && !drone.getElection()) {
      list.forEach(d -> d.setAvailable(false));
      drone.setAvailable(true);
      drone.setElection(true);
      forwardElectionMessage(drone, list);
      responseObserver.onNext(Empty.newBuilder().build());
      responseObserver.onCompleted();
    } else if (drone.getId() != drone.getIdMaster()) {
      drone.setIdMaster(request.getMaster());
      forwardElectionMessage(drone, list);
      responseObserver.onNext(Empty.newBuilder().build());
      responseObserver.onCompleted();
    } else {
      try {
        drone.setElection(false);
        updateNewMasterInList();
        takeDelivery(drone.getBuffer(), drone.connect());
        startDelivery(list, drone.getBuffer());
        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
      } catch (MqttException e) {
        e.printStackTrace();
      }
    }
  }

  private void updateNewMasterInList() {
    Drone master = searchDroneInList(drone, list);
    master.setElection(false);
    master.setIdMaster(drone.getId());
    master.setBattery(drone.getBattery());
    master.setPoint(drone.getPoint());
    master.setTot_km(drone.getTot_km());
    master.setTot_delivery(drone.getTot_delivery());
    master.setTimestamp(drone.getTimestamp());
  }

  private void sendInfoToNewMaster(Drone drone, List<Drone> list) {
    Drone master = getMaster(drone, list);
    Context.current()
        .fork()
        .run(
            () -> {
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
                          channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                        }
                      } catch (Exception e) {
                        channel.shutdownNow();
                        e.printStackTrace();
                      }
                    }

                    @Override
                    public void onCompleted() {
                      try {
                        channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                      } catch (InterruptedException e) {
                        channel.shutdownNow();
                        e.printStackTrace();
                      }
                    }
                  });
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
                  Delivery delivery = buffer.pop();
                  if (list.isEmpty()) {
                    quit(list, buffer, delivery);
                  }
                  if (available(list)) {
                    Drone driver = defineDroneOfDelivery(list, delivery.getStart());
                    driver.setAvailable(false);
                    sendDelivery(delivery, driver, list);
                  } else {
                    buffer.push(delivery);
                  }
                  if (list.size() == 1) {
                    list.get(0).setAvailable(true);
                  }

                } catch (InterruptedException | MqttException e) {
                  e.printStackTrace();
                }
              }
            })
        .start();
  }

  private void quit(List<Drone> list, Queue buffer, Delivery delivery)
      throws MqttException, InterruptedException {
    drone.getClient().disconnect();
    while (buffer.size() > 0) {
      Thread.sleep(5000);
      if (available(list)) {
        Drone driver = defineDroneOfDelivery(list, delivery.getStart());
        driver.setAvailable(false);
        sendDelivery(delivery, driver, list);
      } else {
        buffer.push(delivery);
      }
    }
    removeFromServerList(drone, client);
    synchronized (drone) {
      if (!drone.getSafe()) {
        try {
          drone.wait();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }
    System.exit(0);
  }

  private void removeFromServerList(Drone drone, Client client) {
    WebResource webResource = client.resource("http://localhost:6789" + "/api/remove");
    ClientResponse response =
        webResource.type("application/json").post(ClientResponse.class, drone.getId());

    if (response.getStatus() != 200) {
      throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
    }
  }

  private boolean available(List<Drone> list) {
    synchronized (list) {
      return list.stream().anyMatch(Drone::getAvailable);
    }
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

                      public void onError(Throwable t) {
                        try {
                          if (t instanceof StatusRuntimeException
                              && ((StatusRuntimeException) t).getStatus().getCode()
                                  == Status.UNAVAILABLE.getCode()) {

                            list.remove(next);

                            sendDelivery(delivery, driver, list);
                            channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                          }
                        } catch (InterruptedException e) {
                          channel.shutdownNow();
                        }
                      }

                      public void onCompleted() {
                        drone.setSafe(true);
                        try {
                          channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                          channel.shutdownNow();
                          e.printStackTrace();
                        }
                      }
                    });
              } catch (Exception e) {
                e.printStackTrace();
              }
            });
  }
}
