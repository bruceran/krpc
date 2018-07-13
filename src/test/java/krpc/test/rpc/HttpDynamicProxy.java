package krpc.test.rpc;

import krpc.rpc.bootstrap.Bootstrap;
import krpc.rpc.bootstrap.RpcApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpDynamicProxy {

    static Logger log = LoggerFactory.getLogger(HttpDynamicProxy.class);

    public static void main(String[] args) throws Exception {

        RpcApp app = new Bootstrap()
                .addWebServer(8888)
                .addReferer("us", 100, "127.0.0.1:5600")
                .setTraceAdapter("skywalking")
                .build();

        app.initAndStart();

        Thread.sleep(120000);

        app.stopAndClose();

    }

}

