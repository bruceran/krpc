package krpc.test.dynamicroute;

import com.xxx.userservice.proto.LoginReq;
import com.xxx.userservice.proto.LoginRes;
import com.xxx.userservice.proto.UserService;
import krpc.rpc.bootstrap.Bootstrap;
import krpc.rpc.bootstrap.RefererConfig;
import krpc.rpc.bootstrap.RegistryConfig;
import krpc.rpc.bootstrap.RpcApp;
import krpc.trace.Trace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RpcClient {

    static Logger log = LoggerFactory.getLogger(RpcClient.class);

    public static void main(String[] args) throws Exception {

        RpcApp app = new Bootstrap()
                .addRegistry(new RegistryConfig().setType("consul").setAddrs("10.1.20.16:8500")
                        .setAclToken("dde69310-cec2-eab7-4082-c53cbd556b25")
                )
                .setDynamicRoutePlugin("consul")
                //.addRegistry(new RegistryConfig().setType("consul").setAddrs("192.168.31.144:8500"))
                //.addReferer(new RefererConfig("us").setInterfaceName(UserService.class.getName()).setRegistryName("consul"))
                //.addRegistry(new RegistryConfig().setType("etcd").setAddrs("192.168.31.144:2379"))
                //.addReferer(new RefererConfig("us").setInterfaceName(UserService.class.getName()).setRegistryName("etcd"))
                //.addRegistry(new RegistryConfig().setType("zookeeper").setAddrs("192.168.31.144:2181"))
                //.addReferer(new RefererConfig("us").setInterfaceName(UserService.class.getName()).setRegistryName("zookeeper"))
                //.addRegistry(new RegistryConfig().setType("jedis").setAddrs("127.0.0.1:6379"))
                //.addReferer(new RefererConfig("us").setInterfaceName(UserService.class.getName()).setRegistryName("jedis"))
                .addReferer(new RefererConfig("us").setInterfaceName(UserService.class.getName()).setDirect("10.1.20.73:7100,10.1.20.13:7117"))
                .setTraceAdapter("cat:server=10.135.81.135:8081;enabled=false")
                .build();

        app.initAndStart();

        for (int i = 0; i < 1; ++i) {
            UserService us = app.getReferer("us");

            Trace.start("rpc call","aaa");
            Trace.tagForRpc("dyeing","211");
            LoginReq req = LoginReq.newBuilder().setUserName("abc").setPassword("mmm").build();
            LoginRes res = us.login(req);
            log.info("res=" + res.getRetCode() + "," + res.getRetMsg());
            Trace.stop();

            Thread.sleep(500);
        }

        Thread.sleep(300000);

        app.stopAndClose();
    }


}

