package process;

import com.example.grpc.CheckDroneGrpc.CheckDroneImplBase;
import com.example.grpc.Hello.Empty;
import io.grpc.stub.StreamObserver;

public class DroneCheckImpl extends CheckDroneImplBase {
  @Override
  public void check(Empty request, StreamObserver<Empty> responseObserver) {
    Empty response = Empty.newBuilder().build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }
}
