package services;

import beans.Drone;
import com.example.grpc.DroneDeliveryGrpc;
import com.example.grpc.Hello;
import com.example.grpc.Hello.*;
import com.example.grpc.InfoUpdatedGrpc.InfoUpdatedImplBase;
import com.sun.jersey.api.client.Client;
import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.awt.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import process.Queue;
import process.RingController;

public class InfoUpdatedImpl extends InfoUpdatedImplBase {
  private final List<Drone> list;
  private final Drone drone;
  private final RingController manager;
  private final Client client;
  private static final Logger LOGGER = Logger.getLogger(InfoUpdatedImpl.class.getSimpleName());

  public InfoUpdatedImpl(Drone drone, List<Drone> list, Client client) {
    this.list = list;
    this.drone = drone;
    this.client = client;
    manager = new RingController(list, drone);
  }

  @Override
  public void message(DroneInfo request, StreamObserver<Empty> responseObserver) {
    updateDroneInfoInList(request);
    responseObserver.onNext(Empty.newBuilder().build());
    responseObserver.onCompleted();
  }

  private void updateDroneInfoInList(DroneInfo request) {
    Drone updated = searchDroneInList(request.getId(), list);
    updateDroneInfoInList(request, updated);
    if (request.getBattery() < 15 && request.getId() != drone.getIdMaster()) {
      synchronized (list) {
        list.remove(updated);
      }
    } else if (request.getBattery() < 15 && request.getId() == drone.getIdMaster()) {
      updated.setAvailable(false);
      quitMaster(drone.getClient());
    } else {
      manager.free(list);
    }
  }

  public void quitMaster(MqttClient mqttClient) {
    try {
      mqttClient.disconnect();
      Queue buffer = drone.getBuffer();
      while (buffer.size() > 0) {
        if (!manager.available(list)) {
          synchronized (list) {
            list.wait();
          }
        }
      }
    } catch (MqttException | InterruptedException mqttException) {
      mqttException.printStackTrace();
    }

    manager.removeFromServerList(drone, client);

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

  private void updateDroneInfoInList(DroneInfo request, Drone updated) {
    synchronized (list) {
      updated.setAvailable(true);
      updated.setBattery(request.getBattery());
      updated.setPoint(new Point(request.getX(), request.getY()));
      updated.setTot_km(request.getKm());
      updated.setTot_delivery(request.getTotDelivery());
      updated.setTimestamp(request.getTimestamp());
      updated.setBufferPM10(request.getPm10List());
    }
  }

  private Drone searchDroneInList(int id, List<Drone> list) {
    return list.stream().filter(d -> d.getId() == id).findFirst().orElse(null);
  }

  private void sendDelivery(dronazon.Delivery delivery, Drone driver, List<Drone> list)
      throws InterruptedException {

    drone.setSafe(false);
    Drone next = manager.nextDrone(drone, list);

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
                          if (next.getId() != drone.getIdMaster()) {
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
                          channel.shutdown();
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
