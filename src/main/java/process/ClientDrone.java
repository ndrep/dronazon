package process;

import beans.Drone;
import com.example.grpc.DroneMasterGrpc;
import com.example.grpc.DroneMasterGrpc.*;
import com.example.grpc.DronePresentationGrpc;
import com.example.grpc.DronePresentationGrpc.*;
import com.example.grpc.Hello;
import com.example.grpc.Hello.*;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.codehaus.jackson.jaxrs.JacksonJsonProvider;
import org.eclipse.paho.client.mqttv3.*;

public class ClientDrone {

  private static int guard;

  public static void main(String[] args) {
    String url = "http://localhost:6789";
    Random rand = new Random(System.currentTimeMillis());
    Drone drone = new Drone(rand.nextInt(100), rand.nextInt(5000) + 1000, "localhost");

    try {
      ClientConfig clientConfig = getClientConfig();
      Client client = Client.create(clientConfig);

      WebResource webResource = client.resource(url + "/api/add");

      ClientResponse response =
              webResource.type("application/json").post(ClientResponse.class, drone);

      if (response.getStatus() != 200) {
        throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
      }

      List<Drone> list = response.getEntity(new GenericType<List<Drone>>() {
      });

      updateRingDrone(drone, list);

      Server service = ServerBuilder.forPort(drone.getPort())
              .addService(new DroneMasterImpl(list))
              .addService(new DronePresentationImpl(list))
              .build();
      service.start();

      if (drone.getMaster())
        subscribe();
      else {
        asynchronousGetMaster(drone);
        asynchronousDronePresentation(drone, list);
      }

      list.sort(Comparator.comparing(Drone::getId));

      ExitThread exit = new ExitThread();
      exit.start();
      synchronized (exit){
        try {
          exit.wait();
        }catch (InterruptedException e){
          e.printStackTrace();
        }
      }

      System.exit(0);

    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
    }
  }

  private static void asynchronousGetMaster(Drone drone) throws InterruptedException {
    final ManagedChannel channel = ManagedChannelBuilder.forTarget(drone.getNext().getAddress() + ":" + drone.getNext().getPort()).usePlaintext().build();

    DroneMasterStub stub = DroneMasterGrpc.newStub(channel);

    Master request = Master.newBuilder().build();

    stub.master(request, new StreamObserver<Response>() {

      public void onNext(Response response) {
        System.out.println(response.getId());
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

  private static void asynchronousDronePresentation(Drone drone, List<Drone> list) throws InterruptedException {
    List<Drone>  clean = list.stream().filter(d -> d.getId() != drone.getId()).collect(Collectors.toList());

    for (Drone d: clean) {
      final ManagedChannel channel = ManagedChannelBuilder.forTarget(d.getAddress() + ":" + d.getPort()).usePlaintext().build();

      DronePresentationStub stub = DronePresentationGrpc.newStub(channel);

      Hello.Drone request = Hello.Drone.newBuilder()
              .setId(drone.getId())
              .setPort(drone.getPort())
              .setAddress(drone.getAddress())
              .setNext(drone.getNext().getId())
              .build();

      stub.info(request, new StreamObserver<Empty>(){
        public void onNext(Empty response) {
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
  }

  private static void updateRingDrone(Drone drone, List<Drone> list) {
    if (list.size() == 1)
      setDroneMaster(drone, list);

    drone.setNext(list.get(0));
    for (Drone d : list) {
      if (d.getNext() == list.get(0)) {
        d.setNext(drone);
        break;
      }
    }
  }

  private static void setDroneMaster(Drone drone, List<Drone> list){
    drone.setMaster(true);
    list.stream().filter(d -> drone.getId() == d.getId()).findAny().get().setMaster(true);
  }

  private static ClientConfig getClientConfig() {
    ClientConfig clientConfig = new DefaultClientConfig();
    clientConfig.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);
    clientConfig.getClasses().add(JacksonJsonProvider.class);
    return clientConfig;
  }

  private static void subscribe() {
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

            public void messageArrived(String topic, MqttMessage message) {
              String time = new Timestamp(System.currentTimeMillis()).toString();
              String receivedMessage = new String(message.getPayload());
              System.out.println(
                  clientId
                      + " Received a Message! - Callback - Thread PID: "
                      + Thread.currentThread().getId()
                      + "\n\tTime:    "
                      + time
                      + "\n\tTopic:   "
                      + topic
                      + "\n\tMessage: "
                      + receivedMessage
                      + "\n\tQoS:     "
                      + message.getQos()
                      + "\n");
            }

            public void connectionLost(Throwable cause) {
              System.out.println(
                  clientId
                      + " Connectionlost! cause:"
                      + cause.getMessage()
                      + "-  Thread PID: "
                      + Thread.currentThread().getId());
            }

            public void deliveryComplete(IMqttDeliveryToken token) {

            }
          });
      System.out.println(
          clientId + " Subscribing ... - Thread PID: " + Thread.currentThread().getId());
      client.subscribe(topic, qos);
      System.out.println(clientId + " Subscribed to topics : " + topic);

    } catch (MqttException me) {
      System.out.println("reason " + me.getReasonCode());
      System.out.println("msg " + me.getMessage());
      System.out.println("loc " + me.getLocalizedMessage());
      System.out.println("cause " + me.getCause());
      System.out.println("excep " + me);
      me.printStackTrace();
    }
  }
}
