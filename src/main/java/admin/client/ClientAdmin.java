package admin.client;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import java.util.Scanner;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;

public class ClientAdmin {

  private static WebResource webResource;
  private static ClientResponse response;

  public static void main(String[] args) {
    String menu =
        "\n****************************************\n"
            + "* (1): list of drones                  *\n"
            + "* (2): last n° statistics              *\n"
            + "* (3): mean delivery between t1 and t2 *\n"
            + "* (4): mean km between t1 and t2       *\n"
            + "* (5): add drone                       *\n"
            + "* (q): quit                            *\n"
            + "****************************************";
    String url = "http://localhost:6789";

    System.out.println(menu);
    try {
      Scanner sc = new Scanner(System.in);
      Client client = Client.create();
      MultivaluedMap<String, String> param;
      String option = sc.next();

      while (!option.equals("q")) {
        param = new MultivaluedMapImpl();

        switch (option) {
          case "1":
            webResource = client.resource(url + "/api/list_of_drones");
            response = webResource.accept("application/json").get(ClientResponse.class);

            checkResponse();

            System.out.print("Output from Server: ");

            System.out.println(response.getEntity(String.class));

            System.out.println(menu);
            break;
          case "2":
            System.out.println("Insert parameter");
            param.add("value", sc.next());

            webResource =
                client.resource(url + "/api/last_n_statistics").queryParams(param);
            response = webResource.accept("application/json").get(ClientResponse.class);

            checkResponse();

            System.out.print("Output from Server: ");
            System.out.println(response.getEntity(String.class));

            System.out.println(menu);
            break;
          case "3":
            System.out.println("Insert T1");
            param.add("t1", sc.next());
            System.out.println("Insert T2");
            param.add("t2", sc.next());

            webResource =
                client.resource(url + "/api/mean_of_delivery").queryParams(param);
            response = webResource.accept("application/json").get(ClientResponse.class);

            checkResponse();

            System.out.print("Output from Server: ");
            System.out.println(response.getEntity(String.class));

            System.out.println(menu);
            break;
          case "4":
            System.out.println("Insert T1");
            param.add("t1", sc.next());
            System.out.println("Insert T2");
            param.add("t2", sc.next());

            webResource =
                client.resource(url + "/api/mean_of_km").queryParams(param);
            response = webResource.accept("application/json").get(ClientResponse.class);

            checkResponse();

            System.out.print("Output from Server: ");
            System.out.println(response.getEntity(String.class));

            System.out.println(menu);
            break;
          case "5":
            System.out.println("Insert id");
            param.add("id", sc.next());
            System.out.println("Insert port");
            param.add("port", sc.next());
            System.out.println("Insert address");
            param.add("address", sc.next());

            webResource = client.resource(url + "/api/add");
            response =
                webResource
                    .type(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
                    .post(ClientResponse.class, param);

            checkResponse();

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

  private static void checkResponse() {
    if (response.getStatus() != 200) {
      throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
    }
  }
}
