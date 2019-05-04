package krpc.test.rpc.schema;

import com.xxx.userservice.proto.*;
import krpc.rpc.core.ClientContext;
import krpc.rpc.core.RpcContextData;
import krpc.rpc.core.ServerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
class UserServiceImpl implements UserService {

    static Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    @Autowired
    private PushService pushService;

    @Autowired
    private PushServiceAsync pushServiceAsync;

    int i = 0;

    public UserServiceImpl() {
        System.out.println("UserServiceImpl called");
    }

    @PostConstruct
    public void init() throws Exception {
        System.out.println("pushService=" + pushService);
        System.out.println("pushServiceAsync=" + pushServiceAsync);
    }

    public LoginRes login(LoginReq req) {

        i++;
        return LoginRes.newBuilder().setRetCode(0).setRetMsg("hello, friend mike. receive req#" + i).build();
    }
    public Login2Res login2(Login2Req req) {
        return Login2Res.ok();
    }
    public UpdateProfileRes updateProfile(UpdateProfileReq req) {
        i++;
        return UpdateProfileRes.newBuilder().setRetCode(0).setRetMsg("hello, friend. receive req#" + i).build();
    }

}
