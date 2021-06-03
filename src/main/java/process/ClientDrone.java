package process;

import beans.Drone;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;
import java.awt.*;
import java.sql.Timestamp;
import java.util.List;
import java.util.Random;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.codehaus.jackson.jaxrs.JacksonJsonProvider;
import org.eclipse.paho.client.mqttv3.*;

public class ClientDrone {

  public static boolean master;

  public static void main(String[] args) throws InterruptedException {
    String url = "http://localhost:6789";
    Random rand = new Random(System.currentTimeMillis());

    ClientConfig clientConfig = getClientConfig();
    Client client = Client.create(clientConfig);

    WebResource webResource = client.resource(url + "/api/add");

    Drone drone =
        new Drone(
            Integer.toString(rand.nextInt(100)),
            Integer.toString(rand.nextInt(5000) + 1000),
            "localhost");

    ClientResponse response =
        webResource.type("application/json").post(ClientResponse.class, drone);

    if (response.getStatus() != 200) {
      throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
    }

    List<Drone> list = response.getEntity(new GenericType<List<Drone>>() {});
    list.sort((d1, d2) -> d1.getId().compareToIgnoreCase(d2.getId()));

    updateDrone(drone, list);

    MutableFlag flag = new MutableFlag();
    Quit thread = new Quit(flag);
    thread.start();


    if (master) subscribe();

    while (flag.getFlag() && drone.getBattery() >= 15) {
      Thread.sleep(1000);
    }

    System.exit(0);
  }

  private static void updateDrone(Drone drone, List<Drone> list) {
    master = list.size() == 1;
    Random rand = new Random(System.currentTimeMillis());
    drone.setPoint(new Point(rand.nextInt(10), rand.nextInt(10)));
    drone.setNext(list.get(0));
    for (Drone d : list) {
      if (d.getNext() == list.get(0)) {
        d.setNext(drone);
        break;
      }
    }
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
