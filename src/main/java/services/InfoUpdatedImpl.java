package services;

import beans.Drone;
import com.example.grpc.Hello.*;
import com.example.grpc.InfoUpdatedGrpc.InfoUpdatedImplBase;
import io.grpc.stub.StreamObserver;
import java.awt.*;
import java.util.List;
import process.RingController;

public class InfoUpdatedImpl extends InfoUpdatedImplBase {
  private final List<Drone> list;
  private final Drone drone;
  private RingController manager;

  public InfoUpdatedImpl(Drone drone, List<Drone> list) {
    this.list = list;
    this.drone = drone;
    manager = new RingController(list, drone);
  }

  @Override
  public void message(DroneInfo request, StreamObserver<Empty> responseObserver) {
    updateDroneInfoInList(request);
    responseObserver.onNext(Empty.newBuilder().build());
    responseObserver.onCompleted();
  }

  private void updateDroneInfoInList(DroneInfo request) {
    Drone updated = manager.searchDroneById(request.getId(), list);
    if (request.getBattery() < 15) {
      synchronized (list) {
        list.remove(updated);
      }
    } else {
      manager.updateDroneInfoInList(request, updated);
    }
  }
}
