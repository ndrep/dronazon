package services;

import beans.Drone;
import com.example.grpc.DroneDeliveryGrpc;
import com.example.grpc.DroneDeliveryGrpc.*;
import com.example.grpc.Hello;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import process.MainProcess;
import process.Queue;
import process.RingController;

public class DroneDeliveryImpl extends DroneDeliveryImplBase {
  private final Drone drone;
  private final List<Drone> list;
  private final Client client;
  private final Queue buffer;
  private static final Logger LOGGER = Logger.getLogger(MainProcess.class.getSimpleName());
  private RingController manager;

  public DroneDeliveryImpl(Drone drone, List<Drone> list, Client client, Queue buffer) {
    this.drone = drone;
    this.list = list;
    this.client = client;
    this.buffer = buffer;
    manager = new RingController(list, drone);
  }

  @Override
  public void delivery(Delivery request, StreamObserver<Empty> responseObserver) {
    drone.setSafe(false);
    Context.current()
        .fork()
        .run(
            () -> {
              try {
                if (isDriver(request.getIdDriver(), drone.getId())) {
                  makeDelivery(request);
                } else if (driverIsDead(request)) {
                  LOGGER.info("IL DRONE " + request.getIdDriver() + " DEVE ESSERE ELIMINATO");
                  list.remove(getDriver(request));
                  dronazon.Delivery delivery = updateDelivery(request);
                  buffer.push(delivery);
                  // potrebbe capitare che il driver muore, il messaggio continua a girare nella
                  // rete,
                  // il master inserisce nuovamente il messaggio in coda e setta il driver come
                  // disponibile
                  // ma non lo trova perchè morto.
                  // if (list.contains(getDriver(request))) getDriver(request).setAvailable(true);
                } else {
                  forwardDelivery(request);
                }
              } catch (Exception e) {
                e.printStackTrace();
              }
            });
    responseObserver.onNext(Empty.newBuilder().build());
    responseObserver.onCompleted();
    drone.setSafe(true);
  }

  private Drone getDriver(Delivery request) {
    return list.stream().filter(d -> d.getId() == request.getIdDriver()).findFirst().orElse(null);
  }

  private dronazon.Delivery updateDelivery(Delivery request) {
    Point start = new Point(request.getStartX(), request.getStartY());
    Point end = new Point(request.getEndX(), request.getEndY());
    dronazon.Delivery delivery = new dronazon.Delivery(request.getId());
    delivery.setStart(start);
    delivery.setEnd(end);
    return delivery;
  }

  private boolean driverIsDead(Delivery request) {
    return drone.getId() == drone.getIdMaster() && request.getIdDriver() != drone.getIdMaster();
  }

  private boolean isDriver(int idDriver, int id) {
    return idDriver == id;
  }

  private void forwardDelivery(Delivery request) throws InterruptedException {
    drone.setSafe(false);

    Context.current()
        .fork()
        .run(
            () -> {
              Drone next = nextDrone(drone, list);

              final ManagedChannel channel =
                  ManagedChannelBuilder.forTarget(next.getAddress() + ":" + next.getPort())
                      .usePlaintext()
                      .build();

              DroneDeliveryStub stub = DroneDeliveryGrpc.newStub(channel);

              stub.delivery(
                  request,
                  new StreamObserver<Empty>() {

                    @Override
                    public void onNext(Empty response) {}

                    @Override
                    public void onError(Throwable t) {
                      try {
                        if (t instanceof StatusRuntimeException
                            && ((StatusRuntimeException) t).getStatus().getCode()
                                == Status.UNAVAILABLE.getCode()) {
                          list.remove(next);
                          forwardDelivery(request);
                        }
                      } catch (InterruptedException e) {
                        e.printStackTrace();
                      }
                      try {
                        channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                      } catch (InterruptedException e) {
                        channel.shutdownNow();
                      }
                    }

                    @Override
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
            });
  }

  private void makeDelivery(Delivery request) {
    drone.setSafe(false);
    Context.current()
        .fork()
        .run(
            () -> {
              Drone master = getMaster();

              try {
                Thread.sleep(5000);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }

              Timestamp timestamp = new Timestamp(System.currentTimeMillis());
              updateDroneInfo(request, timestamp);

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
                      // TODO caso in cui master dovesse fallire...
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
                        /*else if (drone.getBattery() < 15 && drone.getIdMaster() == drone.getId()){
                            quitMaster(drone.getClient());
                            channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                            channel.shutdownNow();
                        }

                         */
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
    WebResource webResource = client.resource("http://localhost:6789" + "/api/remove");
    ClientResponse response =
        webResource.type("application/json").post(ClientResponse.class, drone.getId());

    if (response.getStatus() != 200) {
      throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
    }
  }

  private Drone getMaster() {
    return list.stream().filter(d -> d.getId() == drone.getIdMaster()).findFirst().orElse(null);
  }

  private void updateDroneInfo(Delivery request, Timestamp timestamp) {
    drone.setBattery(drone.getBattery() - 10);
    drone.setTot_delivery(drone.getTot_delivery() + 1);
    drone.setTimestamp(timestamp.toString());
    Point start = new Point(request.getStartX(), request.getStartY());
    Point end = new Point(request.getEndX(), request.getEndY());
    double distance = drone.getPoint().distance(start) + start.distance(end);
    drone.setTot_km(drone.getTot_km() + distance);
    drone.setPoint(end);
  }

  private Drone nextDrone(Drone drone, List<Drone> list) {
    int index = list.indexOf(searchDroneInList(drone, list));
    return list.get((index + 1) % list.size());
  }

  private Drone searchDroneInList(Drone drone, List<Drone> list) {
    return list.stream().filter(d -> d.getId() == drone.getId()).findFirst().orElse(null);
  }
}
