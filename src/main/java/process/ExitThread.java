package process;

import java.util.Locale;
import java.util.Scanner;

public class ExitThread extends Thread {
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
      }
    }
  }
}
