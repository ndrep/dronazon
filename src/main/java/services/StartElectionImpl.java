package services;

import beans.Drone;
import com.example.grpc.EndElectionGrpc;
import com.example.grpc.Hello.*;
import com.example.grpc.StartElectionGrpc;
import com.example.grpc.StartElectionGrpc.*;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import process.MainProcess;
import process.RingController;

public class StartElectionImpl extends StartElectionImplBase {
  private final Drone drone;
  private final List<Drone> list;
  private final RingController manager;
  private static final Logger LOGGER = Logger.getLogger(MainProcess.class.getSimpleName());

  public StartElectionImpl(Drone drone, List<Drone> list) {
    this.drone = drone;
    this.list = list;
    manager = new RingController(list, drone);
  }

  @Override
  public void start(Election request, StreamObserver<Empty> responseObserver) {
    Drone tmp = new Drone();
    tmp.setId(request.getId());
    tmp.setBattery(request.getBattery());

    try {
      if (tmp.getId() == drone.getId()) {
        LOGGER.info("SONO IL NUOVO MASTER");
        drone.setIdMaster(drone.getId());
        endElectionMessage(drone, list);
      } else if (tmp.compareTo(drone) > 0) {
        LOGGER.info("SONO PIU' PICCOLO");
        startElectionMessage(drone, tmp, list);
      } else {
        LOGGER.info("SONO PIU' GRANDE");
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
    drone.setSafe(false);
    Drone next = manager.nextDrone(drone, list);

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
                      try {
                        if (t instanceof StatusRuntimeException
                            && ((StatusRuntimeException) t).getStatus().getCode()
                                == Status.UNAVAILABLE.getCode()) {
                          list.remove(next);
                          startElectionMessage(drone, tmp, list);
                          channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                        } else t.printStackTrace();
                      } catch (Exception e) {
                        channel.shutdownNow();
                        e.printStackTrace();
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
            });
  }

  private void endElectionMessage(Drone drone, List<Drone> list) {
    Drone next = manager.nextDrone(drone, list);

    Context.current()
        .fork()
        .run(
            () -> {
              final ManagedChannel channel =
                  ManagedChannelBuilder.forTarget(next.getAddress() + ":" + next.getPort())
                      .usePlaintext()
                      .build();

              EndElectionGrpc.EndElectionStub stub = EndElectionGrpc.newStub(channel);

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
                          endElectionMessage(drone, list);
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
}
