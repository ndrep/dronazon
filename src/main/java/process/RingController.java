package process;

import beans.Drone;
import java.awt.*;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class RingController {
  private final List<Drone> list;
  private final Drone drone;

  public RingController(List<Drone> list, Drone drone) {
    this.list = list;
    this.drone = drone;
  }

  private boolean available(List<Drone> list) {
    return list.stream().anyMatch(Drone::getAvailable);
  }

  private Drone defineDroneOfDelivery(List<Drone> list, Point start) {
    list.sort(Comparator.comparing(Drone::getBattery).thenComparing(Drone::getId));
    list.sort(Collections.reverseOrder());
    return list.stream()
        .filter(Drone::getAvailable)
        .min(Comparator.comparing(d -> d.getPoint().distance(start)))
        .orElse(null);
  }

  private Drone nextDrone(Drone drone, List<Drone> list) {
    int index = list.indexOf(searchDroneInList(drone, list));
    return list.get((index + 1) % list.size());
  }

  private Drone searchDroneInList(Drone drone, List<Drone> list) {
    return list.stream().filter(d -> d.getId() == drone.getId()).findFirst().orElse(null);
  }
}
