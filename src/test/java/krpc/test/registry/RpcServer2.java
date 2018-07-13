package krpc.test.registry;

import com.xxx.userservice.proto.UserService;
import krpc.rpc.bootstrap.Bootstrap;
import krpc.rpc.bootstrap.RegistryConfig;
import krpc.rpc.bootstrap.RpcApp;
import krpc.rpc.bootstrap.ServiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RpcServer2 {

    static Logger log = LoggerFactory.getLogger(RpcServer2.class);

    public static void main(String[] args) throws Exception {

        UserServiceImpl impl = new UserServiceImpl(); // user code is here

        RpcApp app = new Bootstrap()
                .addServer(5601)
                .addRegistry(new RegistryConfig().setType("zookeeper").setAddrs("192.168.31.144:2181"))
                .addService(new ServiceConfig().setInterfaceName(UserService.class.getName()).setImpl(impl).setRegistryNames("zookeeper"))
                .build();

        app.initAndStart();

        Thread.sleep(5000000);

        app.stopAndClose();

    }
}

