package process;

import beans.Drone;
import java.util.List;
import com.example.grpc.DronePresentationGrpc.*;
import com.example.grpc.Hello;
import com.example.grpc.Hello.*;
import io.grpc.stub.StreamObserver;

public class DronePresentationImpl extends DronePresentationImplBase{

    private List<Drone> list;
    public DronePresentationImpl(List<Drone> list) {
        this.list = list;
    }

    @Override
    public void info(Hello.Drone request, StreamObserver<Empty> responseObserver) {
        Drone drone = new Drone(request.getId(), request.getPort(), request.getAddress());

        drone.setNext(list.stream().filter(d -> d.getId() == request.getId()).findFirst().get());

        list.add(drone);

        for (Drone d : list) {
            if (d.getNext().getId() == request.getNext()) {
                d.setNext(drone);
                break;
            }
        }
    }
}
