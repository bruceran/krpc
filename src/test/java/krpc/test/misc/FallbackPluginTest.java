package krpc.test.misc;

import com.google.protobuf.Message;
import com.xxx.userservice.proto.LoginReq;
import com.xxx.userservice.proto.LoginRes;
import com.xxx.userservice.proto.UserService;
import krpc.rpc.core.ClientContextData;
import krpc.rpc.core.proto.RpcMeta;
import krpc.rpc.impl.DefaultFallbackPlugin;
import krpc.rpc.impl.DefaultServiceMetas;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FallbackPluginTest {

    @Test
    public void test1() throws Exception {

        UserServiceImpl impl = new UserServiceImpl();
        DefaultServiceMetas metas = new DefaultServiceMetas();
        metas.addReferer(UserService.class, impl, null);

        DefaultFallbackPlugin c = new DefaultFallbackPlugin();
        c.setServiceMetas(metas);
        c.init();

        RpcMeta meta = RpcMeta.newBuilder().setDirection(RpcMeta.Direction.REQUEST).setServiceId(100).setMsgId(1).build();
        ClientContextData ctx = new ClientContextData("no_connection:0:0", meta, null, null);

        LoginReq req = LoginReq.newBuilder().setUserName("abc").setPassword("123").build();
        Message res = c.fallback(ctx, req);
        LoginRes loginRes = (LoginRes) res;
        assertEquals(0, loginRes.getRetCode());

        req = LoginReq.newBuilder().setUserName("abcd").setPassword("123").build();
        res = c.fallback(ctx, req);
        loginRes = (LoginRes) res;
        assertEquals(-1000000, loginRes.getRetCode());
    }


}

