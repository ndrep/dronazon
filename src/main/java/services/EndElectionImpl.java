package services;

import beans.Drone;
import com.example.grpc.EndElectionGrpc;
import com.example.grpc.EndElectionGrpc.*;
import com.example.grpc.Hello.*;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class EndElectionImpl extends EndElectionImplBase {
  private final Drone drone;
  private final List<Drone> list;

  public EndElectionImpl(Drone drone, List<Drone> list) {
    this.drone = drone;
    this.list = list;
  }

  @Override
  public void end(Elected request, StreamObserver<Empty> responseObserver) {
    drone.setIdMaster(request.getMaster());
    Drone next = nextDrone(drone, list);
    if (!isMaster(next, request.getMaster())) {
      endElectionMessage(drone, next, list);
    }
    responseObserver.onNext(Empty.newBuilder().build());
    responseObserver.onCompleted();
  }

  private boolean isMaster(Drone next, int master) {
    return next.getId() == master;
  }

  private void endElectionMessage(Drone drone, Drone next, List<Drone> list) {

    Context.current()
        .fork()
        .run(
            () -> {
              final ManagedChannel channel =
                  ManagedChannelBuilder.forTarget(next.getAddress() + ":" + next.getPort())
                      .usePlaintext()
                      .build();

              EndElectionStub stub = EndElectionGrpc.newStub(channel);

              Elected elected = Elected.newBuilder().setMaster(drone.getIdMaster()).build();

              stub.end(
                  elected,
                  new StreamObserver<Empty>() {
                    @Override
                    public void onNext(Empty value) {}

                    @Override
                    public void onError(Throwable t) {
                      try {
                        if (t instanceof StatusRuntimeException
                            && ((StatusRuntimeException) t).getStatus().getCode()
                                == Status.UNAVAILABLE.getCode()) {
                          list.remove(next);
                          endElectionMessage(drone, next, list);
                          channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                        }
                      } catch (Exception e) {
                        e.printStackTrace();
                        channel.shutdownNow();
                      }
                    }

                    @Override
                    public void onCompleted() {
                      try {
                        drone.setElection(false);
                        channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                      } catch (InterruptedException e) {
                        e.printStackTrace();
                        channel.shutdownNow();
                      }
                    }
                  });
            });
  }

  private Drone nextDrone(Drone drone, List<Drone> list) {
    int index = list.indexOf(searchDroneInList(drone, list));
    return list.get((index + 1) % list.size());
  }

  private Drone searchDroneInList(Drone drone, List<Drone> list) {
    return list.stream().filter(d -> isMaster(d, drone.getId())).findFirst().orElse(null);
  }
}
