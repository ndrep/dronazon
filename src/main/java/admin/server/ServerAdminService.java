package admin.server;

import beans.Drone;
import beans.SmartCity;
import java.util.List;
import javax.ws.rs.*;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("api")
public class ServerAdminService {

  @Path("list_of_drones")
  @GET
  @Produces({"application/json"})
  public Response getListOfDrones() {
    return Response.ok(SmartCity.getInstance()).build();
  }

  @Path("last_n_statistics")
  @GET
  @Produces({"application/json"})
  public Response getLastNStat(@QueryParam("value") String value) {
    return Response.ok("last " + value + " statistics").build();
  }

  @Path("mean_of_delivery")
  @GET
  @Produces({"application/json"})
  public Response getMeanOfDelivery(@QueryParam("t1") String t1, @QueryParam("t2") String t2) {
    return Response.ok("mean of delivery between " + t1 + " and " + t2).build();
  }

  @Path("mean_of_km")
  @GET
  @Produces({"application/json"})
  public Response getMeanOfKm(@QueryParam("t1") String t1, @QueryParam("t2") String t2) {
    return Response.ok("mean of km between " + t1 + " and " + t2).build();
  }

  @Path("add")
  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces({"application/json"})
  public Response addDrone(
      @FormParam("id") String id,
      @FormParam("port") String port,
      @FormParam("address") String address) {
    List<Drone> list = SmartCity.getInstance().add(new Drone(id, port, address));
    GenericEntity<List<Drone>> entity = new GenericEntity<List<Drone>>(list) {};
    return Response.ok(entity).build();
  }
}