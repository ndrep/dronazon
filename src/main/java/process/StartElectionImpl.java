package process;

import beans.Drone;
import com.example.grpc.Hello.*;
import com.example.grpc.StartElectionGrpc;
import com.example.grpc.StartElectionGrpc.*;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class StartElectionImpl extends StartElectionImplBase {
  private final Drone drone;
  private final List<Drone> list;
  private static final Logger LOGGER = Logger.getLogger(DroneProcess.class.getSimpleName());

  public StartElectionImpl(Drone drone, List<Drone> list) {
    this.drone = drone;
    this.list = list;
  }

  @Override
  public void start(Election request, StreamObserver<Empty> responseObserver) {
    Drone tmp = new Drone();
    tmp.setId(request.getId());
    tmp.setBattery(request.getBattery());

      System.out.println(tmp.getId());
      System.out.println(drone.getId());

    try {
      if (tmp.getId() == drone.getId()) {
          searchDroneInList(drone, list).setIdMaster(drone.getId());
      } else if (tmp.compareTo(drone) > 0) {
          LOGGER.info("IO SONO <");
        startElectionMessage(drone, tmp, list);
      } else {
          LOGGER.info("IO SONO >");
        startElectionMessage(drone, drone, list);
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    responseObserver.onNext(Empty.newBuilder().build());
    responseObserver.onCompleted();
  }

  private void startElectionMessage(Drone drone, Drone tmp, List<Drone> list)
      throws InterruptedException {
    Drone next = nextDrone(drone, list);


    Context.current()
        .fork()
        .run(
            () -> {
              final ManagedChannel channel =
                  ManagedChannelBuilder.forTarget(next.getAddress() + ":" + next.getPort())
                      .usePlaintext()
                      .build();

              StartElectionStub stub = StartElectionGrpc.newStub(channel);

              Election election =
                  Election.newBuilder().setId(tmp.getId()).setBattery(tmp.getBattery()).build();

              stub.start(
                  election,
                  new StreamObserver<Empty>() {
                    @Override
                    public void onNext(Empty value) {}

                    @Override
                    public void onError(Throwable t) {
                      if (t instanceof StatusRuntimeException
                          && ((StatusRuntimeException) t).getStatus().getCode()
                              == Status.UNAVAILABLE.getCode()) {
                        try {
                          startElectionMessage(drone, tmp, list);
                          channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                          channel.shutdownNow();
                          e.printStackTrace();
                        }
                      } else {
                        t.printStackTrace();
                      }
                    }

                    @Override
                    public void onCompleted() {
                      try {
                        channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                      } catch (InterruptedException e) {
                        channel.shutdownNow();
                      }
                    }
                  });
              channel.shutdown();
            });
  }

  private Drone nextDrone(Drone drone, List<Drone> list) {
    int index = list.indexOf(searchDroneInList(drone, list));
    return list.get((index + 1) % list.size());
  }

  private Drone searchDroneInList(Drone drone, List<Drone> list) {
    return list.stream().filter(d -> d.getId() == drone.getId()).findFirst().orElse(null);
  }
}
