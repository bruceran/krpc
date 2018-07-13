package krpc.test.registry;

import com.xxx.userservice.proto.*;
import krpc.rpc.bootstrap.Bootstrap;
import krpc.rpc.bootstrap.RegistryConfig;
import krpc.rpc.bootstrap.RpcApp;
import krpc.rpc.bootstrap.ServiceConfig;
import krpc.rpc.core.RpcContextData;
import krpc.rpc.core.ServerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RpcServer1 {

    static Logger log = LoggerFactory.getLogger(RpcServer1.class);

    public static void main(String[] args) throws Exception {

        UserServiceImpl impl = new UserServiceImpl(); // user code is here

        RpcApp app = new Bootstrap()

                //.addRegistry(new RegistryConfig().setType("consul").setAddrs("192.168.31.144:8500"))
                //.addService(new ServiceConfig().setInterfaceName(UserService.class.getName()).setImpl(impl).setRegistryNames("consul"))
                //.addRegistry(new RegistryConfig().setType("etcd").setAddrs("192.168.31.144:2379"))
                //.addService(new ServiceConfig().setInterfaceName(UserService.class.getName()).setImpl(impl).setRegistryNames("etcd"))
                //.addRegistry(new RegistryConfig().setType("zookeeper").setAddrs("192.168.31.144:2181"))
                //.addService(new ServiceConfig().setInterfaceName(UserService.class.getName()).setImpl(impl).setRegistryNames("zookeeper"))
                .addRegistry(new RegistryConfig().setType("jedis").setAddrs("127.0.0.1:6379"))
                .addService(new ServiceConfig().setInterfaceName(UserService.class.getName()).setImpl(impl).setRegistryNames("jedis"))
                .build();

        app.initAndStart();

        Thread.sleep(500000);

        app.stopAndClose();

    }
}

class UserServiceImpl implements UserService {

    static Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    public LoginRes login(LoginReq req) {

        RpcContextData ctx = ServerContext.get();
        log.info("login received, peers=" + ctx.getMeta().getTrace().getPeers());
        return LoginRes.newBuilder().setRetCode(0).setRetMsg("hello, friend. receive req ").build();
    }

    public UpdateProfileRes updateProfile(UpdateProfileReq req) {
        UpdateProfileRes res = UpdateProfileRes.newBuilder().setRetCode(-100002).build();
        return res;
    }


}