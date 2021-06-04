package process;

import beans.Drone;
import com.example.grpc.DroneMasterGrpc;
import com.example.grpc.DroneMasterGrpc.*;
import com.example.grpc.Hello.*;
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
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.codehaus.jackson.jaxrs.JacksonJsonProvider;
import org.eclipse.paho.client.mqttv3.*;

public class ClientDrone {
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
      list.sort(Comparator.comparing(Drone::getId));

      updateRingDrone(drone, list);

      Server server = ServerBuilder.forPort(drone.getPort()).addService(new DroneMasterImpl(list)).build();
      server.start();

      MutableFlag flag = new MutableFlag();
      Quit thread = new Quit(flag);
      thread.start();

      if (drone.getMaster())
        subscribe();
      else {
        asynchronousStreamCall(drone);
      }

      while (flag.getFlag() && drone.getBattery() >= 15) {
        Thread.sleep(1000);
      }

      System.exit(0);

    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
    }
  }

  private static void asynchronousStreamCall(Drone drone) throws InterruptedException {
    final ManagedChannel channel = ManagedChannelBuilder.forTarget(drone.getNext().getAddress() + ":" + drone.getNext().getPort()).usePlaintext().build();

    DroneMasterStub stub = DroneMasterGrpc.newStub(channel);

    Master request = Master.newBuilder().build();

    stub.master(request, new StreamObserver<Response>() {

      public void onNext(Response Response) {
        System.out.println(Response.getId());
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
              // Not used here
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
