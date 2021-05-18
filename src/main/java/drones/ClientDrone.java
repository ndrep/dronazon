package drones;

import beans.Drone;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import org.codehaus.jackson.jaxrs.JacksonJsonProvider;

public class ClientDrone {
  public static void main(String[] args){
    String url = "http://localhost:6789";


    ClientConfig clientConfig = new DefaultClientConfig();
    clientConfig.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);
    clientConfig.getClasses().add(JacksonJsonProvider.class);
    Client client = Client.create(clientConfig);

    WebResource webResource = client.resource(url + "/api/add");

    MultivaluedMap<String, String> param = new MultivaluedMapImpl();
    param.add("id", ThreadLocalRandom.current().nextInt(1, 100) + "");
    param.add("port", ThreadLocalRandom.current().nextInt(1000, 5000) + "");
    param.add("address", "localhost");

    ClientResponse response =
        webResource.type(MediaType.APPLICATION_FORM_URLENCODED).post(ClientResponse.class, param);

    if (response.getStatus() != 200) {
      throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
    }

    System.out.print("Output from Server: ");

    List<Drone> list = response.getEntity(new GenericType<List<Drone>>() {});



  }
}
