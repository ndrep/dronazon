package beans;

import java.awt.*;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import org.codehaus.jackson.annotate.JsonIgnore;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class Drone {
  private String id;
  private String port;
  private String address;
  @JsonIgnore private Point point;
  private Drone next;
  private boolean master;
  private int power = 100;

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

  public Point getPoint() {
    return point;
  }

  public void setPoint(Point point) {
    this.point = point;
  }

  public boolean getMaster() {
    return master;
  }

  public void setMaster(boolean value) {
    this.master = value;
  }

  public Drone getNext() {
    return next;
  }

  public void setNext(Drone next) {
    this.next = next;
  }

  public int getPower() {
    return power;
  }

  public void setPower(int power) {
    this.power = power;
  }
}
