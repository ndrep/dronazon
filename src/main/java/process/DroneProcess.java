package process;

import beans.Drone;
import com.example.grpc.*;
import com.example.grpc.CheckDroneGrpc;
import com.example.grpc.CheckDroneGrpc.CheckDroneStub;
import com.example.grpc.DroneDeliveryGrpc.*;
import com.example.grpc.DroneMasterGrpc.*;
import com.example.grpc.DronePresentationGrpc.*;
import com.example.grpc.Hello.*;
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
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.codehaus.jackson.jaxrs.JacksonJsonProvider;
import org.eclipse.paho.client.mqttv3.*;

public class DroneProcess {

  private final Drone drone;
  private static final Logger LOGGER = Logger.getLogger(DroneProcess.class.getSimpleName());

  public DroneProcess(Drone drone) {
    this.drone = drone;
  }

  public Drone getDrone() {
    return drone;
  }

  public static void main(String[] args) {
    String url = "http://localhost:6789";
    Random rand = new Random(System.currentTimeMillis());
    DroneProcess dp =
        new DroneProcess(new Drone(rand.nextInt(100), rand.nextInt(8080) + 1000, "localhost"));
    Drone drone = dp.getDrone();

    ClientConfig clientConfig = dp.config();
    Client client = Client.create(clientConfig);

    List<Drone> list = dp.getAllDronesInRing(url, client);
    Point point = dp.getStartPoint(url, client);

    Drone dlist = dp.searchDroneInList(drone, list);

    dlist.setPoint(point);

    list.sort(Comparator.comparing(Drone::getId));

    if (list.size() == 1) {
      dlist.setIdMaster(drone.getId());
      dlist.setBattery(100000);
      drone.setIdMaster(drone.getId());
    }

    Queue buffer = new Queue();

    try {
      dp.startAllGrpcServices(drone, list, client);
      if (dp.isMaster(drone.getIdMaster(), drone.getId())) {

        dp.startDelivery(list, buffer);

        new Thread(() -> {

            while (true) {
                Delivery delivery = buffer.pop(list);
                Drone driver = defineDroneOfDelivery(list, delivery.getStart());

                try {
                    dp.sendDelivery(delivery, driver.getId(), list);
                    driver.setAvailable(false);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        }).start();

      } else {
        dp.searchMasterInList(drone, list);
        dp.greeting(drone, list);
      }

      dp.printInfo(drone, list);

      dp.quit(url, drone, client);
      System.exit(0);

    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
    }
  }

  private void quit(String url, Drone drone, Client client) throws InterruptedException {
    Thread t =
        new Thread(
            () -> {
              Scanner sc = new Scanner(System.in);
              String input = sc.nextLine().toLowerCase(Locale.ROOT);
              while (!input.equals("quit")) {
                input = sc.nextLine().toLowerCase(Locale.ROOT);
              }
              WebResource webResource = client.resource(url + "/api/remove");
              ClientResponse response =
                  webResource.type("application/json").post(ClientResponse.class, drone.getId());

              if (response.getStatus() != 200) {
                throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
              }
            });
    t.start();
    t.join();
  }

  private void printInfo(Drone drone, List<Drone> list) {
    Thread print =
        new Thread(
            () -> {
              while (true) {
                try {

                  checkDroneLife(drone, list);
                  Thread.sleep(10000);
                  buildMessage(drone, list);

                } catch (InterruptedException e) {
                  e.printStackTrace();
                }
              }
            });

    print.start();
  }

    private void buildMessage(Drone drone, List<Drone> list) {
        StringBuilder builder = new StringBuilder();
        Drone d = searchDroneInList(drone, list);
        builder.append("ID: ").append(d.getId()).append("\n");
        builder.append("MASTER: ").append(d.getIdMaster()).append("\n");
        builder.append("KM: ").append(d.getTot_km()).append("\n");
        builder.append("DELIVERY: ").append(d.getTot_delivery()).append("\n");
        builder.append("TIMESTAMP: ").append(d.getTimestamp()).append("\n");
        builder.append("BATTERY: ").append(d.getBattery()).append("\n");
        builder.append("POSITION: ").append(d.printPoint()).append("\n");
        builder.append("LIST: ").append("[");
        for (Drone t : list) {
          builder.append(t.toString());
        }
        builder.append("]");
        LOGGER.info("\n" + builder + "\n");
    }

    private void startAllGrpcServices(Drone drone, List<Drone> list, Client client)
      throws IOException {
    Server service =
        ServerBuilder.forPort(drone.getPort())
            .addService(new DroneMasterImpl(drone, list))
            .addService(new DronePresentationImpl(drone, list))
            .addService(new DroneDeliveryImpl(drone, list, client))
            .addService(new InfoUpdatedImpl(drone, list))
            .addService(new DroneCheckImpl())
            .build();
    service.start();
  }

  private boolean isMaster(int idMaster, int id) {
    return idMaster == id;
  }

  private Point getStartPoint(String url, Client client) {
    WebResource webResource = client.resource(url + "/api/point");

    ClientResponse info_point = webResource.type("application/json").get(ClientResponse.class);

    if (info_point.getStatus() != 200) {
      throw new RuntimeException("Failed : HTTP error code : " + info_point.getStatus());
    }

    return info_point.getEntity(Point.class);
  }

  private List<Drone> getAllDronesInRing(String url, Client client) {
    WebResource webResource = client.resource(url + "/api/add");

    ClientResponse response =
        webResource.type("application/json").post(ClientResponse.class, drone);

    if (response.getStatus() != 200) {
      throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
    }

    return response.getEntity(new GenericType<List<Drone>>() {});
  }

  private Drone searchDroneInList(Drone drone, List<Drone> list) {
    return list.stream().filter(d -> isMaster(d.getId(), drone.getId())).findFirst().orElse(null);
  }

  private void searchMasterInList(Drone drone, List<Drone> list) throws InterruptedException {
    Drone next = nextDrone(drone, list);

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
            searchDroneInList(drone, list).setIdMaster(response.getId());
          }

          public void onError(Throwable throwable) {
            try {
              channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
              channel.shutdownNow();
            }
          }

          public void onCompleted() {
            channel.shutdownNow();
          }
        });
    channel.awaitTermination(1, TimeUnit.SECONDS);
  }

  private Drone nextDrone(Drone drone, List<Drone> list) {
    int index = list.indexOf(searchDroneInList(drone, list));
    return list.get((index + 1) % list.size());
  }

  private void greeting(Drone drone, List<Drone> list) throws InterruptedException {
    Drone dr = searchDroneInList(drone, list);
    List<Drone> clean =
        list.stream().filter(d -> d.getId() != dr.getId()).collect(Collectors.toList());

    for (Drone d : clean) {

      final ManagedChannel channel =
          ManagedChannelBuilder.forTarget(d.getAddress() + ":" + d.getPort())
              .usePlaintext()
              .build();

      DronePresentationStub stub = DronePresentationGrpc.newStub(channel);

      Hello.Drone request =
          Hello.Drone.newBuilder()
              .setId(dr.getId())
              .setPort(dr.getPort())
              .setAddress(dr.getAddress())
              .setX((int) dr.getPoint().getX())
              .setY((int) dr.getPoint().getY())
              .build();

      stub.info(
          request,
          new StreamObserver<Empty>() {
            public void onNext(Empty response) {}

            public void onError(Throwable throwable) {
              try {
                channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                channel.shutdownNow();
              }
            }

            public void onCompleted() {
              channel.shutdown();
            }
          });
      channel.awaitTermination(1, TimeUnit.MINUTES);
    }
  }

  private ClientConfig config() {
    ClientConfig clientConfig = new DefaultClientConfig();
    clientConfig.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);
    clientConfig.getClasses().add(JacksonJsonProvider.class);
    return clientConfig;
  }

  private void startDelivery(List<Drone> list, Queue buffer) {
    try {
      String broker = "tcp://localhost:1883";
      String clientId = MqttClient.generateClientId();
      String topic = "dronazon/smartcity/orders";
      int qos = 0;
      MqttClient client = configClientMQTT(broker, clientId);

      client.setCallback(
          new MqttCallback() {

            public void messageArrived(String topic, MqttMessage message) throws InterruptedException {
                String receivedMessage = new String(message.getPayload());
                Gson gson = new GsonBuilder().create();

                Delivery delivery = gson.fromJson(receivedMessage, Delivery.class);

                buffer.push(delivery, list);
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

  private MqttClient configClientMQTT(String broker, String clientId) throws MqttException {
    MqttClient client = new MqttClient(broker, clientId);
    MqttConnectOptions connOpts = new MqttConnectOptions();
    connOpts.setCleanSession(true);
    client.connect(connOpts);
    return client;
  }

  private void sendDelivery(Delivery delivery, int driver, List<Drone> list)
      throws InterruptedException {
    checkDroneLife(drone, list);

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
                        .setIdDriver(driver)
                        .build();

                stub.delivery(
                    request,
                    new StreamObserver<Empty>() {
                      public void onNext(Empty response) {}

                      public void onError(Throwable throwable) {
                        try {
                          channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                          channel.shutdownNow();
                        }
                      }

                      public void onCompleted() {
                        channel.shutdown();
                      }
                    });
                channel.awaitTermination(1, TimeUnit.MINUTES);
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
              try {

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
                        Drone drone = nextDrone(next, list);
                        list.remove(next);
                        try {
                          channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                          checkDroneLife(drone, list);
                        } catch (InterruptedException e) {
                          channel.shutdownNow();
                        }
                      }

                      @Override
                      public void onCompleted() {
                        channel.shutdown();
                      }
                    });
              } catch (Exception e) {
                e.printStackTrace();
              }
            });
  }

  private static Drone defineDroneOfDelivery(List<Drone> list, Point start) {
      for (Drone d: list
           ) {
          System.out.println(d.getAvailable());
      }
    return list.stream()
        .filter(Drone::getAvailable)
        .min(Comparator.comparing(d -> d.getPoint().distance(start)))
        .orElse(null);
  }
}
