package process;

import beans.Drone;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.paho.client.mqttv3.MqttException;

public class QuitMasterThread extends Thread {
  private final Drone drone;
  private final List<Drone> list;
  private final RingController manager;

  public QuitMasterThread(Drone drone, List<Drone> list) {
    this.drone = drone;
    this.list = list;
    manager = new RingController(list, drone);
  }

  @Override
  public void run() {

    try {
      if (drone.getMqttClient().isConnected()) {
        drone.getMqttClient().disconnect();
      }

      Queue buffer = drone.getBuffer();
      while (buffer.size() > 0
          || !manager.available(
              list.stream()
                  .filter(d -> d.getId() != d.getIdMaster())
                  .collect(Collectors.toList()))) {
        synchronized (list) {
          list.wait();
        }
      }
    } catch (MqttException | InterruptedException mqttException) {
      mqttException.printStackTrace();
    }

    manager.removeFromServerList(drone);

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
}
