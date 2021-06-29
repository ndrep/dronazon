package services;

import beans.Drone;
import com.example.grpc.DroneMasterGrpc.DroneMasterImplBase;
import com.example.grpc.Hello.*;
import io.grpc.stub.StreamObserver;
import java.util.List;

public class DroneMasterImpl extends DroneMasterImplBase {
  private final List<Drone> list;
  private final Drone drone;

  public DroneMasterImpl(Drone drone, List<Drone> list) {
    this.drone = drone;
    this.list = list;
  }

  @Override
  public void master(Empty request, StreamObserver<Response> responseObserver) {
    Response response = Response.newBuilder().setId(drone.getIdMaster()).build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }
}