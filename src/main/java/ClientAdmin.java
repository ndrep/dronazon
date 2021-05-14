import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import java.util.Scanner;
import javax.ws.rs.core.MultivaluedMap;

public class ClientAdmin {
  private static final String menu =
      "****************************************\n"
          + "* (1): list of drones                  *\n"
          + "* (2): last n° statistics              *\n"
          + "* (3): mean delivery between t1 and t2 *\n"
          + "* (4): mean km between t1 and t2       *\n"
          + "* (5): quit                            *\n"
          + "****************************************";
  private static WebResource webResource;
  private static ClientResponse response;

  public static void main(String[] args) {
    System.out.println(menu);
    try {
      Scanner sc = new Scanner(System.in);
      Client client = Client.create();
      MultivaluedMap<String, String> queryParams;
      String option = sc.next();

      while (!option.equals("5")) {
        queryParams = new MultivaluedMapImpl();

        switch (option) {
          case "1":
            webResource = client.resource("http://localhost:6789/api/list_of_drones");
            response = webResource.accept("application/json").get(ClientResponse.class);

            if (response.getStatus() != 200) {
              throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
            }

            System.out.print("Output from Server: ");
            System.out.println(response.getEntity(String.class));

            System.out.println(menu);
            break;
          case "2":
            System.out.println("Insert parameter");
            queryParams.add("value", sc.next());

            webResource =
                client
                    .resource("http://localhost:6789/api/last_n_statistics")
                    .queryParams(queryParams);
            response = webResource.accept("application/json").get(ClientResponse.class);

            if (response.getStatus() != 200) {
              throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
            }

            System.out.print("Output from Server: ");
            System.out.println(response.getEntity(String.class));

            System.out.println(menu);
            break;
          case "3":
            System.out.println("Insert T1");
            queryParams.add("t1", sc.next());
            System.out.println("Insert T2");
            queryParams.add("t2", sc.next());

            webResource =
                client
                    .resource("http://localhost:6789/api/mean_of_delivery")
                    .queryParams(queryParams);
            response = webResource.accept("application/json").get(ClientResponse.class);

            if (response.getStatus() != 200) {
              throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
            }

            System.out.print("Output from Server: ");
            System.out.println(response.getEntity(String.class));

            System.out.println(menu);
            break;
          case "4":
            System.out.println("Insert T1");
            queryParams.add("t1", sc.next());
            System.out.println("Insert T2");
            queryParams.add("t2", sc.next());

            webResource =
                client.resource("http://localhost:6789/api/mean_of_km").queryParams(queryParams);
            response = webResource.accept("application/json").get(ClientResponse.class);

            if (response.getStatus() != 200) {
              throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
            }

            System.out.print("Output from Server: ");
            System.out.println(response.getEntity(String.class));

            System.out.println(menu);
            break;
          default:
            System.out.println("Wrong option!\nRepeat please");
            System.out.println(menu);
        }
        option = sc.next();
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
