package process;

public class MutableStringInput {
  private volatile String input = "...";

  public String getInput() {
    return input;
  }

  public void setInput(String input) {
    this.input = input;
  }
}
