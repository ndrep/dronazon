package services;

import beans.Drone;
import com.example.grpc.DroneDeliveryGrpc;
import com.example.grpc.DroneDeliveryGrpc.*;
import com.example.grpc.Hello.*;
import com.example.grpc.InfoUpdatedGrpc;
import com.example.grpc.InfoUpdatedGrpc.*;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import process.DroneProcess;
import process.Queue;

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
                  drone.setAvailable(true);
                } else {
                  forwardDelivery(request);
                }
              } catch (Exception e) {
                e.printStackTrace();
              }

              responseObserver.onNext(Empty.newBuilder().build());
              responseObserver.onCompleted();
            });
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

  private boolean driverIsDead(Delivery request) {
    return drone.getId() == drone.getIdMaster() && request.getIdDriver() != drone.getIdMaster();
  }

  private boolean isDriver(int idDriver, int id) {
    return idDriver == id;
  }

  private void forwardDelivery(Delivery request) throws InterruptedException {
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
              if (t instanceof StatusRuntimeException
                  && ((StatusRuntimeException) t).getStatus().getCode()
                      == Status.UNAVAILABLE.getCode()){
                list.remove(next);
                forwardDelivery(request);
              }
              channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
              channel.shutdownNow();
            }
          }

          @Override
          public void onCompleted() {
            channel.shutdownNow();
          }

        });
  }

  private void makeDelivery(Delivery request) throws InterruptedException {
    Drone master =
        list.stream().filter(d -> d.getId() == drone.getIdMaster()).findFirst().orElse(null);

    Thread.sleep(5000);

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
            String url = "http://localhost:6789";
            if (drone.getBattery() < 15 && drone.getId() != drone.getIdMaster()) {
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

  private Drone getNextDrone(List<Drone> list) {
    int index = list.indexOf(findDrone(list));
    return list.get((index + 1) % list.size());
  }

  private Drone findDrone(List<Drone> list) {
    return list.stream().filter(d -> isDriver(d.getId(), drone.getId())).findFirst().orElse(null);
  }
}
