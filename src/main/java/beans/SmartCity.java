package beans;

import java.awt.*;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class SmartCity {

  private ArrayList<Drone> list;

  private static SmartCity manager;

  public SmartCity() {
    list = new ArrayList<>();
  }

  public static synchronized SmartCity getInstance() {
    if (manager == null) {
      manager = new SmartCity();
    }
    return manager;
  }

  public ArrayList<Drone> getList() {
    return new ArrayList<>(list);
  }

  public synchronized void setList(ArrayList<Drone> list) {
    this.list = list;
  }

  public synchronized ArrayList<Drone> add(Drone drone) {
    drone.setPoint(randomPoint());
    if (list.isEmpty()) {
      drone.setMaster(true);
    }

    list.add(drone);
    return list;
  }

  private Point randomPoint() {
    return new Point(
        ThreadLocalRandom.current().nextInt(0, 10), ThreadLocalRandom.current().nextInt(0, 10));
  }
}
