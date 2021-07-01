package process;

import beans.Drone;
import beans.Statistics;
import com.example.grpc.*;
import com.example.grpc.CheckDroneGrpc;
import com.example.grpc.CheckDroneGrpc.CheckDroneStub;
import com.example.grpc.DroneDeliveryGrpc.*;
import com.example.grpc.DroneMasterGrpc.*;
import com.example.grpc.DronePresentationGrpc.*;
import com.example.grpc.Hello.*;
import com.example.grpc.StartElectionGrpc.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;
import dronazon.Delivery;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import java.awt.Point;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.codehaus.jackson.jaxrs.JacksonJsonProvider;
import org.eclipse.paho.client.mqttv3.*;
import services.*;
import simulator.Measurement;
import simulator.PM10Simulator;

public class MainProcess {

  private final Drone drone;
  private static final Logger LOGGER = Logger.getLogger(MainProcess.class.getSimpleName());

  public MainProcess(Drone drone) {
    this.drone = drone;
  }

  public Drone getDrone() {
    return drone;
  }

  public static void main(String[] args) throws MqttException {
    Random rand = new Random(System.currentTimeMillis());
    MainProcess dp =
        new MainProcess(new Drone(rand.nextInt(1000), rand.nextInt(8080) + 1000, "localhost"));
    Drone drone = dp.getDrone();
    MqttClient mqtt = drone.connect();

    ClientConfig clientConfig = dp.config();
    Client client = Client.create(clientConfig);

    List<Drone> list = dp.getAllDronesInRing(client);
    Point point = dp.getStartPoint(client);

    drone.setPoint(point);

    list.sort(Comparator.comparing(Drone::getId));

    if (list.size() == 1) {
      drone.setIdMaster(drone.getId());
      list.get(0).setPoint(drone.getPoint());
    }

    try {
      dp.startAllGrpcServices(drone, list, client, drone.getBuffer());
      if (dp.isMaster(drone.getIdMaster(), drone.getId())) {
        dp.takeDelivery(drone.getBuffer(), mqtt);
        dp.startDelivery(list, drone.getBuffer(), client);
      } else {
        dp.searchMasterInList(drone, list);
        dp.greeting(drone, list);
      }
      dp.sensorStart();
      dp.printInfo(drone, list, client);
      dp.cleanBeforeQuit(drone, client, mqtt, list);

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

  private void startDelivery(List<Drone> list, Queue buffer, Client client) {
    new Thread(
            () -> {
              while (true) {
                try {
                  Delivery delivery = buffer.pop();
                  if (drone.getBattery() < 15 || list.size() == 0) {
                    cleanBeforeQuit(list, buffer, client, delivery);
                  }
                  if (available(list)) {
                    Drone driver = defineDroneOfDelivery(list, delivery.getStart());
                    driver.setAvailable(false);
                    sendDelivery(delivery, driver, list);
                  } else {
                    buffer.push(delivery);
                  }
                  if (list.size() == 1) {
                    list.get(0).setAvailable(true);
                  }

                } catch (InterruptedException | MqttException e) {
                  e.printStackTrace();
                }
              }
            })
        .start();
  }

  private void cleanBeforeQuit(List<Drone> list, Queue buffer, Client client, Delivery delivery)
      throws MqttException, InterruptedException {
    drone.getClient().disconnect();
    while (buffer.size() > 0) {
      Thread.sleep(5000);
      if (available(list)) {
        Drone driver = defineDroneOfDelivery(list, delivery.getStart());
        driver.setAvailable(false);
        sendDelivery(delivery, driver, list);
      } else {
        buffer.push(delivery);
      }
    }
    removeFromServerList(drone, client);
    synchronized (drone) {
      if (!drone.getSafe()) {
        try {
          drone.wait();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }
    System.exit(0);
  }

  private boolean available(List<Drone> list) {
    return list.stream().anyMatch(Drone::getAvailable);
  }

  private void cleanBeforeQuit(Drone drone, Client client, MqttClient mqttClient, List<Drone> list)
      throws InterruptedException {

    new Thread(
            () -> {
              Scanner sc = new Scanner(System.in);
              String input = sc.nextLine().toLowerCase(Locale.ROOT);
              while (!input.equals("quit")) {
                input = sc.nextLine().toLowerCase(Locale.ROOT);
              }
              if (isMaster(drone.getId(), drone.getIdMaster())) {
                try {
                  mqttClient.disconnect();
                  Queue buffer = drone.getBuffer();
                  while (buffer.size() > 0) {
                    Delivery delivery = buffer.pop();
                    Thread.sleep(5000);
                    if (available(list)) {
                      Drone driver = defineDroneOfDelivery(list, delivery.getStart());
                      driver.setAvailable(false);
                      sendDelivery(delivery, driver, list);
                    } else {
                      buffer.push(delivery);
                    }
                  }

                } catch (MqttException | InterruptedException e) {
                  e.printStackTrace();
                }
              }

              removeFromServerList(drone, client);

              synchronized (drone) {
                if (!drone.getSafe()) {
                  try {
                    drone.wait();
                  } catch (InterruptedException e) {
                    e.printStackTrace();
                  }
                }
              }
              System.exit(0);
            })
        .start();
  }

  private void removeFromServerList(Drone drone, Client client) {
    WebResource webResource = client.resource("http://localhost:6789" + "/api/remove");
    ClientResponse response =
        webResource.type("application/json").post(ClientResponse.class, drone.getId());

    if (response.getStatus() != 200) {
      throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
    }
  }

  private void printInfo(Drone drone, List<Drone> list, Client client) {
    new Thread(
            () -> {
              while (true) {
                try {
                  checkDroneLife(drone, list);
                  Thread.sleep(10000);
                  buildMessage(drone, list);
                  if (drone.getId() == drone.getIdMaster()) {
                    WebResource webResource =
                        client.resource("http://localhost:6789" + "/api/statistics");

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
                        new Statistics(
                            delivery,
                            km,
                            pm10,
                            battery,
                            timestamp.toString());

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
    builder.append("SENSOR: ").append(drone.getBufferPM10()).append("\n");
    builder.append("LIST: ").append("[\n");
    for (Drone t : list) {
      builder.append(t.toString()).append(" \n");
    }
    builder.append("]");

    LOGGER.info("\n" + builder + "\n");
  }

  private void startAllGrpcServices(Drone drone, List<Drone> list, Client client, Queue buffer)
      throws IOException {
    Server service =
        ServerBuilder.forPort(drone.getPort())
            .addService(new DroneMasterImpl(drone, list))
            .addService(new DronePresentationImpl(drone, list))
            .addService(new DroneDeliveryImpl(drone, list, client, buffer))
            .addService(new InfoUpdatedImpl(drone, list))
            .addService(new DroneCheckImpl())
            .addService(new StartElectionImpl(drone, list))
            .addService(new EndElectionImpl(drone, list, client))
            .build();
    service.start();
  }

  private boolean isMaster(int idMaster, int id) {
    return idMaster == id;
  }

  private Point getStartPoint(Client client) {
    WebResource webResource = client.resource("http://localhost:6789" + "/api/point");

    ClientResponse info_point = webResource.type("application/json").get(ClientResponse.class);

    if (info_point.getStatus() != 200) {
      throw new RuntimeException("Failed : HTTP error code : " + info_point.getStatus());
    }

    return info_point.getEntity(Point.class);
  }

  private List<Drone> getAllDronesInRing(Client client) {
    WebResource webResource = client.resource("http://localhost:6789" + "/api/add");

    ClientResponse response =
        webResource.type("application/json").post(ClientResponse.class, drone);

    if (response.getStatus() != 200) {
      System.exit(0);
      throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
    }

    return response.getEntity(new GenericType<List<Drone>>() {});
  }

  private Drone searchDroneInList(Drone drone, List<Drone> list) {
    return list.stream().filter(d -> d.getId() == drone.getId()).findFirst().orElse(null);
  }

  private void searchMasterInList(Drone drone, List<Drone> list) throws InterruptedException {
    Drone next = nextDrone(drone, list);

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

  private Drone nextDrone(Drone drone, List<Drone> list) {
    int index = list.indexOf(searchDroneInList(drone, list));
    return list.get((index + 1) % list.size());
  }

  private void greeting(Drone drone, List<Drone> list) throws InterruptedException {
    List<Drone> clean =
        list.stream().filter(d -> d.getId() != drone.getId()).collect(Collectors.toList());

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
                      public void onNext(Empty response) {}

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
  }

  private ClientConfig config() {
    ClientConfig clientConfig = new DefaultClientConfig();
    clientConfig.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);
    clientConfig.getClasses().add(JacksonJsonProvider.class);
    return clientConfig;
  }

  private void takeDelivery(Queue buffer, MqttClient client) {
    try {
      String topic = "dronazon/smartcity/orders";
      int qos = 0;

      client.setCallback(
          new MqttCallback() {

            public void messageArrived(String topic, MqttMessage message) {
              String receivedMessage = new String(message.getPayload());
              Gson gson = new GsonBuilder().create();

              Delivery delivery = gson.fromJson(receivedMessage, Delivery.class);
              buffer.push(delivery);
            }

            public void connectionLost(Throwable cause) {
              cause.printStackTrace();
            }

            public void deliveryComplete(IMqttDeliveryToken token) {}
          });

      client.subscribe(topic, qos);

    } catch (MqttException me) {
      me.printStackTrace();
    }
  }

  private void sendDelivery(Delivery delivery, Drone driver, List<Drone> list)
      throws InterruptedException {

    drone.setSafe(false);
    Drone next = nextDrone(drone, list);

    Context.current()
        .fork()
        .run(
            () -> {
              try {
                final ManagedChannel channel =
                    ManagedChannelBuilder.forTarget(next.getAddress() + ":" + next.getPort())
                        .usePlaintext()
                        .build();

                DroneDeliveryStub stub = DroneDeliveryGrpc.newStub(channel);
                Point start = delivery.getStart();
                Point end = delivery.getEnd();

                Hello.Delivery request =
                    Hello.Delivery.newBuilder()
                        .setId(delivery.getId())
                        .setStartX(start.x)
                        .setStartY(start.y)
                        .setEndX(end.x)
                        .setEndY(end.y)
                        .setIdDriver(driver.getId())
                        .build();

                stub.delivery(
                    request,
                    new StreamObserver<Empty>() {
                      public void onNext(Empty response) {}

                      public void onError(Throwable throwable) {
                        try {
                          list.remove(next);
                          sendDelivery(delivery, driver, list);
                          channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                          channel.shutdownNow();
                        }
                      }

                      public void onCompleted() {
                        drone.setSafe(true);
                        try {
                          channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                          channel.shutdown();
                          e.printStackTrace();
                        }
                      }
                    });
              } catch (Exception e) {
                e.printStackTrace();
              }
            });
  }

  private void checkDroneLife(Drone drone, List<Drone> list) throws InterruptedException {
    Drone next = nextDrone(drone, list);

    Context.current()
        .fork()
        .run(
            () -> {
              final ManagedChannel channel =
                  ManagedChannelBuilder.forTarget(next.getAddress() + ":" + next.getPort())
                      .usePlaintext()
                      .build();

              CheckDroneStub stub = CheckDroneGrpc.newStub(channel);

              Empty request = Empty.newBuilder().build();

              stub.check(
                  request,
                  new StreamObserver<Empty>() {
                    @Override
                    public void onNext(Empty value) {}

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

    StartElectionStub stub = StartElectionGrpc.newStub(channel);

    Election election =
        Election.newBuilder().setId(drone.getId()).setBattery(drone.getBattery()).build();

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

  private Drone defineDroneOfDelivery(List<Drone> list, Point start) {
    list.sort(Comparator.comparing(Drone::getBattery).thenComparing(Drone::getId));
    list.sort(Collections.reverseOrder());
    return list.stream()
        .filter(Drone::getAvailable)
        .min(Comparator.comparing(d -> d.getPoint().distance(start)))
        .orElse(null);
  }
}
