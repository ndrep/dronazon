package process;

import java.util.Locale;
import java.util.Scanner;

public class QuitThread extends Thread {

  private final Scanner sc;
  private MutableStringInput quit;

  public QuitThread(Scanner sc, MutableStringInput quit) {
    this.sc = sc;
    this.quit = quit;
  }

  @Override
  public void run() {
    String input;
    while (true) {
      if ((input = sc.next()).toLowerCase(Locale.ROOT).equals("quit")) {
        quit.setInput(input);
      }
    }
  }
}
