package dronazon;

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

      System.out.println(clientId + " Connecting Broker " + broker);
      client.connect(connOpts);
      System.out.println(clientId + " Connected - Thread PID: " + Thread.currentThread().getId());


      int count = 0;
      while (true) {
        String payload = "delivery number: " + count++;
        MqttMessage message = new MqttMessage(payload.getBytes());

        message.setQos(qos);
        message.setRetained(false);

        System.out.println(clientId + " Publishing message: " + payload);
        client.publish(topic, message);
        System.out.println(clientId + " Message published - Thread PID: " + Thread.currentThread().getId());
        
        Thread.sleep(15000);
      }


    } catch (MqttException me) {
      System.out.println("reason " + me.getReasonCode());
      System.out.println("msg " + me.getMessage());
      System.out.println("loc " + me.getLocalizedMessage());
      System.out.println("cause " + me.getCause());
      System.out.println("excep " + me);
      me.printStackTrace();
    } catch (Exception e){
      e.printStackTrace();
    }
  }
}
