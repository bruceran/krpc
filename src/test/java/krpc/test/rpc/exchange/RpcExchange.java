package krpc.test.rpc.exchange;

import com.xxx.userservice.proto.UserService;
import krpc.rpc.bootstrap.Bootstrap;
import krpc.rpc.bootstrap.RpcApp;
import krpc.rpc.bootstrap.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RpcExchange {

    static Logger log = LoggerFactory.getLogger(RpcExchange.class);

    public static void main(String[] args) throws Exception {

        UserServiceImpl impl = new UserServiceImpl(); // user code is here

        RpcApp app = new Bootstrap()
                .addServer(new ServerConfig().setPort(5601).setExchangeServiceIds("100"))
                .addReferer("us", UserService.class, "127.0.0.1:5600")
                .build();

        app.initAndStart();

        Thread.sleep(3000000);

        app.stopAndClose();

    }
}

