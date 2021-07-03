package services;

import beans.Drone;
import com.example.grpc.Hello.*;
import com.example.grpc.InfoUpdatedGrpc.InfoUpdatedImplBase;
import io.grpc.stub.StreamObserver;
import java.awt.*;
import java.util.List;
import process.QuitMasterThread;
import process.RingController;

public class InfoUpdatedImpl extends InfoUpdatedImplBase {
  private final List<Drone> list;
  private final Drone drone;
  private final RingController manager;


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
    Drone updated = searchDroneInList(request.getId(), list);
    updateDroneInfo(request, updated);
    if (request.getBattery() < 15 && request.getId() != drone.getIdMaster()) {
      synchronized (list) {
        list.remove(updated);
      }
    } else if (request.getBattery() < 15 && request.getId() == drone.getIdMaster()) {
      updated.setAvailable(false);
      new QuitMasterThread(drone,list).start();
    } else {
      synchronized (list) {
        list.notifyAll();
      }
    }
  }

  private void updateDroneInfo(DroneInfo request, Drone updated) {
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
}
