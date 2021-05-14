import javax.ws.rs.*;
import javax.ws.rs.core.Response;

@Path("api")
public class ServerAdminService {

  @Path("list_of_drones")
  @GET
  @Produces({"application/json"})
  public Response getList() {
    return Response.ok("list of drones").build();
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
}
