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

  public static StatisticsSmartCity getInstance() {
    if (manager == null) {
      manager = new StatisticsSmartCity();
    }
    return manager;
  }

  public ArrayList<Statistics> getList() {
    return new ArrayList<>(list);
  }

  public void setList(ArrayList<Statistics> list) {
    this.list = list;
  }

  public ArrayList<Statistics> add(Statistics statistics) {
    if (list.stream()
            .filter(d -> d.getTimestamp().equals(statistics.getTimestamp()))
            .findFirst()
            .orElse(null)
        == null) list.add(statistics);
    return list;
  }
}
