package drone;

import java.util.ArrayList;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class DroneList {

  private ArrayList<Drone> list;

  private static DroneList manager;

  public DroneList() {
    list = new ArrayList<>();
  }

  public static synchronized DroneList getInstance() {
    if (manager == null) {
      manager = new DroneList();
    }
    return manager;
  }

  public ArrayList<Drone> getList() {
    return new ArrayList<>(list);
  }

  public synchronized void setList(ArrayList<Drone> list) {
    this.list = list;
  }

  public synchronized void add(Drone drone) {
    if (list.stream().noneMatch(d -> d.getId() == drone.getId())) list.add(drone);
  }

  public synchronized void remove(Drone drone) {
    list.remove(drone);
  }
}
