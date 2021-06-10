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
  @JsonIgnore private int battery = 100;
  @JsonIgnore private boolean available = true;
  @JsonIgnore private Point point;
  @JsonIgnore private int idMaster;

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

  public boolean getAvailable() {
    return available;
  }

  public int getIdMaster() {
    return idMaster;
  }

  public void setIdMaster(int idMaster) {
    this.idMaster = idMaster;
  }

  public void setAvailable(boolean available) {
    this.available = available;
  }

  @Override
  public String toString() {
    return "Drone{"
        + "id="
        + id
        + ", port="
        + port
        + ", address='"
        + address
        + '\''
        + ", battery="
        + battery
        + ", point="
        + point
        + '}';
  }
}
