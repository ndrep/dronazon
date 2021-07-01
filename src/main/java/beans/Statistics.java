package beans;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class Statistics {
  private double delivery;
  private double km;
  private double pm10;
  private double battery;
  private String timestamp;

  public Statistics() {}

  public Statistics(double delivery, double km, double pm10, double battery, String timestamp) {
    this.delivery = delivery;
    this.km = km;
    this.pm10 = pm10;
    this.battery = battery;
    this.timestamp = timestamp;
  }

  public double getDelivery() {
    return delivery;
  }

  public void setDelivery(double delivery) {
    this.delivery = delivery;
  }

  public double getKm() {
    return km;
  }

  public void setKm(double km) {
    this.km = km;
  }

  public double getPm10() {
    return pm10;
  }

  public void setPm10(double pm10) {
    this.pm10 = pm10;
  }

  public double getBattery() {
    return battery;
  }

  public void setBattery(double battery) {
    this.battery = battery;
  }

  public String getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(String timestamp) {
    this.timestamp = timestamp;
  }
}
