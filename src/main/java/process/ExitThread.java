package process;

import beans.Drone;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public class ExitThread extends Thread {
  private Drone drone;
  private List<Drone> list;

  public ExitThread(Drone drone, List<Drone> list) {
    this.drone = drone;
    this.list = list;
  }

  @Override
  public void run() {
    Scanner sc = new Scanner(System.in);
    synchronized (this) {
      String input = sc.nextLine().toLowerCase(Locale.ROOT);
      while (true) {
        if (input.equals("quit")) {
          notify();
          break;
        }
        input = sc.nextLine().toLowerCase(Locale.ROOT);
      }
    }
  }
}
