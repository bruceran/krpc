package krpc.test.rpc.encrypt;

import com.xxx.userservice.proto.*;
import krpc.rpc.bootstrap.Bootstrap;
import krpc.rpc.bootstrap.ClientConfig;
import krpc.rpc.bootstrap.RpcApp;
import krpc.rpc.bootstrap.ServerConfig;
import krpc.rpc.core.RpcClosure;
import krpc.rpc.core.RpcContextData;
import krpc.rpc.core.ServerContext;
import krpc.rpc.impl.transport.TransportBase;
import krpc.trace.Trace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;

public class RpcServer {

    static Logger log = LoggerFactory.getLogger(RpcServer.class);

    static public RpcApp app;
    public static void main(String[] args) throws Exception {

        UserServiceImpl impl = new UserServiceImpl(); // user code is here

        app = new Bootstrap()
                .addServer(new ServerConfig().setEnableEncrypt(true))
                .addService(UserService.class, impl)
                .build();

        app.initAndStart();

        Thread.sleep(3000000);

        app.stopAndClose();
    }
}

class UserServiceImpl implements UserService {

    static Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    public LoginRes login(LoginReq req) {
        String connId  = ServerContext.get().getConnId();
        TransportBase t = (TransportBase)(RpcServer.app.getServer().getTransport());
        String key = "a39#@@$$r2134cdc";
        t.putKey(connId,key);

        System.out.println("login received");
        return LoginRes.newBuilder().setRetCode(0).setRetMsg(key).build();
    }

    public UpdateProfileRes updateProfile(UpdateProfileReq req) {
        System.out.println("updateProfile received" );
        return UpdateProfileRes.newBuilder().setRetMsg("abc").build();
    }

}