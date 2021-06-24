package dronazon;

import java.awt.*;
import java.util.Random;

public class Delivery {
  private final int id;
  private Point start;
  private Point end;

  public Delivery(int id) {
    this.id = id;
    Random rand = new Random(System.currentTimeMillis());
    start = new Point(rand.nextInt(10), rand.nextInt(10));
    end = new Point(rand.nextInt(10), rand.nextInt(10));
  }

  public int getId() {
    return id;
  }

  public Point getStart() {
    return start;
  }

  public Point getEnd() {
    return end;
  }

  public void setStart(Point start) {
    this.start = start;
  }

  public void setEnd(Point end) {
    this.end = end;
  }
}
