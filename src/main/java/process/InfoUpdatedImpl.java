package process;

import beans.Drone;
import com.example.grpc.Hello.*;
import com.example.grpc.InfoUpdatedGrpc.InfoUpdatedImplBase;
import io.grpc.stub.StreamObserver;
import java.awt.*;
import java.util.List;
import java.util.logging.Logger;

public class InfoUpdatedImpl extends InfoUpdatedImplBase {
  private final List<Drone> list;
  private final Drone drone;
  private static final Logger LOGGER = Logger.getLogger(ClientDrone.class.getName());


  public InfoUpdatedImpl(Drone drone, List<Drone> list) {
    this.list = list;
    this.drone = drone;
  }

  @Override
  public void message(DroneInfo request, StreamObserver<Empty> responseObserver) {
    LOGGER.info("TIMESTAMP: " + request.getTimestamp() + "\n");
    LOGGER.info("KM: " + request.getKm() + "\n");

    list.get(list.indexOf(searchDroneInList(request.getId(), list))).setAvailable(true);
    list.get(list.indexOf(searchDroneInList(request.getId(), list)))
        .setBattery(request.getBattery());
    list.get(list.indexOf(searchDroneInList(request.getId(), list)))
        .setPoint(new Point(request.getX(), request.getY()));

    responseObserver.onNext(Empty.newBuilder().build());

    responseObserver.onCompleted();
  }

  private Drone searchDroneInList(int id, List<Drone> list) {
    return list.stream().filter(d -> d.getId() == id).findFirst().get();
  }
}
