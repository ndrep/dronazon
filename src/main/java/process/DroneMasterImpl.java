package process;

import com.example.grpc.DroneMasterGrpc.DroneMasterImplBase;
import com.example.grpc.Hello.*;
import io.grpc.stub.StreamObserver;
import beans.Drone;

import java.util.List;

public class DroneMasterImpl extends DroneMasterImplBase {
    private final List<Drone> list;
    public DroneMasterImpl(List<Drone> list){
        this.list = list;
    }

    @Override
    public void master(Master request, StreamObserver<Response> responseObserver) {
        Drone master = list.stream().filter(Drone::getMaster).findAny().get();

        Response response = Response.newBuilder().setId(master.getId()).build();

        responseObserver.onNext(response);

        responseObserver.onCompleted();
    }
}
