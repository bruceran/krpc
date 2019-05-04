package krpc.test.rpc.exchange;

import com.xxx.userservice.proto.*;
import krpc.rpc.bootstrap.Bootstrap;
import krpc.rpc.bootstrap.RpcApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RpcClient {

    static Logger log = LoggerFactory.getLogger(RpcClient.class);

    public static void main(String[] args) throws Exception {

        RpcApp app = new Bootstrap()
                .addReferer("us", UserService.class, "127.0.0.1:5601")
                .build();

        app.initAndStart();

        // user code

        //Thread.sleep(2000);

        UserService us = app.getReferer("us");

        for(int i=0;i<1;++i) {


            Login2Req req2 = Login2Req.newBuilder().setUserName("abc").setPassword("mmm").setGender("female").build();
            Login2Res res2 = us.login2(req2);
            log.info("res2=" + res2.getRetCode() + "," + res2.getRetMsg() + ", mobile=" + res2.getMobile());

            LoginReq req = LoginReq.newBuilder().setUserName("abc").setPassword("mmm").setGender("male").build();
            LoginRes res = us.login(req);
            log.info("res=" + res.getRetCode() + "," + res.getRetMsg() + ", mobile=" + res.getMobile());

            Thread.sleep(5000);

        }

//        UpdateProfileRes res3 = us.updateProfile(UpdateProfileReq.newBuilder().setMobile("abc").build());
//        log.info("res3=" + res3.getRetCode() + "," + res3.getRetMsg());

        Thread.sleep(300000);

        app.stopAndClose();

        ((ch.qos.logback.classic.Logger) log).getLoggerContext().stop();
    }


}

