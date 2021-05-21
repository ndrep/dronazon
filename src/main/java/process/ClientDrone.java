package process;

import beans.Drone;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import org.codehaus.jackson.jaxrs.JacksonJsonProvider;

public class ClientDrone {

  public static boolean master;

  public static void main(String[] args) {
    String url = "http://localhost:6789";
    Random rand = new Random(System.currentTimeMillis());
    Scanner sc = new Scanner(System.in);

    ClientConfig clientConfig = getClientConfig();
    Client client = Client.create(clientConfig);

    WebResource webResource = client.resource(url + "/api/add");

    Drone drone =
        new Drone(
            Integer.toString(rand.nextInt(100)),
            Integer.toString(rand.nextInt(5000) + 1000),
            "localhost");

    ClientResponse response =
        webResource.type("application/json").post(ClientResponse.class, drone);

    if (response.getStatus() != 200) {
      throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
    }

    List<Drone> list = response.getEntity(new GenericType<List<Drone>>() {});

    updateDrone(drone, list);

    MutableStringInput inputString = new MutableStringInput();
    QuitThread thread = new QuitThread(sc, inputString);
    thread.start();

    while (!inputString.getInput().equals("quit")) {}
  }

  private static void updateDrone(Drone drone, List<Drone> list) {
    master = list.size() == 1;
    drone.setNext(list.get(0));
    for (Drone d : list) {
      if (d.getNext() == list.get(0)) {
        d.setNext(drone);
        break;
      }
    }
  }

  private static ClientConfig getClientConfig() {
    ClientConfig clientConfig = new DefaultClientConfig();
    clientConfig.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);
    clientConfig.getClasses().add(JacksonJsonProvider.class);
    return clientConfig;
  }
}
