package process;

import beans.Drone;
import com.example.grpc.DroneDeliveryGrpc;
import com.example.grpc.DroneDeliveryGrpc.*;
import com.example.grpc.Hello.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DroneDeliveryImpl extends DroneDeliveryImplBase {
  private final Drone drone;
  private final List<Drone> list;

  public DroneDeliveryImpl(Drone drone, List<Drone> list) {
    this.drone = drone;
    this.list = list;
  }

  @Override
  public void delivery(Delivery request, StreamObserver<Statistics> responseObserver) {

    try {
      if (request.getIdDriver() == drone.getId()) {

        System.out.println("Consegna effettuata da " + drone.getId());

        Thread.sleep(10000);

        Drone updated = list.get(list.indexOf(searchDroneInList(request.getIdDriver())));
        updated.setBattery(updated.getBattery() - 10);

        Statistics stat =
            Statistics.newBuilder()
                .setId(request.getIdDriver())
                .setStartX(request.getEndX())
                .setStartY(request.getEndY())
                .setPower(updated.getBattery())
                .build();

        responseObserver.onNext(stat);

        responseObserver.onCompleted();

      } else {
        Drone next = getNextDrone(list);

        final ManagedChannel channel =
            ManagedChannelBuilder.forTarget(next.getAddress() + ":" + next.getPort())
                .usePlaintext()
                .build();

        DroneDeliveryStub stub = DroneDeliveryGrpc.newStub(channel);

        stub.delivery(
            request,
            new StreamObserver<Statistics>() {
              public void onNext(Statistics response) {}

              public void onError(Throwable throwable) {
                System.out.println("Error! " + throwable.getMessage());
              }

              public void onCompleted() {
                channel.shutdownNow();
              }
            });
        channel.awaitTermination(1, TimeUnit.SECONDS);
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  private Drone searchDroneInList(int id) {
    return list.stream().filter(d -> d.getId() == id).findFirst().get();
  }

  private Drone getNextDrone(List<Drone> list) {
    int index = list.indexOf(findDrone(list));
    return list.get((index + 1) % list.size());
  }

  private Drone findDrone(List<Drone> list) {
    return list.stream().filter(d -> d.getId() == drone.getId()).findFirst().orElse(null);
  }
}
