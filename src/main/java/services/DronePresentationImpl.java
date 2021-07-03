package services;

import beans.Drone;
import com.example.grpc.DronePresentationGrpc.*;
import com.example.grpc.Hello;
import com.example.grpc.Hello.Empty;
import io.grpc.stub.StreamObserver;
import java.awt.*;
import java.util.Comparator;
import java.util.List;
import process.RingController;

public class DronePresentationImpl extends DronePresentationImplBase {

  private final List<Drone> list;
  private final RingController manager;

  public DronePresentationImpl(Drone drone, List<Drone> list) {
    this.list = list;
    manager = new RingController(list, drone);
  }

  @Override
  public void info(Hello.Drone request, StreamObserver<Empty> responseObserver) {
    try {
      Drone drone = new Drone(request.getId(), request.getPort(), request.getAddress());
      if (manager.isMasterDrone()) {
        drone.setPoint(new Point(request.getX(), request.getY()));
        drone.setAvailable(true);
        synchronized (list) {
          list.notifyAll();
        }
      }
      list.add(drone);
      list.sort(Comparator.comparing(Drone::getId));
    } catch (Exception e) {
      e.printStackTrace();
    }
    responseObserver.onNext(Empty.newBuilder().build());
    responseObserver.onCompleted();
  }
}
