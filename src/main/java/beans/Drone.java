package beans;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import org.codehaus.jackson.annotate.JsonIgnore;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class Drone implements Comparable<Drone> {
  private String id;
  private String port;
  private String address;
  @JsonIgnore private Drone next;
  @JsonIgnore private int battery = 100;

  public Drone() {}

  public Drone(String id, String port, String address) {
    this.id = id;
    this.port = port;
    this.address = address;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getPort() {
    return port;
  }

  public void setPort(String port) {
    this.port = port;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public Drone getNext() {
    return next;
  }

  public void setNext(Drone next) {
    this.next = next;
  }

  public int getBattery() {
    return battery;
  }

  public void setBattery(int battery) {
    this.battery = battery;
  }

  @Override
  public int compareTo(Drone drone) {
    if (battery > drone.battery) {
      return 1;
    }
    if (battery == drone.battery && Integer.parseInt(id) > Integer.parseInt(drone.getId())) {
      return 1;
    }

    return 0;
  }
}
