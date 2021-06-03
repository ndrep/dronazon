package dronazon;

import java.awt.*;
import java.util.Random;

public class Delivery {
  private final int id;
  private final Point start;
  private final Point end;

  public Delivery(int id) {
    this.id = id;
    Random rand = new Random(System.currentTimeMillis());
    start = new Point(rand.nextInt(10), rand.nextInt(10));
    end = new Point(rand.nextInt(10), rand.nextInt(10));
  }
}
