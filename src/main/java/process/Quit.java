package process;

import java.util.Scanner;

public class Quit extends Thread {

  private volatile MutableFlag flag;

  public Quit(MutableFlag flag) {
    this.flag = flag;
  }

  @Override
  public void run() {
    Scanner sc = new Scanner(System.in);
    while (true) {
      if (sc.next().equals("quit")) {
        flag.setFlag(false);
        break;
      }
    }
  }
}
