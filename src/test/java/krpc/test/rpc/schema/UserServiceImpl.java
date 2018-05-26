package krpc.test.rpc.schema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.xxx.pushservice.proto.PushReq;
import com.xxx.pushservice.proto.PushService;
import com.xxx.userservice.proto.LoginReq;
import com.xxx.userservice.proto.LoginRes;
import com.xxx.userservice.proto.UpdateProfileReq;
import com.xxx.userservice.proto.UpdateProfileRes;
import com.xxx.userservice.proto.UserService;

import krpc.rpc.core.ClientContext;
import krpc.rpc.core.RpcContextData;
import krpc.rpc.core.ServerContext;

class UserServiceImpl implements UserService {
	
	static Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
	
	int i = 0;
	
	public LoginRes login(LoginReq req) {
		i++;
		return LoginRes.newBuilder().setRetCode(0).setRetMsg("hello, friend mike. receive req#"+i).build();
	}
	
	public UpdateProfileRes updateProfile(UpdateProfileReq req) {
		i++;
		return UpdateProfileRes.newBuilder().setRetCode(0).setRetMsg("hello, friend. receive req#"+i).build();
	}

	
}
