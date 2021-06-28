package process;

import beans.Drone;
import dronazon.Delivery;
import java.util.ArrayList;
import java.util.List;

public class Queue {

  public ArrayList<Delivery> buffer = new ArrayList<>();

  public synchronized void push(Delivery delivery) {
    buffer.add(delivery);
    notify();
  }

  public synchronized Delivery pop(List<Drone> list) {
    while (buffer.size() == 0) {
      try {
        wait();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    Delivery delivery = buffer.get(0);
    buffer.remove(0);

    return delivery;
  }

  public int size() {
    return buffer.size();
  }
}
