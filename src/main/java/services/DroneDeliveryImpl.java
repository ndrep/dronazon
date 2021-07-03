package services;

import beans.Drone;
import com.example.grpc.DroneDeliveryGrpc.*;
import com.example.grpc.Hello.*;
import com.example.grpc.InfoUpdatedGrpc;
import com.example.grpc.InfoUpdatedGrpc.*;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import java.awt.*;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import process.DeliveryController;
import process.Queue;
import process.RingController;

public class DroneDeliveryImpl extends DroneDeliveryImplBase {
  private final Drone drone;
  private final List<Drone> list;
  private final Queue buffer;
  private final RingController manager;
  private final DeliveryController controller;
  private static final Logger LOGGER = Logger.getLogger(DroneDeliveryImpl.class.getSimpleName());

  public DroneDeliveryImpl(Drone drone, List<Drone> list, Queue buffer) {
    this.drone = drone;
    this.list = list;
    this.buffer = buffer;
    manager = new RingController(list, drone);
    controller = new DeliveryController(list, drone);
  }

  @Override
  public void delivery(Delivery request, StreamObserver<Empty> responseObserver) {
    drone.setSafe(false);
    Context.current()
        .fork()
        .run(
            () -> {
              try {
                if (controller.isDriver(request.getIdDriver(), drone.getId())) {
                  makeDelivery(request);
                } else if (controller.driverIsDead(request)) {
                  LOGGER.info("IL DRONE " + request.getIdDriver() + " DEVE ESSERE ELIMINATO");
                  list.remove(manager.getDriver(request));
                  dronazon.Delivery delivery = updateDelivery(request);
                  buffer.push(delivery);
                } else {
                  controller.forwardDelivery(request);
                }
              } catch (Exception e) {
                e.printStackTrace();
              }
            });
    responseObserver.onNext(Empty.newBuilder().build());
    responseObserver.onCompleted();
    drone.setSafe(true);
  }

  private dronazon.Delivery updateDelivery(Delivery request) {
    Point start = new Point(request.getStartX(), request.getStartY());
    Point end = new Point(request.getEndX(), request.getEndY());
    dronazon.Delivery delivery = new dronazon.Delivery(request.getId());
    delivery.setStart(start);
    delivery.setEnd(end);
    return delivery;
  }

  private void makeDelivery(Delivery request) {
    drone.setSafe(false);
    Context.current()
        .fork()
        .run(
            () -> {
              Drone master = manager.getMaster(drone, list);

              try {
                Thread.sleep(5000);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }

              Timestamp timestamp = new Timestamp(System.currentTimeMillis());
              manager.updateDroneInfo(request, timestamp);

              assert master != null;

              final ManagedChannel channel =
                  ManagedChannelBuilder.forTarget(master.getAddress() + ":" + master.getPort())
                      .usePlaintext()
                      .build();

              InfoUpdatedStub stub = InfoUpdatedGrpc.newStub(channel);

              DroneInfo info =
                  DroneInfo.newBuilder()
                      .setId(drone.getId())
                      .setTimestamp(drone.getTimestamp())
                      .setKm(drone.getTot_km())
                      .setBattery(drone.getBattery())
                      .setX(request.getEndX())
                      .setY(request.getEndY())
                      .setTotDelivery(drone.getTot_delivery())
                      .addAllPm10(drone.getBufferPM10())
                      .build();

              stub.message(
                  info,
                  new StreamObserver<Empty>() {
                    @Override
                    public void onNext(Empty value) {}

                    @Override
                    public void onError(Throwable t) {
                      try {
                        channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                      } catch (InterruptedException e) {
                        channel.shutdownNow();
                      }
                    }

                    @Override
                    public void onCompleted() {
                      try {
                        if (drone.getBattery() < 15 && drone.getIdMaster() != drone.getId()) {
                          removeFromServerList();

                          synchronized (drone) {
                            if (!drone.getSafe()) {
                              try {
                                drone.wait();
                              } catch (InterruptedException e) {
                                e.printStackTrace();
                              }
                            }
                          }

                          channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                          channel.shutdownNow();
                          System.exit(0);
                        }
                        drone.setSafe(true);
                      } catch (InterruptedException e) {
                        e.printStackTrace();
                      }
                      try {
                        channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                      } catch (InterruptedException e) {
                        channel.shutdownNow();
                      }
                    }
                  });
            });
  }

  private void removeFromServerList() {
    WebResource webResource = drone.getClient().resource("http://localhost:6789" + "/api/remove");
    ClientResponse response =
        webResource.type("application/json").post(ClientResponse.class, drone.getId());

    if (response.getStatus() != 200) {
      throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
    }
  }
}
