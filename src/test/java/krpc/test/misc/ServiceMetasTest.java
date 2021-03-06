package krpc.test.misc;

import com.xxx.userservice.proto.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import krpc.rpc.core.ReflectionUtils;
import krpc.rpc.core.RpcData;
import krpc.rpc.core.proto.RpcMeta;
import krpc.rpc.impl.DefaultProxyGenerator;
import krpc.rpc.impl.DefaultServiceMetas;
import krpc.rpc.impl.RpcClient;
import krpc.rpc.impl.transport.DefaultRpcCodec;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

class UserServiceImpl implements UserService {
    public LoginRes login(LoginReq req) {
        return LoginRes.newBuilder().setRetCode(0).setRetMsg("hello, friend.").build();
    }
    public Login2Res login2(Login2Req req) {
        return Login2Res.ok();
    }
    public UpdateProfileRes updateProfile(UpdateProfileReq req) {
        return UpdateProfileRes.newBuilder().setRetCode(0).setRetMsg("hello, friend.").build();
    }

}

public class ServiceMetasTest {

    @Test
    public void test1() throws Exception {

        UserServiceImpl impl = new UserServiceImpl();
        ReflectionUtils.getMethodInfo(impl.getClass());

        LoginRes ret = LoginRes.newBuilder().setRetCode(-100).build();
        assertEquals(-100, ReflectionUtils.getRetCode(ret));

        LoginRes ret2 = (LoginRes) ReflectionUtils.generateResponseObject(LoginRes.class, -400, "bad request");
        assertEquals(-400, ret2.getRetCode());
        assertEquals("bad request", ret2.getRetMsg());
    }

    @Test
    public void test2() {
        UserServiceImpl impl = new UserServiceImpl();
        LoginRes ret = LoginRes.newBuilder().setRetCode(-100).build();
        ReflectionUtils.checkInterface(UserService.class, impl);
        try {
            ReflectionUtils.checkInterface(UserService.class, ret);
        } catch (Exception e) {
            assertTrue(true);
        }
    }

    @Test
    public void test3() {
        UserServiceImpl impl = new UserServiceImpl();
        assertEquals(100, ReflectionUtils.getServiceId(UserService.class));
        System.out.println(ReflectionUtils.getMsgIds(UserService.class));
        System.out.println(ReflectionUtils.getMethodInfo(impl.getClass()));

        DefaultServiceMetas metas = new DefaultServiceMetas();
        metas.addService(UserService.class, impl, null);

        assertTrue(metas.findService(100) != null);
        assertTrue(metas.findMethod(100, 1) != null);
        assertTrue(metas.findMethod(100, 2) != null);
        assertTrue(metas.findReqClass(100, 1) != null);
        assertTrue(metas.findResClass(100, 1) != null);
    }

    @Test
    public void test4() {
        UserServiceImpl impl = new UserServiceImpl();
        DefaultServiceMetas metas = new DefaultServiceMetas();
        metas.addService(UserService.class, impl, null);
        DefaultRpcCodec codec = new DefaultRpcCodec(metas);

        LoginReq req = LoginReq.newBuilder().setUserName("abc").setPassword("mmm").build();
        RpcMeta meta = RpcMeta.newBuilder().setDirection(RpcMeta.Direction.REQUEST).setServiceId(100).setMsgId(1).setSequence(1001).build(); // // heartbeat package donot need sequence
        RpcData data = new RpcData(meta, req);
        ByteBuf bb = Unpooled.buffer();
        codec.encode(data, bb,null);
        RpcMeta meta2 = codec.decodeMeta(bb);
        RpcData data2 = codec.decodeBodyAsMessage(meta2, bb,null);
        LoginReq req2 = (LoginReq) data2.getBody();
        assertEquals("abc", req2.getUserName());
        assertEquals("mmm", req2.getPassword());
        assertEquals(1001, data2.getMeta().getSequence());
    }

    @Test
    public void test5() {
        UserServiceImpl impl = new UserServiceImpl();
        DefaultServiceMetas metas = new DefaultServiceMetas();
        metas.addService(UserService.class, impl, null);
        DefaultRpcCodec codec = new DefaultRpcCodec(metas);

        LoginReq req = LoginReq.newBuilder().build(); // test empty parameter
        RpcMeta meta = RpcMeta.newBuilder().setDirection(RpcMeta.Direction.REQUEST).setServiceId(100).setMsgId(1).setSequence(1001).build(); // // heartbeat package donot need sequence
        RpcData data = new RpcData(meta, req);
        ByteBuf bb = Unpooled.buffer();
        codec.encode(data, bb,null);
        RpcMeta meta2 = codec.decodeMeta(bb);
        RpcData data2 = codec.decodeBodyAsMessage(meta2, bb,null);
    }

    @Test
    public void test6() {
        RpcClient client = new RpcClient();
        DefaultServiceMetas metas = new DefaultServiceMetas();
        DefaultProxyGenerator proxy = new DefaultProxyGenerator();
        Object impl = proxy.generateReferer(UserService.class, client);
        metas.addReferer(UserService.class, impl, client);
        Object aimpl = proxy.generateAsyncReferer(UserServiceAsync.class, client);
        metas.addAsyncReferer(UserServiceAsync.class, aimpl, client);

        assertTrue(metas.findReferer(100) != null);
        assertTrue(metas.findMethod(100, 1) != null);
        assertTrue(metas.findMethod(100, 2) != null);
        assertTrue(metas.findReqClass(100, 1) != null);
        assertTrue(metas.findResClass(100, 1) != null);

        assertTrue(metas.findAsyncReferer(100) != null);
        assertTrue(metas.findAsyncMethod(100, 1) != null);
        assertTrue(metas.findAsyncMethod(100, 2) != null);
    }

}

