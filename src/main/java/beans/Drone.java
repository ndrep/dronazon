package beans;

import java.awt.*;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import org.codehaus.jackson.annotate.JsonIgnore;
import process.Queue;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class Drone implements Comparable<Drone> {
  private int id;
  private int port;
  private String address;
  @JsonIgnore private int battery = 100;
  @JsonIgnore private boolean available = true;
  @JsonIgnore private Point point;
  @JsonIgnore private int idMaster;
  @JsonIgnore private int tot_delivery;
  @JsonIgnore private double tot_km;
  @JsonIgnore private String timestamp = "no delivery made";
  @JsonIgnore private boolean safe = true;
  @JsonIgnore private boolean election = false;
  @JsonIgnore private final Queue buffer = new Queue();

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

  public int getTot_delivery() {
    return tot_delivery;
  }

  public void setTot_delivery(int tot_delivery) {
    this.tot_delivery = tot_delivery;
  }

  public double getTot_km() {
    return tot_km;
  }

  public void setTot_km(double tot_km) {
    this.tot_km = tot_km;
  }

  public String getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(String timestamp) {
    this.timestamp = timestamp;
  }

  public boolean getSafe() {
    return safe;
  }

  public synchronized void setSafe(boolean safe) {
    this.safe = safe;
    if (safe) {
      notify();
    }
  }

  public Queue getBuffer() {
    return buffer;
  }

  public void setElection(boolean election) {
    this.election = election;
  }

  public boolean getElection() {
    return election;
  }

  @Override
  public String toString() {
    return " id=" + id + " ";
  }

  public String printPoint() {
    return "(" + point.x + "," + point.y + ")";
  }

  @Override
  public int compareTo(Drone o) {
    if (battery > o.getBattery()) return 1;
    else if (battery == o.getBattery() && id > o.getId()) return 1;
    else return -1;
  }
}
