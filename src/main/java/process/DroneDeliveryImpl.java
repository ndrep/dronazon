package process;

import beans.Drone;
import com.example.grpc.DroneDeliveryGrpc;
import com.example.grpc.DroneDeliveryGrpc.*;
import com.example.grpc.Hello.*;
import com.example.grpc.InfoUpdatedGrpc;
import com.example.grpc.InfoUpdatedGrpc.*;
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
  private static final Logger LOGGER = Logger.getLogger(ClientDrone.class.getName());

  public DroneDeliveryImpl(Drone drone, List<Drone> list) {
    this.drone = drone;
    this.list = list;
  }

  @Override
  public void delivery(Delivery request, StreamObserver<Empty> responseObserver) {
    Context.current()
        .fork()
        .run(
            () -> {
              try {
                if (isDriver(request.getIdDriver(), drone.getId())) {
                  makeDelivery(request);
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
        list.stream()
            .filter(d -> isDriver(d.getId(), drone.getIdMaster()))
            .findFirst()
            .orElse(null);

    Drone updated = list.get(list.indexOf(searchDroneById(request.getIdDriver())));
    updated.setBattery(updated.getBattery() - 10);

    LOGGER.info("\nsto consegnando...\n");
    Thread.sleep(10000);

    assert master != null;

    final ManagedChannel channel =
        ManagedChannelBuilder.forTarget(master.getAddress() + ":" + master.getPort())
            .usePlaintext()
            .build();

    InfoUpdatedStub stub = InfoUpdatedGrpc.newStub(channel);

    Timestamp timestamp = new Timestamp(System.currentTimeMillis());
    Point start = new Point(request.getStartX(), request.getStartY());
    Point end = new Point(request.getEndX(), request.getEndY());
    double distance = updated.getPoint().distance(start) + start.distance(end);

    DroneInfo info =
        DroneInfo.newBuilder()
            .setId(drone.getId())
            .setTimestamp(timestamp.toString())
            .setKm(distance)
            .setBattery(updated.getBattery())
            .setX(request.getEndX())
            .setY(request.getEndY())
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
            channel.shutdownNow();
          }
        });
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
}
