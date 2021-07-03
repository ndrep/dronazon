package admin.server;

import beans.Drone;
import beans.SmartCity;
import beans.Statistics;
import beans.StatisticsSmartCity;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.awt.*;
import java.sql.Timestamp;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import javax.ws.rs.*;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.Response;

@Path("api")
public class ServerAdminService {

  @Path("statistics")
  @POST
  @Consumes({"application/json"})
  public Response updateStatistics(Statistics statistics) {
    StatisticsSmartCity.getInstance().add(statistics);
    return Response.status(200).build();
  }

  @Path("list_of_drones")
  @GET
  @Produces({"application/json"})
  public Response getListOfDrones() {
    return Response.status(200).entity(SmartCity.getInstance()).build();
  }

  @Path("last_n_statistics")
  @GET
  @Produces({"application/json"})
  public Response getLastNStat(@QueryParam("value") String value) {
    int n = Integer.parseInt(value);
    List<Statistics> list = StatisticsSmartCity.getInstance().getList();
    return Response.status(200)
        .entity(list.stream().skip(list.size() - n).collect(Collectors.toList()))
        .build();
  }

  @Path("mean_of_delivery")
  @GET
  @Produces({"application/json"})
  public Response getMeanOfDelivery(@QueryParam("t1") String t1, @QueryParam("t2") String t2) {
    List<Statistics> list = StatisticsSmartCity.getInstance().getList();
    list =
        list.stream()
            .filter(
                s ->
                    Timestamp.valueOf(s.getTimestamp()).after(Timestamp.valueOf(t1))
                        && Timestamp.valueOf(s.getTimestamp()).before(Timestamp.valueOf(t2)))
            .collect(Collectors.toList());
    double mean = list.stream().map(Statistics::getDelivery).reduce(0.0, Double::sum) / list.size();
    return Response.status(200).entity(mean).build();
  }

  @Path("mean_of_km")
  @GET
  @Produces({"application/json"})
  public Response getMeanOfKm(@QueryParam("t1") String t1, @QueryParam("t2") String t2) {
    List<Statistics> list = StatisticsSmartCity.getInstance().getList();
    list =
        list.stream()
            .filter(
                s ->
                    Timestamp.valueOf(s.getTimestamp()).after(Timestamp.valueOf(t1))
                        && Timestamp.valueOf(s.getTimestamp()).before(Timestamp.valueOf(t2)))
            .collect(Collectors.toList());
    double mean = list.stream().map(Statistics::getKm).reduce(0.0, Double::sum) / list.size();
    return Response.status(200).entity(mean).build();
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
    if (list.stream().filter(d -> d.getId() == drone.getId()).count() > 1) {
      list.remove(drone);
      return Response.status(400).build();
    }
    GenericEntity<List<Drone>> entity = new GenericEntity<List<Drone>>(list) {};
    return Response.status(200).entity(entity).build();
  }

  @Path("remove")
  @POST
  @Consumes({"application/json"})
  public Response removeDrone(int id) {
    SmartCity.getInstance().remove(id);
    return Response.status(200).build();
  }
}
