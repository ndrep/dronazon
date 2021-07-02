package process;

import beans.Drone;
import simulator.Measurement;
import simulator.PM10Simulator;

public class PM10SensorThread extends Thread {
  private final Drone drone;

  public PM10SensorThread(Drone drone) {
    this.drone = drone;
  }

  @Override
  public void run() {
    PM10Buffer pm10Buffer =
        new PM10Buffer(
            pm10 ->
                drone
                    .getBufferPM10()
                    .add(
                        pm10.readAllAndClean().stream()
                                .map(Measurement::getValue)
                                .reduce(0.0, Double::sum)
                            / 8.0));
    new PM10Simulator(pm10Buffer).start();
  }
}
