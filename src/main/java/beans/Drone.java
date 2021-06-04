package beans;

import java.awt.*;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import org.codehaus.jackson.annotate.JsonIgnore;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class Drone {
  private int id;
  private int port;
  private String address;
  @JsonIgnore private boolean master;
  @JsonIgnore private Drone next;
  @JsonIgnore private int battery = 100;
  @JsonIgnore private Point point;


  public Drone() {}

  public Drone(int id, int port, String address) {
    this.id = id;
    this.port = port;
    this.address = address;
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
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

  public Point getPoint() {
    return point;
  }

  public void setPoint(Point point) {
    this.point = point;
  }

  public boolean getMaster() {
    return master;
  }

  public void setMaster(boolean master) {
    this.master = master;
  }
}
