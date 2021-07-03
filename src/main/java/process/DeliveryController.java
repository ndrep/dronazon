package process;

import beans.Drone;
import com.example.grpc.DroneDeliveryGrpc;
import com.example.grpc.Hello;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dronazon.Delivery;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import org.eclipse.paho.client.mqttv3.*;

import java.awt.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DeliveryController {
    private final List<Drone> list;
    private final Drone drone;
    private RingController manager;

    public DeliveryController(List<Drone> list, Drone drone) {
        this.list = list;
        this.drone = drone;
        manager = new RingController(list, drone);
    }

    public void takeDelivery(Queue buffer, MqttClient client) {
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

    public void sendDelivery(Delivery delivery, Drone driver, List<Drone> list)
            throws InterruptedException {
        drone.setSafe(false);

        Drone next = manager.nextDrone(drone, list);

        Context.current()
                .fork()
                .run(
                        () -> {
                            try {
                                final ManagedChannel channel =
                                        ManagedChannelBuilder.forTarget(next.getAddress() + ":" + next.getPort())
                                                .usePlaintext()
                                                .build();

                                DroneDeliveryGrpc.DroneDeliveryStub stub = DroneDeliveryGrpc.newStub(channel);
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
                                        new StreamObserver<Hello.Empty>() {
                                            public void onNext(Hello.Empty response) {}

                                            public void onError(Throwable t) {
                                                try {
                                                    if (t instanceof StatusRuntimeException
                                                            && ((StatusRuntimeException) t).getStatus().getCode()
                                                            == Status.UNAVAILABLE.getCode()) {

                                                        if (next.getId() != drone.getIdMaster()) {
                                                            list.remove(next);
                                                            sendDelivery(delivery, driver, list);
                                                            channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                                                        }
                                                        channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);

                                                    }
                                                } catch (InterruptedException e) {
                                                    channel.shutdownNow();
                                                }
                                            }

                                            public void onCompleted() {
                                                drone.setSafe(true);
                                                try {
                                                    channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                                                } catch (InterruptedException e) {
                                                    channel.shutdownNow();
                                                    e.printStackTrace();
                                                }
                                            }
                                        });
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });
    }
}
