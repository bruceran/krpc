package krpc.test.javaconfig.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import com.xxx.pushservice.proto.PushReq;
import com.xxx.pushservice.proto.PushService;
import com.xxx.userservice.proto.LoginReq;
import com.xxx.userservice.proto.LoginRes;
import com.xxx.userservice.proto.UpdateProfileReq;
import com.xxx.userservice.proto.UpdateProfileRes;
import com.xxx.userservice.proto.UserService;

import krpc.core.RpcClientContext;
import krpc.core.RpcContextData;
import krpc.core.RpcServerContext;

@Component("userService")
class UserServiceImpl implements UserService, ApplicationContextAware {
	
	static Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
	
	private PushService ps;
		
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

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		ps = (PushService)applicationContext.getBean("push");
	}
	
}
