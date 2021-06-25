package process;

import beans.Drone;
import com.example.grpc.CheckDroneGrpc;
import com.example.grpc.DroneDeliveryGrpc;
import com.example.grpc.DroneDeliveryGrpc.*;
import com.example.grpc.Hello.*;
import com.example.grpc.InfoUpdatedGrpc;
import com.example.grpc.InfoUpdatedGrpc.*;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.awt.*;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class DroneDeliveryImpl extends DroneDeliveryImplBase {
  private final Drone drone;
  private final List<Drone> list;
  private final Client client;
  private final Queue buffer;
  private static final Logger LOGGER = Logger.getLogger(DroneProcess.class.getSimpleName());

  public DroneDeliveryImpl(Drone drone, List<Drone> list, Client client, Queue buffer) {
    this.drone = drone;
    this.list = list;
    this.client = client;
    this.buffer = buffer;
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
                  dronazon.Delivery delivery = updateDelivery(request);
                  buffer.push(delivery);
                } else {
                  forwardDelivery(request);
                }
              } catch (Exception e) {
                e.printStackTrace();
              }

              responseObserver.onNext(Empty.newBuilder().build());
              responseObserver.onCompleted();
            });
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
    checkDroneLife(drone, list);

    Drone next = getNextDrone(list);

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
              forwardDelivery(request);
              channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
              channel.shutdownNow();
            }
          }

          @Override
          public void onCompleted() {
            drone.setSafe(true);
            channel.shutdownNow();
          }
        });
  }

  private void makeDelivery(Delivery request) throws InterruptedException {
    int IdMaster =
        list.stream()
            .filter(d -> d.getId() == drone.getId())
            .findFirst()
            .orElse(null)
            .getIdMaster();

    Drone master = list.stream().filter(d -> d.getId() == IdMaster).findFirst().orElse(null);

    Drone updated = list.get(list.indexOf(searchDroneById(request.getIdDriver())));

    Thread.sleep(5000);

    Timestamp timestamp = new Timestamp(System.currentTimeMillis());
    updateDroneInfo(request, updated, timestamp);

    assert master != null;

    final ManagedChannel channel =
        ManagedChannelBuilder.forTarget(master.getAddress() + ":" + master.getPort())
            .usePlaintext()
            .build();

    InfoUpdatedStub stub = InfoUpdatedGrpc.newStub(channel);

    DroneInfo info =
        DroneInfo.newBuilder()
            .setId(updated.getId())
            .setTimestamp(timestamp.toString())
            .setKm(updated.getTot_km())
            .setBattery(updated.getBattery())
            .setX(request.getEndX())
            .setY(request.getEndY())
            .setTotDelivery(updated.getTot_delivery())
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
            String url = "http://localhost:6789";
            if (updated.getBattery() < 15 && updated.getId() != updated.getIdMaster()) {
              WebResource webResource = client.resource(url + "/api/remove");
              ClientResponse response =
                  webResource.type("application/json").post(ClientResponse.class, drone.getId());

              if (response.getStatus() != 200) {
                throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
              }
              channel.shutdownNow();
              System.exit(0);
            }
            channel.shutdownNow();
          }
        });
  }

  private void updateDroneInfo(Delivery request, Drone updated, Timestamp timestamp) {
    updated.setBattery(updated.getBattery() - 10);
    updated.setTot_delivery(updated.getTot_delivery() + 1);
    updated.setTimestamp(timestamp.toString());
    Point start = new Point(request.getStartX(), request.getStartY());
    Point end = new Point(request.getEndX(), request.getEndY());
    double distance = updated.getPoint().distance(start) + start.distance(end);
    updated.setTot_km(updated.getTot_km() + distance);
    updated.setPoint(end);
  }

  private Drone searchDroneById(int id) {
    return list.stream().filter(d -> isDriver(d.getId(), id)).findFirst().get();
  }

  private Drone getNextDrone(List<Drone> list) {
    int index = list.indexOf(findDrone(list));
    return list.get((index + 1) % list.size());
  }

  private Drone findDrone(List<Drone> list) {
    return list.stream().filter(d -> isDriver(d.getId(), drone.getId())).findFirst().orElse(null);
  }

  private void checkDroneLife(Drone drone, List<Drone> list) throws InterruptedException {
    Drone next = findDrone(list);

    Context.current()
        .fork()
        .run(
            () -> {
              try {

                final ManagedChannel channel =
                    ManagedChannelBuilder.forTarget(next.getAddress() + ":" + next.getPort())
                        .usePlaintext()
                        .build();

                CheckDroneGrpc.CheckDroneStub stub = CheckDroneGrpc.newStub(channel);

                Empty request = Empty.newBuilder().build();

                stub.check(
                    request,
                    new StreamObserver<Empty>() {
                      @Override
                      public void onNext(Empty value) {}

                      @Override
                      public void onError(Throwable t) {
                        try {
                          list.remove(next);
                          checkDroneLife(drone, list);
                          channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                          channel.shutdownNow();
                        }
                      }

                      @Override
                      public void onCompleted() {
                        channel.shutdown();
                      }
                    });
              } catch (Exception e) {
                e.printStackTrace();
              }
            });
  }
}
