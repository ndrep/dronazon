package process;

import beans.Drone;
import com.example.grpc.CheckDroneGrpc;
import com.example.grpc.Hello;
import com.example.grpc.StartElectionGrpc;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import io.grpc.*;
import io.grpc.stub.StreamObserver;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class RingController {
  private final List<Drone> list;
  private final Drone drone;

  public RingController(List<Drone> list, Drone drone) {
    this.list = list;
    this.drone = drone;
  }

  boolean available(List<Drone> list) {
    return list.stream().anyMatch(Drone::getAvailable);
  }

    Drone defineDroneOfDelivery(List<Drone> list, Point start) {
        List<Drone> tmp = new ArrayList<>(list);
        tmp.sort(Comparator.comparing(Drone::getBattery).thenComparing(Drone::getId));
        tmp.sort(Collections.reverseOrder());
        return tmp.stream()
                .filter(Drone::getAvailable)
                .min(Comparator.comparing(d -> d.getPoint().distance(start)))
                .orElse(null);
    }

  void removeFromServerList(Drone drone, Client client) {
    WebResource webResource = client.resource("http://localhost:6789" + "/api/remove");
    ClientResponse response =
            webResource.type("application/json").post(ClientResponse.class, drone.getId());

    if (response.getStatus() != 200) {
      throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
    }
  }

  Drone nextDrone(Drone drone, List<Drone> list) {
    int index = list.indexOf(searchDroneInList(drone, list));
    return list.get((index + 1) % list.size());
  }

  boolean isMaster(int idMaster, int id) {
    return idMaster == id;
  }

  void checkDroneLife(Drone drone, List<Drone> list) throws InterruptedException {
    Drone next = nextDrone(drone, list);

    Context.current()
            .fork()
            .run(
                    () -> {
                      final ManagedChannel channel =
                              ManagedChannelBuilder.forTarget(next.getAddress() + ":" + next.getPort())
                                      .usePlaintext()
                                      .build();

                      CheckDroneGrpc.CheckDroneStub stub = CheckDroneGrpc.newStub(channel);

                      Hello.Empty request = Hello.Empty.newBuilder().build();

                      stub.check(
                              request,
                              new StreamObserver<Hello.Empty>() {
                                @Override
                                public void onNext(Hello.Empty value) {}

                                @Override
                                public void onError(Throwable t) {
                                  if (t instanceof StatusRuntimeException
                                          && ((StatusRuntimeException) t).getStatus().getCode()
                                          == Status.UNAVAILABLE.getCode()) {
                                    list.remove(next);
                                    try {
                                      if (isMaster(next.getId(), drone.getIdMaster())) {
                                        drone.setElection(true);
                                        startElectionMessage(drone, list);
                                      } else {
                                        checkDroneLife(drone, list);
                                      }
                                    } catch (InterruptedException e) {
                                      e.printStackTrace();
                                    }
                                  } else {
                                    t.printStackTrace();
                                  }
                                  try {
                                    channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                                  } catch (InterruptedException e) {
                                    channel.shutdownNow();
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

  private void startElectionMessage(Drone drone, List<Drone> list) throws InterruptedException {
    Drone next = nextDrone(drone, list);

    final ManagedChannel channel =
            ManagedChannelBuilder.forTarget(next.getAddress() + ":" + next.getPort())
                    .usePlaintext()
                    .build();

    StartElectionGrpc.StartElectionStub stub = StartElectionGrpc.newStub(channel);

    Hello.Election election =
            Hello.Election.newBuilder().setId(drone.getId()).setBattery(drone.getBattery()).build();

    stub.start(
            election,
            new StreamObserver<Hello.Empty>() {
              @Override
              public void onNext(Hello.Empty value) {}

              @Override
              public void onError(Throwable t) {

                if (t instanceof StatusRuntimeException
                        && ((StatusRuntimeException) t).getStatus().getCode()
                        == Status.UNAVAILABLE.getCode()) {
                  try {
                    startElectionMessage(drone, list);
                  } catch (InterruptedException e) {
                    e.printStackTrace();
                  }
                }
                try {
                  channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                  channel.shutdownNow();
                }
              }

              @Override
              public void onCompleted() {
                try {
                  drone.setElection(true);
                  channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                  channel.shutdownNow();
                }
              }
            });
    channel.shutdown();
  }


  private Drone searchDroneInList(Drone drone, List<Drone> list) {
    return list.stream().filter(d -> d.getId() == drone.getId()).findFirst().orElse(null);
  }
}
