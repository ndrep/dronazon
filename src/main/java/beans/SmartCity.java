package beans;

import java.awt.*;
import java.util.ArrayList;
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
    list.add(drone);
    return list;
  }

  public synchronized ArrayList<Drone> remove(int id) {
    for (Drone d : list) {
      if (d.getId() == id) {
        list.remove(d);
        break;
      }
    }
    return list;
  }
}
