package krpc.test.rpc.reversecall;

import com.xxx.userservice.proto.*;
import krpc.rpc.bootstrap.Bootstrap;
import krpc.rpc.bootstrap.RpcApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RpcClient {

    static Logger log = LoggerFactory.getLogger(RpcClient.class);

    public static void main(String[] args) throws Exception {

        PushServiceImpl impl = new PushServiceImpl();

        RpcApp app = new Bootstrap()
                .addReferer("us", UserService.class, "127.0.0.1:5600")
                .addReverseService(PushService.class, impl)  // !!!
                .build();

        app.initAndStart();

        // user code

        UserService us = app.getReferer("us");

        LoginReq req = LoginReq.newBuilder().setUserName("abc").setPassword("mmm").build();
        LoginRes res = us.login(req);
        log.info("res=" + res.getRetCode() + "," + res.getRetMsg());

        // user code end

        Thread.sleep(5000);

        app.stopAndClose();
    }

}

class PushServiceImpl implements PushService {

    static Logger log = LoggerFactory.getLogger(PushServiceImpl.class);

    int i = 0;

    public PushRes push(PushReq req) {
        log.debug("received:" + req.getMessage() + "," + req.getClientId());
        return PushRes.newBuilder().setRetCode(0).setRetMsg("hello, I have recieved your push!" + (++i)).build();
    }

}

