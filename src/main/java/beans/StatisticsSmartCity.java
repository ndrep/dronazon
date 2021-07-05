package beans;

import java.util.ArrayList;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class StatisticsSmartCity {
  private ArrayList<Statistics> list;

  public StatisticsSmartCity() {
    list = new ArrayList<>();
  }

  private static StatisticsSmartCity manager;

  public static synchronized StatisticsSmartCity getInstance() {
    if (manager == null) {
      manager = new StatisticsSmartCity();
    }
    return manager;
  }

  public synchronized ArrayList<Statistics> getList() {
    return new ArrayList<>(list);
  }

  public synchronized void setList(ArrayList<Statistics> list) {
    this.list = list;
  }

  public synchronized ArrayList<Statistics> add(Statistics statistics) {
    if (list.stream()
            .filter(d -> d.getTimestamp().equals(statistics.getTimestamp()))
            .findFirst()
            .orElse(null)
        == null) list.add(statistics);
    return list;
  }
}
