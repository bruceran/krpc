package krpc.test.rpc.schema2;

import com.xxx.userservice.proto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
class UserServiceImpl  implements UserService  {

    static Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    @Autowired
    private PushService pushService;

    @Autowired
    private PushServiceAsync pushServiceAsync;

    @Autowired
    TempBean1 tempBean;

    @Autowired
    private PushServicev2 pushv2;

    int i = 0;

    public UserServiceImpl() {
        System.out.println("UserServiceImpl called");
    }

    @PostConstruct
    public void init() throws Exception {
        System.out.println("pushService=" + pushService);
        System.out.println("pushServiceAsync=" + pushServiceAsync);
        System.out.println("tempBean=" + tempBean);
        System.out.println("pushv2=" + pushv2);
    }

    public LoginRes login(LoginReq req) {

        i++;
        return LoginRes.newBuilder().setRetCode(0).setRetMsg("hello, friend mike. receive req#" + i).build();
    }

    public UpdateProfileRes updateProfile(UpdateProfileReq req) {
        i++;
        return UpdateProfileRes.newBuilder().setRetCode(0).setRetMsg("hello, friend. receive req#" + i).build();
    }

    public Login2Res login2(Login2Req req) {
        return Login2Res.ok();
    }

}
