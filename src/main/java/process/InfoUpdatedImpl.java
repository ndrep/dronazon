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
    Drone updated = getDrone(request);
    if (updated.getBattery() < 15) {
      list.remove(updated);
    } else {
      updated.setAvailable(true);
      updated.setBattery(request.getBattery());
      updated.setPoint(new Point(request.getX(), request.getY()));
      updated.setTot_km(request.getKm());
      updated.setTot_delivery(request.getTotDelivery());
      updated.setTimestamp(request.getTimestamp());
    }
  }

  private Drone getDrone(DroneInfo request) {
    return list.get(list.indexOf(searchDroneInList(request.getId(), list)));
  }

  private Drone searchDroneInList(int id, List<Drone> list) {
    return list.stream().filter(d -> d.getId() == id).findFirst().get();
  }
}
