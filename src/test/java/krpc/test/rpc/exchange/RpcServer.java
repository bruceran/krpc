package krpc.test.rpc.exchange;

import com.xxx.userservice.proto.UserService;
import krpc.rpc.bootstrap.Bootstrap;
import krpc.rpc.bootstrap.RpcApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RpcServer {

    static Logger log = LoggerFactory.getLogger(RpcServer.class);

    public static void main(String[] args) throws Exception {

        UserServiceImpl impl = new UserServiceImpl(); // user code is here

        RpcApp app = new Bootstrap()
                .addService(UserService.class, impl)
                .build();

        app.initAndStart();

        Thread.sleep(3000000);

        app.stopAndClose();

    }
}

