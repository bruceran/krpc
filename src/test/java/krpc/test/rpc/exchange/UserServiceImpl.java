package krpc.test.rpc.exchange;

import com.xxx.userservice.proto.*;

class UserServiceImpl implements UserService {

    int i = 0;

    public LoginRes login(LoginReq req) {
        //System.out.println("req="+req);
        ++i;

        try { Thread.sleep(5000); }catch(Throwable e) {}
        return LoginRes.newBuilder().setMobile("111").setRetCode(0).setRetMsg("hello, friend. receive req#" + i).build();
    }

    public Login2Res login2(Login2Req req) {
        //System.out.println("req2="+req);
        ++i;
        return Login2Res.newBuilder().setMobile("222").setRetCode(0).setRetMsg("hello, friend. receive req#" + i).build();
    }

    public UpdateProfileRes updateProfile(UpdateProfileReq req) {
        return UpdateProfileRes.failed(-2000);
    }

}