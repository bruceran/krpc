package krpc.test.rpc;

import com.xxx.userservice.proto.UserService;
import krpc.rpc.bootstrap.Bootstrap;
import krpc.rpc.bootstrap.RefererConfig;
import krpc.rpc.bootstrap.RegistryConfig;
import krpc.rpc.bootstrap.RpcApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpProxy {

    static Logger log = LoggerFactory.getLogger(HttpProxy.class);

    public static void main(String[] args) throws Exception {

        RpcApp app = new Bootstrap()
                .addWebServer(8888)
                .addRegistry(new RegistryConfig().setType("consul").setAddrs("10.1.20.16:8500")
                        .setAclToken("dde69310-cec2-eab7-4082-c53cbd556b25")
                )
                .setDynamicRoutePlugin("consul")
                .addReferer("us", UserService.class, "10.1.20.73:7100,10.1.20.13:7117")
                //.setTraceAdapter("zipkin:server=127.0.0.1:9411")
                //.setTraceAdapter("cat:server=192.168.213.128:8080")
                //.setTraceAdapter("skywalking:server=127.0.0.1:10800")
                .setTraceAdapter("cat:server=10.135.81.135:8081;enabled=false")
                .setName("gate")
                .build();

        app.initAndStart();

        Thread.sleep(12000000);

        app.stopAndClose();

    }

}

