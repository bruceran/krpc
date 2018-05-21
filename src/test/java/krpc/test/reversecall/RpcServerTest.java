package krpc.test.reversecall;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xxx.pushservice.proto.PushReq;
import com.xxx.pushservice.proto.PushService;
import com.xxx.userservice.proto.LoginReq;
import com.xxx.userservice.proto.LoginRes;
import com.xxx.userservice.proto.UpdateProfileReq;
import com.xxx.userservice.proto.UpdateProfileRes;
import com.xxx.userservice.proto.UserService;

import krpc.bootstrap.Bootstrap;
import krpc.bootstrap.RpcApp;
import krpc.core.RpcClientContext;
import krpc.core.RpcContextData;
import krpc.core.RpcServerContext;

public class RpcServerTest {

	static Logger log = LoggerFactory.getLogger(RpcServerTest.class);
	

	public static void main(String[] args) throws Exception {
		
		UserServiceImpl impl = new UserServiceImpl(); // user code is here

		RpcApp app = new Bootstrap() 
			.addService(UserService.class,impl) 
			.addReverseReferer("push",PushService.class) // !!!
			.build();
		
		UserServiceImpl.ps = app.getReferer("push");
		
		app.initAndStart();
		
		Thread.sleep(30000);

		app.stopAndClose();
	}	
		
}

class UserServiceImpl implements UserService {
	
	static Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
	
	public static PushService ps;
		
	int i = 0;
	
	public LoginRes login(LoginReq req) {
		i++;
		
		PushReq pushReq = PushReq.newBuilder().setClientId("123").setMessage("I like you").build();
		
		RpcContextData ctx = RpcServerContext.get(); // got current connId, the connId can be saved in anywhere (memory or db)
		
		RpcClientContext.setConnId(ctx.getConnId()); // set the target connection
		ps.push(pushReq); // do push

		return LoginRes.newBuilder().setRetCode(0).setRetMsg("hello, friend. receive req#"+i).build();
	}
	
	public UpdateProfileRes updateProfile(UpdateProfileReq req) {
		i++;
		return UpdateProfileRes.newBuilder().setRetCode(0).setRetMsg("hello, friend. receive req#"+i).build();
	}
	
}
