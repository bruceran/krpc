package krpc.test.rpc.xml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xxx.userservice.proto.*;


class UserServiceImpl implements UserService {
	
	static Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
	
	int i = 0;
	
	public LoginRes login(LoginReq req) {
		i++;
		return LoginRes.newBuilder().setRetCode(0).setRetMsg("hello, friend. receive req#"+i).build();
	}
	
	public UpdateProfileRes updateProfile(UpdateProfileReq req) {
		i++;
		return UpdateProfileRes.newBuilder().setRetCode(0).setRetMsg("hello, friend. receive req#"+i).build();
	}

	
}
