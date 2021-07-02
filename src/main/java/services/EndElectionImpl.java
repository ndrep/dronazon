package services;

import beans.Drone;
import com.example.grpc.DroneDeliveryGrpc;
import com.example.grpc.EndElectionGrpc;
import com.example.grpc.EndElectionGrpc.*;
import com.example.grpc.Hello;
import com.example.grpc.Hello.*;
import com.example.grpc.InfoUpdatedGrpc;
import com.example.grpc.InfoUpdatedGrpc.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import dronazon.Delivery;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.paho.client.mqttv3.*;
import process.DeliveryController;
import process.Queue;
import process.RingController;

public class EndElectionImpl extends EndElectionImplBase {
  private final Drone drone;
  private List<Drone> list;
  private final Client client;
  private RingController manager;
  private DeliveryController controller;

  public EndElectionImpl(Drone drone, List<Drone> list, Client client) {
    this.drone = drone;
    this.list = list;
    this.client = client;
    manager = new RingController(list, drone);
    controller = new DeliveryController(list, drone);

  }

  @Override
  public void end(Elected request, StreamObserver<Empty> responseObserver) {
    if (drone.getId() == drone.getIdMaster() && !drone.getElection()) {
      list.forEach(d -> d.setAvailable(false));
      drone.setAvailable(true);
      drone.setElection(true);
      forwardElectionMessage(drone, list);
    } else if (drone.getId() != drone.getIdMaster()) {
      drone.setIdMaster(request.getMaster());
      forwardElectionMessage(drone, list);
    } else {
      try {
        drone.setElection(false);
        manager.updateNewMasterInList();
        controller.takeDelivery(drone.getBuffer(), drone.connect());
        //TODO AGGIUNTA
        list = list.stream().filter(d -> !(!d.getAvailable() && d.getBattery()==100)).collect(Collectors.toList());
        startDelivery(list, drone.getBuffer());
      } catch (MqttException e) {
        e.printStackTrace();
      }
    }
    responseObserver.onNext(Empty.newBuilder().build());
    responseObserver.onCompleted();
  }

  private void sendInfoToNewMaster(Drone drone, List<Drone> list) {
    Drone master = manager.getMaster(drone, list);
    Context.current()
        .fork()
        .run(
            () -> {
              final ManagedChannel channel =
                  ManagedChannelBuilder.forTarget(master.getAddress() + ":" + master.getPort())
                      .usePlaintext()
                      .build();

              InfoUpdatedStub stub = InfoUpdatedGrpc.newStub(channel);

              Point point = drone.getPoint();
              DroneInfo info =
                  DroneInfo.newBuilder()
                      .setId(drone.getId())
                      .setTimestamp(drone.getTimestamp())
                      .setKm(drone.getTot_km())
                      .setBattery(drone.getBattery())
                      .setX((int) point.getX())
                      .setY((int) point.getY())
                      .setTotDelivery(drone.getTot_delivery())
                      .build();

              stub.message(
                  info,
                  new StreamObserver<Empty>() {
                    @Override
                    public void onNext(Empty value) {}

                    @Override
                    public void onError(Throwable t) {
                      try {
                        if (t instanceof StatusRuntimeException
                            && ((StatusRuntimeException) t).getStatus().getCode()
                                == Status.UNAVAILABLE.getCode()) {
                          channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                        }
                      } catch (Exception e) {
                        channel.shutdownNow();
                        e.printStackTrace();
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

  private void forwardElectionMessage(Drone drone, List<Drone> list) {
    Drone next = manager.nextDrone(drone, list);

    Context.current()
        .fork()
        .run(
            () -> {
              final ManagedChannel channel =
                  ManagedChannelBuilder.forTarget(next.getAddress() + ":" + next.getPort())
                      .usePlaintext()
                      .build();

              EndElectionStub stub = EndElectionGrpc.newStub(channel);

              Elected elected = Elected.newBuilder().setMaster(drone.getIdMaster()).build();

              stub.end(
                  elected,
                  new StreamObserver<Empty>() {
                    @Override
                    public void onNext(Empty value) {}

                    @Override
                    public void onError(Throwable t) {
                      try {
                        if (t instanceof StatusRuntimeException
                            && ((StatusRuntimeException) t).getStatus().getCode()
                                == Status.UNAVAILABLE.getCode()) {
                          list.remove(next);

                          forwardElectionMessage(drone, list);
                          channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                        }
                      } catch (Exception e) {
                        channel.shutdownNow();
                      }
                    }

                    @Override
                    public void onCompleted() {
                      try {
                        sendInfoToNewMaster(drone, list);
                        channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                      } catch (InterruptedException e) {
                        e.printStackTrace();
                        channel.shutdownNow();
                      }
                    }
                  });
            });
  }


  private void startDelivery(List<Drone> list, Queue buffer) {
    new Thread(
            () -> {
              while (true) {
                try {
                  Delivery delivery = buffer.pop();
                  if (manager.available(list)) {
                    Drone driver = manager.defineDroneOfDelivery(list, delivery.getStart());
                    driver.setAvailable(false);
                    controller.sendDelivery(delivery, driver, list);
                  } else {
                    buffer.push(delivery);
                  }
                  if (list.size() == 1) {
                    list.get(0).setAvailable(true);
                  }

                } catch (InterruptedException e) {
                  e.printStackTrace();
                }
              }
            })
        .start();
  }
}
