package process;

import beans.Drone;
import com.example.grpc.DronePresentationGrpc.*;
import com.example.grpc.Hello;
import com.example.grpc.Hello.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.grpc.stub.StreamObserver;

import java.awt.*;
import java.util.Comparator;
import java.util.List;

public class DronePresentationImpl extends DronePresentationImplBase {

  private final List<Drone> list;

  public DronePresentationImpl(List<Drone> list) {
    this.list = list;
  }

  @Override
  public void info(Hello.Drone request, StreamObserver<Empty> responseObserver) {
    Drone drone = new Drone(request.getId(), request.getPort(), request.getAddress());
    drone.setPoint(new Point(request.getX(), request.getY()));

    list.add(drone);

    list.sort(Comparator.comparing(Drone::getId));

    printlist(list);
  }

  private static void printlist(List<Drone> list){
    GsonBuilder builder = new GsonBuilder().setPrettyPrinting();
    Gson gson = builder.create();
    System.out.println(gson.toJson(list));
  }
}
