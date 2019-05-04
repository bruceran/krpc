package krpc.test.rpc.javaconfig.server;

import com.xxx.userservice.proto.*;
import krpc.rpc.core.ClientContext;
import krpc.rpc.core.RpcContextData;
import krpc.rpc.core.ServerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("userService")
class UserServiceImpl implements UserService {

    static Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    @Autowired
    private PushService ps;

    int i = 0;

    public LoginRes login(LoginReq req) {
        i++;

        PushReq pushReq = PushReq.newBuilder().setClientId("123").setMessage("I like you").build();

        RpcContextData ctx = ServerContext.get(); // got current connId, the connId can be saved in anywhere (memory or db)

        ClientContext.setConnId(ctx.getConnId()); // set the target connection
        ps.push(pushReq); // do push

        return LoginRes.newBuilder().setRetCode(0).setRetMsg("hello, friend. receive req#" + i).build();
    }

    public UpdateProfileRes updateProfile(UpdateProfileReq req) {
        i++;
        return UpdateProfileRes.newBuilder().setRetCode(0).setRetMsg("hello, friend. receive req#" + i).build();
    }

    public Login2Res login2(Login2Req req) {
        return Login2Res.ok();
    }

}
