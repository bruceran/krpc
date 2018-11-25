package krpc.test.rpc.encrypt;

import com.xxx.userservice.proto.*;
import krpc.rpc.bootstrap.Bootstrap;
import krpc.rpc.bootstrap.ClientConfig;
import krpc.rpc.bootstrap.RpcApp;
import krpc.rpc.core.ClientContext;
import krpc.rpc.core.ClientContextData;
import krpc.rpc.impl.transport.TransportBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class RpcClient {

    static Logger log = LoggerFactory.getLogger(RpcClient.class);

    public static void main(String[] args) throws Exception {

        RpcApp app = new Bootstrap()
                .addClient(new ClientConfig().setConnections(1).setEnableEncrypt(true))
                .addReferer("us", UserService.class, "127.0.0.1:5600")
                .build();

        app.initAndStart();


        // user code

        //Thread.sleep(2000);

        UserService us = app.getReferer("us");

        LoginReq req = LoginReq.newBuilder().setUserName("abc").setPassword("mmm").build();
        LoginRes res = us.login(req); // shake hands
        log.info("res=" + res.getRetCode() + "," + res.getRetMsg());
        if( res.getRetCode() != 0 ) {
            return;
        }


        String key = res.getRetMsg(); // get aes key
        ClientContextData data = ClientContext.get();
        String connId = data.getConnId();
        TransportBase t = (TransportBase)(app.getClient().getTransport());
        t.putKey(connId,key); // put aes per connection

        UpdateProfileReq req1 = UpdateProfileReq.newBuilder().build();
        UpdateProfileRes res1 = us.updateProfile(req1);

        Thread.sleep(300000);

        app.stopAndClose();

        ((ch.qos.logback.classic.Logger) log).getLoggerContext().stop();
    }


}

