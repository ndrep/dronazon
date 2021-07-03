package services;

import beans.Drone;
import com.example.grpc.DronePresentationGrpc.*;
import com.example.grpc.Hello;
import com.example.grpc.Hello.Empty;
import io.grpc.stub.StreamObserver;
import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import process.RingController;

public class DronePresentationImpl extends DronePresentationImplBase {

  private final List<Drone> list;
  private final Drone drone;
  private final RingController manager;
  private static final Logger LOGGER =
      Logger.getLogger(DronePresentationImpl.class.getSimpleName());

  public DronePresentationImpl(Drone drone, List<Drone> list) {
    this.drone = drone;
    this.list = list;
    manager = new RingController(list, drone);
  }

  @Override
  public void info(Hello.Drone request, StreamObserver<Empty> responseObserver) {
    try {
      Drone hello = new Drone(request.getId(), request.getPort(), request.getAddress());
      if (isMasterDrone()) {
        hello.setPoint(new Point(request.getX(), request.getY()));
        hello.setAvailable(true);
        synchronized (list){
          list.notifyAll();
        }

      }
      list.add(hello);
      list.sort(Comparator.comparing(Drone::getId));
    } catch (Exception e) {
      e.printStackTrace();
    }
    responseObserver.onNext(Empty.newBuilder().build());
    responseObserver.onCompleted();
  }

  private boolean isMasterDrone() {
    return drone.getIdMaster() == drone.getId();
  }
}
