package process;

import beans.Drone;
import com.example.grpc.DronePresentationGrpc.*;
import com.example.grpc.Hello;
import com.example.grpc.Hello.Empty;
import io.grpc.stub.StreamObserver;
import java.awt.*;
import java.util.Comparator;
import java.util.List;

public class DronePresentationImpl extends DronePresentationImplBase {

  private final List<Drone> list;
  private final Drone drone;

  public DronePresentationImpl(Drone drone, List<Drone> list) {
    this.drone = drone;
    this.list = list;
  }

  @Override
  public void info(Hello.Drone request, StreamObserver<Empty> responseObserver) {
    try {
      Drone inDrone = new Drone(request.getId(), request.getPort(), request.getAddress());

      if (isMasterDrone()) inDrone.setPoint(new Point(request.getX(), request.getY()));

      list.add(inDrone);

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
