package process;

import beans.Drone;
import com.example.grpc.Hello.*;
import com.example.grpc.InfoUpdatedGrpc.InfoUpdatedImplBase;
import io.grpc.stub.StreamObserver;
import java.awt.*;
import java.util.List;

public class InfoUpdatedImpl extends InfoUpdatedImplBase {
  private final List<Drone> list;
  private final Drone drone;

  public InfoUpdatedImpl(Drone drone, List<Drone> list) {
    this.list = list;
    this.drone = drone;
  }

  @Override
  public void message(DroneInfo request, StreamObserver<Empty> responseObserver) {

    updateDroneInfoInList(request);

    responseObserver.onNext(Empty.newBuilder().build());
    responseObserver.onCompleted();
  }

  private void updateDroneInfoInList(DroneInfo request) {
    list.get(list.indexOf(searchDroneInList(request.getId(), list))).setAvailable(true);
    list.get(list.indexOf(searchDroneInList(request.getId(), list)))
        .setBattery(request.getBattery());
    list.get(list.indexOf(searchDroneInList(request.getId(), list)))
        .setPoint(new Point(request.getX(), request.getY()));
    list.get(list.indexOf(searchDroneInList(request.getId(), list))).setTot_km(request.getKm());
    list.get(list.indexOf(searchDroneInList(request.getId(), list)))
        .setTot_delivery(request.getTotDelivery());
    list.get(list.indexOf(searchDroneInList(request.getId(), list)))
        .setTimestamp(request.getTimestamp());
  }

  private Drone searchDroneInList(int id, List<Drone> list) {
    return list.stream().filter(d -> d.getId() == id).findFirst().get();
  }
}
