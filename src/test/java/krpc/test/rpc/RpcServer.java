package krpc.test.rpc;

import com.xxx.userservice.proto.*;
import krpc.rpc.bootstrap.Bootstrap;
import krpc.rpc.bootstrap.RpcApp;
import krpc.rpc.core.RpcClosure;
import krpc.rpc.core.RpcContextData;
import krpc.rpc.core.ServerContext;
import krpc.trace.Span;
import krpc.trace.Trace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;

public class RpcServer {

    static Logger log = LoggerFactory.getLogger(RpcServer.class);

    public static void main(String[] args) throws Exception {

        UserServiceImpl impl = new UserServiceImpl(); // user code is here

        RpcApp app = new Bootstrap()
                .addService(UserService.class, impl)
                //.setTraceAdapter("zipkin:server=127.0.0.1:9411")
                //.setTraceAdapter("cat:server=10.135.81.135:8081")
                //.setTraceAdapter("skywalking:server=127.0.0.1:10800")
                //.setName("uss")
                //.setSelfCheckPort(9910)
                .build();

        app.initAndStart();

        Thread.sleep(3000000);

        app.stopAndClose();

        impl.t.interrupt();
    }
}

class UserServiceImpl implements UserService {

    static Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    int i = 0;
    ArrayBlockingQueue<RpcClosure> queue = new ArrayBlockingQueue<>(100);
    Thread t;

    UserServiceImpl() {
        t = new Thread(() -> run());
        t.start();
    }

    public LoginRes login(LoginReq req) {
        System.out.println("username=" + req.getUserName());
        RpcContextData ctx = ServerContext.get();

        Span span = Trace.start("DB", "queryUser");
        try {
            Thread.sleep(100);
        } catch (Exception e) {
        }
        span.logEvent("QUERY", "FINDUSER", "SUCCESS","test");
        span.logEvent("QUERY", "FINDUSER", "ERROR", "id not found");
        span.tag("secret", "xxx");
        span.stop();

        span = Trace.start("REDIS", "set");
        try {
            Thread.sleep(100);
        } catch (Exception e) {
        }
        span.setRemoteAddr("10.1.2.198:8909");
        span.tag("userId", "mmm");
        span.tag("userName", "nnn");
        span.stop(false);

        span.incCount("ORDER_COUNT");
        span.incSum("ORDER_AMOUNT", 100);

        span.logEvent("QUERY", "GETRATE", "SUCCESS", "test");


        try {
            throw new RuntimeException("test");
        } catch (Exception e) {
            span.logException(e);
        }
        /*	*/

        log.info("login received, peers=" + ctx.getMeta().getTrace().getPeers());
        i++;
        return LoginRes.newBuilder().setRetCode(0).setRetMsg("hello, friend. receive req#" + i).build();
    }
    public Login2Res login2(Login2Req req) {
        return Login2Res.ok();
    }
    public UpdateProfileRes updateProfile(UpdateProfileReq req) {
        i++;
        RpcClosure u = ServerContext.closure(req); // !!! you can pass this object anywhere
        queue.offer(u);
        return null;
    }

    public void run() {
        try {
            while (true) {
                RpcClosure c = queue.take();
                c.restoreContext();
                log.info("async updateProfile received req#" + i);
                //try { Thread.sleep(3000); } catch(Exception e) {}
                UpdateProfileRes res = UpdateProfileRes.newBuilder().setRetCode(-100002).build();
                c.done(res); // !!! call this anytime if you have get response
            }
        } catch (Exception e) {
        }
    }

}