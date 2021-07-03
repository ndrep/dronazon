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
import java.sql.Timestamp;
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

  public void updateDroneInfo(Hello.Delivery request, Timestamp timestamp) {
    drone.setBattery(drone.getBattery() - 10);
    drone.setTot_delivery(drone.getTot_delivery() + 1);
    drone.setTimestamp(timestamp.toString());
    Point start = new Point(request.getStartX(), request.getStartY());
    Point end = new Point(request.getEndX(), request.getEndY());
    double distance = drone.getPoint().distance(start) + start.distance(end);
    drone.setTot_km(drone.getTot_km() + distance);
    drone.setPoint(end);
  }

  public Drone getMaster(Drone drone, List<Drone> list) {
    return list.stream().filter(d -> d.getId() == drone.getIdMaster()).findFirst().orElse(null);
  }

  public Drone getDriver(Hello.Delivery request) {
    return list.stream().filter(d -> d.getId() == request.getIdDriver()).findFirst().orElse(null);
  }

  public void updateNewMasterInList() {
    Drone master = searchDroneInList(drone, list);
    master.setElection(false);
    master.setAvailable(true);
    master.setIdMaster(drone.getId());
    master.setBattery(drone.getBattery());
    master.setPoint(drone.getPoint());
    master.setTot_km(drone.getTot_km());
    master.setTot_delivery(drone.getTot_delivery());
    master.setTimestamp(drone.getTimestamp());
  }

  public boolean available(List<Drone> list) {
    synchronized (list) {
      return list.stream().anyMatch(Drone::getAvailable);
    }
  }

  public boolean isMasterDrone() {
    return drone.getIdMaster() == drone.getId();
  }

  public Drone defineDroneOfDelivery(List<Drone> list, Point start) {
    List<Drone> tmp = new ArrayList<>(list);
    tmp.sort(Comparator.comparing(Drone::getBattery).thenComparing(Drone::getId));
    tmp.sort(Collections.reverseOrder());
    return tmp.stream()
        .filter(Drone::getAvailable)
        .min(Comparator.comparing(d -> d.getPoint().distance(start)))
        .orElse(null);
  }

  public void removeFromServerList(Drone drone) {
    WebResource webResource = drone.getClient().resource("http://localhost:6789" + "/api/remove");
    ClientResponse response =
        webResource.type("application/json").post(ClientResponse.class, drone.getId());

    if (response.getStatus() != 200) {
      throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
    }
  }

  public Drone nextDrone(Drone drone, List<Drone> list) {
    int index = list.indexOf(searchDroneInList(drone, list));
    return list.get((index + 1) % list.size());
  }

  public boolean isMaster(int idMaster, int id) {
    return idMaster == id;
  }

  public void checkDroneLife(Drone drone, List<Drone> list) throws InterruptedException {
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

  public void startElectionMessage(Drone drone, List<Drone> list) throws InterruptedException {
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

  public Drone searchDroneInList(Drone drone, List<Drone> list) {
    return list.stream().filter(d -> d.getId() == drone.getId()).findFirst().orElse(null);
  }
}
