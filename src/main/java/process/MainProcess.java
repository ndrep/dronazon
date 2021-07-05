package process;

import beans.Drone;
import beans.Statistics;
import com.example.grpc.*;
import com.example.grpc.DroneMasterGrpc.*;
import com.example.grpc.DronePresentationGrpc.*;
import com.example.grpc.Hello.*;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import java.awt.Point;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.codehaus.jackson.jaxrs.JacksonJsonProvider;
import org.eclipse.paho.client.mqttv3.*;
import services.*;
import simulator.Measurement;
import simulator.PM10Simulator;

public class MainProcess {

  private final Drone drone;
  private List<Drone> list;
  private final RingController manager;
  private final DeliveryController controller;
  private static final Logger LOGGER = Logger.getLogger(MainProcess.class.getSimpleName());

  public MainProcess(Drone drone) {
    this.drone = drone;
    list = new ArrayList<>();
    manager = new RingController(list, drone);
    controller = new DeliveryController(list, drone);
  }

  public static void main(String[] args) throws MqttException {
    LOGGER.setUseParentHandlers(false);
    ConsoleHandler handler = new ConsoleHandler();
    Formatter formatter = new LogFormatter();
    handler.setFormatter(formatter);
    LOGGER.addHandler(handler);
    Random rand = new Random(System.currentTimeMillis());
    MainProcess dp =
        new MainProcess(new Drone(rand.nextInt(1000), rand.nextInt(8080), "localhost"));

    MqttClient mqtt = dp.drone.connect();

    ClientConfig clientConfig = dp.config();
    dp.drone.setClient(Client.create(clientConfig));

    dp.list = new ArrayList<>(dp.getAllDronesInRing());

    Point point = dp.getStartPoint();
    dp.drone.setPoint(point);

    dp.list.sort(Comparator.comparing(Drone::getId));

    if (dp.list.size() == 1) {
      dp.drone.setIdMaster(dp.drone.getId());
      dp.list.get(0).setPoint(dp.drone.getPoint());
    }

    try {
      dp.startAllGrpcServices(dp.drone, dp.list);
      if (dp.manager.isMaster(dp.drone.getIdMaster(), dp.drone.getId())) {
        dp.controller.takeDelivery(dp.drone.getBuffer(), mqtt);
        dp.controller.startDelivery(dp.list, dp.drone.getBuffer());
      } else {
        dp.searchMasterInList(dp.drone, dp.list);
        dp.entranceInRing(dp.drone, dp.list);
      }
      dp.sensorStart();
      dp.printDroneInfo(dp.drone, dp.list);
      dp.typeQuitToExit(dp.drone, dp.list);

    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
    }
  }

  private void sensorStart() {
    PM10Buffer pm10Buffer =
        new PM10Buffer(
            pm10 ->
                drone
                    .getBufferPM10()
                    .add(
                        pm10.readAllAndClean().stream()
                                .map(Measurement::getValue)
                                .reduce(0.0, Double::sum)
                            / 8.0));
    new PM10Simulator(pm10Buffer).start();
  }

  private void typeQuitToExit(Drone drone, List<Drone> list) throws InterruptedException {

    new Thread(
            () -> {
              Scanner sc = new Scanner(System.in);
              String input = sc.nextLine().toLowerCase(Locale.ROOT);
              while (!input.equals("quit")) {
                input = sc.nextLine().toLowerCase(Locale.ROOT);
              }

              manager.removeFromServerList(drone);

              if (manager.isMaster(drone.getId(), drone.getIdMaster())) {
                try {
                  if (drone.getMqttClient().isConnected()) {
                    drone.getMqttClient().disconnect();
                  }

                  Queue buffer = drone.getBuffer();
                  while (buffer.size() > 0
                          || !manager.available(
                          list.stream()
                                  .filter(d -> d.getId() != d.getIdMaster())
                                  .collect(Collectors.toList()))) {
                    synchronized (list) {
                      list.wait();
                    }
                  }
                } catch (MqttException | InterruptedException mqttException) {
                  mqttException.printStackTrace();
                }
                System.exit(0);
              }

              synchronized (drone) {
                if (!drone.getSafe()) {
                  try {
                    drone.wait();
                    System.exit(0);
                  } catch (InterruptedException e) {
                    e.printStackTrace();
                  }
                }
              }
              System.exit(0);
            })
        .start();
  }

  private void printDroneInfo(Drone drone, List<Drone> list) {
    new Thread(
            () -> {
              while (true) {
                try {
                  manager.checkDroneLife(drone, list);
                  Thread.sleep(10000);
                  buildMessage(drone, list);
                  if (drone.getId() == drone.getIdMaster()) {
                    WebResource webResource =
                        drone.getClient().resource("http://localhost:6789" + "/api/statistics");

                    double delivery =
                        (double)
                                list.stream()
                                    .filter(d -> !d.getTimestamp().equals("NONE"))
                                    .map(Drone::getTot_delivery)
                                    .reduce(0, Integer::sum)
                            / list.size();
                    double km =
                        list.stream()
                                .filter(d -> !d.getTimestamp().equals("NONE"))
                                .map(Drone::getTot_km)
                                .reduce(0.0, Double::sum)
                            / list.size();
                    double pm10 =
                        list.stream()
                                .filter(d -> !d.getTimestamp().equals("NONE"))
                                .map(
                                    drone1 ->
                                        drone1.getBufferPM10().stream().reduce(0.0, Double::sum)
                                            / drone1.getBufferPM10().size())
                                .reduce(0.0, Double::sum)
                            / list.size();
                    double battery =
                        (double)
                                list.stream()
                                    .filter(d -> !d.getTimestamp().equals("NONE"))
                                    .map(Drone::getBattery)
                                    .reduce(0, Integer::sum)
                            / list.size();
                    Timestamp timestamp = new Timestamp(System.currentTimeMillis());
                    Statistics statistics =
                        new Statistics(delivery, km, pm10, battery, timestamp.toString());

                    webResource.type("application/json").post(ClientResponse.class, statistics);
                  }

                } catch (InterruptedException e) {
                  e.printStackTrace();
                }
              }
            })
        .start();
  }

  private void buildMessage(Drone drone, List<Drone> list) {

    StringBuilder builder = new StringBuilder();
    builder.append("ID: ").append(drone.getId()).append("\n");
    builder.append("MASTER: ").append(drone.getIdMaster()).append("\n");
    builder.append("KM: ").append(drone.getTot_km()).append("\n");
    builder.append("DELIVERY: ").append(drone.getTot_delivery()).append("\n");
    builder.append("TIMESTAMP: ").append(drone.getTimestamp()).append("\n");
    builder.append("BATTERY: ").append(drone.getBattery()).append("\n");
    builder.append("POSITION: ").append(drone.printPoint()).append("\n");
    synchronized (drone.getBuffer()) {
      builder.append("SENSOR: ").append(drone.getBufferPM10()).append("\n");
    }
    builder.append("LIST: ").append("[\n");
    for (Drone t : list) {
      builder.append(t.toString()).append(" \n");
    }
    builder.append("]");

    LOGGER.info("\n" + builder + "\n");
  }

  private void startAllGrpcServices(Drone drone, List<Drone> list) throws IOException {
    Server service =
        ServerBuilder.forPort(drone.getPort())
            .addService(new DroneMasterImpl(drone))
            .addService(new DronePresentationImpl(drone, list))
            .addService(new DroneDeliveryImpl(drone, list))
            .addService(new InfoUpdatedImpl(drone, list))
            .addService(new DroneCheckImpl())
            .addService(new StartElectionImpl(drone, list))
            .addService(new EndElectionImpl(drone, list))
            .build();
    service.start();
  }

  private Point getStartPoint() {
    WebResource webResource = drone.getClient().resource("http://localhost:6789" + "/api/point");

    ClientResponse info_point = webResource.type("application/json").get(ClientResponse.class);

    if (info_point.getStatus() != 200) {
      throw new RuntimeException("Failed : HTTP error code : " + info_point.getStatus());
    }

    return info_point.getEntity(Point.class);
  }

  private List<Drone> getAllDronesInRing() {
    WebResource webResource = drone.getClient().resource("http://localhost:6789" + "/api/add");

    ClientResponse response =
        webResource.type("application/json").post(ClientResponse.class, drone);

    if (response.getStatus() != 200) {
      System.exit(0);
      throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
    }

    return response.getEntity(new GenericType<List<Drone>>() {});
  }

  private void searchMasterInList(Drone drone, List<Drone> list) throws InterruptedException {
    Drone next = manager.nextDrone(drone, list);

    if (next.getId() == drone.getId()){
      manager.removeFromServerList(drone);
      System.exit(0);
    }

    Context.current()
        .fork()
        .run(
            () -> {
              assert next != null;
              final ManagedChannel channel =
                  ManagedChannelBuilder.forTarget(next.getAddress() + ":" + next.getPort())
                      .usePlaintext()
                      .build();

              DroneMasterStub stub = DroneMasterGrpc.newStub(channel);

              Empty request = Empty.newBuilder().build();

              stub.master(
                  request,
                  new StreamObserver<Response>() {

                    public void onNext(Response response) {
                      drone.setIdMaster(response.getId());
                    }

                    public void onError(Throwable t) {
                      if (t instanceof StatusRuntimeException
                          && ((StatusRuntimeException) t).getStatus().getCode()
                              == Status.UNAVAILABLE.getCode()) {
                        try {
                          list.remove(next);
                          searchMasterInList(drone, list);
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

                    public void onCompleted() {
                      try {
                        channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                      } catch (InterruptedException e) {
                        channel.shutdownNow();
                        e.printStackTrace();
                      }
                    }
                  });
            });
  }

  private void entranceInRing(Drone drone, List<Drone> list) {
    List<Drone> clean =
        list.stream().filter(d -> d.getId() != drone.getId()).collect(Collectors.toList());

    if (!clean.isEmpty()) {
      Context.current()
              .fork()
              .run(
                      () -> {
                        for (Drone d : clean) {

                          final ManagedChannel channel =
                                  ManagedChannelBuilder.forTarget(d.getAddress() + ":" + d.getPort())
                                          .usePlaintext()
                                          .build();

                          DronePresentationStub stub = DronePresentationGrpc.newStub(channel);

                          Hello.Drone request =
                                  Hello.Drone.newBuilder()
                                          .setId(drone.getId())
                                          .setPort(drone.getPort())
                                          .setAddress(drone.getAddress())
                                          .setX((int) drone.getPoint().getX())
                                          .setY((int) drone.getPoint().getY())
                                          .build();

                          stub.info(
                                  request,
                                  new StreamObserver<Empty>() {
                                    public void onNext(Empty response) {
                                    }

                                    public void onError(Throwable t) {
                                      try {
                                        channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                                      } catch (InterruptedException e) {
                                        channel.shutdownNow();
                                      }
                                    }

                                    public void onCompleted() {
                                      try {
                                        channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                                      } catch (InterruptedException e) {
                                        channel.shutdownNow();
                                        e.printStackTrace();
                                      }
                                    }
                                  });
                        }
                      });
    }else {
      manager.removeFromServerList(drone);
      System.exit(0);
    }
  }

  private ClientConfig config() {
    ClientConfig clientConfig = new DefaultClientConfig();
    clientConfig.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);
    clientConfig.getClasses().add(JacksonJsonProvider.class);
    return clientConfig;
  }
}
