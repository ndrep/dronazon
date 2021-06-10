package process;

import beans.Drone;
import com.example.grpc.DroneDeliveryGrpc;
import com.example.grpc.DroneDeliveryGrpc.*;
import com.example.grpc.DroneMasterGrpc;
import com.example.grpc.DroneMasterGrpc.*;
import com.example.grpc.DronePresentationGrpc;
import com.example.grpc.DronePresentationGrpc.*;
import com.example.grpc.Hello;
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
import java.sql.Timestamp;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.codehaus.jackson.jaxrs.JacksonJsonProvider;
import org.eclipse.paho.client.mqttv3.*;

public class ClientDrone {

  private static Drone drone;

  public static void main(String[] args) {
    String url = "http://localhost:6789";
    Random rand = new Random(System.currentTimeMillis());
    drone = new Drone(rand.nextInt(100), rand.nextInt(8080) + 1000, "localhost");

    try {
      ClientConfig clientConfig = getClientConfig();
      Client client = Client.create(clientConfig);

      List<Drone> list = getDroneList(url, client);

      Point point = getStartPoint(url, client);

      findDrone(drone, list).setPoint(point);

      list.sort(Comparator.comparing(Drone::getId));

      if (isMasterDrone(list.size(), 1)) {
        drone.setIdMaster(drone.getId());
      }

      Server service =
          ServerBuilder.forPort(drone.getPort())
              .addService(new DroneMasterImpl(drone, list))
              .addService(new DronePresentationImpl(drone, list))
              .addService(new DroneDeliveryImpl(drone, list))
              .build();
      service.start();

      if (isMasterDrone(drone.getIdMaster(), drone.getId())) {
        subscribe(list);
      } else {
        asynchronousGetMaster(drone, list);
        asynchronousDronePresentation(drone, list);
      }

      ExitThread exit = new ExitThread();
      exit.start();
      synchronized (exit) {
        try {
          exit.wait();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }

      System.exit(0);

    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
    }
  }

  private static boolean isMasterDrone(int idMaster, int id) {
    return idMaster == id;
  }

  private static Point getStartPoint(String url, Client client) {
    WebResource webResource = client.resource(url + "/api/point");

    ClientResponse info_point = webResource.type("application/json").get(ClientResponse.class);

    if (info_point.getStatus() != 200) {
      throw new RuntimeException("Failed : HTTP error code : " + info_point.getStatus());
    }

    return info_point.getEntity(Point.class);
  }

  private static List<Drone> getDroneList(String url, Client client) {
    WebResource webResource = client.resource(url + "/api/add");

    ClientResponse response =
        webResource.type("application/json").post(ClientResponse.class, drone);

    if (response.getStatus() != 200) {
      throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
    }

    return response.getEntity(new GenericType<List<Drone>>() {});
  }

  private static Drone findDrone(Drone drone, List<Drone> list) {
    return list.stream()
        .filter(d -> isMasterDrone(d.getId(), drone.getId()))
        .findFirst()
        .orElse(null);
  }

  private static void asynchronousGetMaster(Drone drone, List<Drone> list)
      throws InterruptedException {
    Drone next = getNextDrone(drone, list);

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
            drone.setIdMaster(
                list.stream()
                    .filter(d -> isMasterDrone(d.getId(), response.getId()))
                    .findFirst()
                    .get()
                    .getId());
          }

          public void onError(Throwable throwable) {
            System.out.println("Error! " + throwable.getMessage());
          }

          public void onCompleted() {
            channel.shutdownNow();
          }
        });
    channel.awaitTermination(1, TimeUnit.MINUTES);
  }

  private static Drone getNextDrone(Drone drone, List<Drone> list) {
    int index = list.indexOf(findDrone(drone, list));
    return list.get((index + 1) % list.size());
  }

  private static void asynchronousDronePresentation(Drone drone, List<Drone> list)
      throws InterruptedException {
    Drone dr = findDrone(drone, list);
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
              System.out.println("Error! " + throwable.getMessage());
            }

            public void onCompleted() {
              channel.shutdownNow();
            }
          });
      channel.awaitTermination(1, TimeUnit.MINUTES);
    }
  }

  private static ClientConfig getClientConfig() {
    ClientConfig clientConfig = new DefaultClientConfig();
    clientConfig.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);
    clientConfig.getClasses().add(JacksonJsonProvider.class);
    return clientConfig;
  }

  private static void subscribe(List<Drone> list) {
    MqttClient client;
    String broker = "tcp://localhost:1883";
    String clientId = MqttClient.generateClientId();
    String topic = "dronazon/smartcity/orders";
    int qos = 0;

    try {
      client = new MqttClient(broker, clientId);
      MqttConnectOptions connOpts = new MqttConnectOptions();
      connOpts.setCleanSession(true);

      System.out.println(clientId + " Connecting Broker " + broker);
      client.connect(connOpts);
      System.out.println(clientId + " Connected - Thread PID: " + Thread.currentThread().getId());

      client.setCallback(
          new MqttCallback() {

            public void messageArrived(String topic, MqttMessage message)
                throws InterruptedException {
              String time = new Timestamp(System.currentTimeMillis()).toString();
              String receivedMessage = new String(message.getPayload());
              System.out.println(
                  clientId
                      + " Received a Message! - Callback - Thread PID: "
                      + Thread.currentThread().getId()
                      + "\n\tTime:    "
                      + time
                      + "\n\tMessage: "
                      + receivedMessage);

              Gson gson = new GsonBuilder().create();
              Delivery delivery = gson.fromJson(receivedMessage, Delivery.class);
              int idDriver = defineDroneOfDelivery(list, delivery.getStart()).getId();

              asynchronousDelivery(delivery, idDriver, list);

              for (Drone d : list) {
                System.out.println(d.toString());
              }
            }

            public void connectionLost(Throwable cause) {
              System.out.println(
                  clientId
                      + " Connectionlost! cause:"
                      + cause.getMessage()
                      + "-  Thread PID: "
                      + Thread.currentThread().getId());
            }

            public void deliveryComplete(IMqttDeliveryToken token) {}
          });
      System.out.println(
          clientId + " Subscribing ... - Thread PID: " + Thread.currentThread().getId());
      client.subscribe(topic, qos);
      System.out.println(clientId + " Subscribed to topics : " + topic);

    } catch (MqttException me) {
      me.printStackTrace();
    }
  }

  private static void asynchronousDelivery(Delivery delivery, int driver, List<Drone> list)
      throws InterruptedException {
    Drone next = getNextDrone(drone, list);

    final ManagedChannel channel =
        ManagedChannelBuilder.forTarget(next.getAddress() + ":" + next.getPort())
            .usePlaintext()
            .build();

    DroneDeliveryStub stub = DroneDeliveryGrpc.newStub(channel);

    Hello.Delivery request =
        Hello.Delivery.newBuilder()
            .setId(delivery.getId())
            .setStartX((int) delivery.getStart().getX())
            .setStartY((int) delivery.getStart().getY())
            .setEndX((int) delivery.getEnd().getX())
            .setEndY((int) +delivery.getEnd().getY())
            .setIdDriver(driver)
            .build();

    stub.delivery(
        request,
        new StreamObserver<Statistics>() {
          public void onNext(Statistics response) {
            Drone updateDrone = list.get(list.indexOf(searchDroneInList(response.getId(), list)));

            updateDrone.setAvailable(true);
            updateDrone.setBattery(response.getPower());
            updateDrone.setPoint(new Point(response.getStartX(), response.getStartY()));
          }

          public void onError(Throwable throwable) {
            System.out.println("Error! " + throwable.getMessage());
          }

          public void onCompleted() {
            channel.shutdownNow();
          }
        });
    channel.awaitTermination(1, TimeUnit.MINUTES);
  }

  private static Drone searchDroneInList(int id, List<Drone> list) {
    return list.stream().filter(d -> d.getId() == id).findFirst().get();
  }

  private static Drone defineDroneOfDelivery(List<Drone> list, Point point) {
    Drone driver =
        list.stream()
            .filter(Drone::getAvailable)
            .min(Comparator.comparing(d -> d.getPoint().distance(point)))
            .orElse(null);
    findDrone(driver, list).setAvailable(false);
    return driver;
  }
}
