package admin.server;

import beans.Drone;
import beans.SmartCity;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.awt.*;
import java.util.List;
import java.util.Random;
import javax.ws.rs.*;
import javax.ws.rs.core.GenericEntity;
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

  @Path("point")
  @GET
  @Produces({"application/json"})
  public Response getPoint() {
    GsonBuilder builder = new GsonBuilder();
    Random rand = new Random(System.currentTimeMillis());
    Point point = new Point(rand.nextInt(10), rand.nextInt(10));
    Gson gson = builder.create();
    return Response.ok(gson.toJson(point)).build();
  }

  @Path("add")
  @POST
  @Consumes({"application/json"})
  @Produces({"application/json"})
  public Response addDrone(Drone drone) {
    List<Drone> list = SmartCity.getInstance().add(drone);
    GenericEntity<List<Drone>> entity = new GenericEntity<List<Drone>>(list) {};
    return Response.status(200).entity(entity).build();
  }
}
