package dronazon;

import com.google.gson.Gson;
import org.eclipse.paho.client.mqttv3.*;

public class Dronazon {
  public static void main(String[] args) {
    MqttClient client;
    String broker = "tcp://localhost:1883";
    String clientId = MqttClient.generateClientId();
    String topic = "dronazon/smartcity/orders";
    int qos = 0;
    try {
      client = new MqttClient(broker, clientId);
      MqttConnectOptions connOpts = new MqttConnectOptions();
      connOpts.setCleanSession(true);
      connOpts.setKeepAliveInterval(60);

      System.out.println(" Connecting Broker " + broker);
      client.connect(connOpts);

      int count = 0;
      Gson gson = new Gson();

      while (true) {
        Delivery delivery = new Delivery(count++);
        String payload = gson.toJson(delivery);
        MqttMessage message = new MqttMessage(payload.getBytes());

        message.setQos(qos);
        message.setRetained(true);

        System.out.println("ORDER: " + payload);
        client.publish(topic, message);

        Thread.sleep(3000);
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
