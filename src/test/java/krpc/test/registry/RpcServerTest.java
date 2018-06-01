package krpc.test.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xxx.userservice.proto.LoginReq;
import com.xxx.userservice.proto.LoginRes;
import com.xxx.userservice.proto.UpdateProfileReq;
import com.xxx.userservice.proto.UpdateProfileRes;
import com.xxx.userservice.proto.UserService;

import krpc.rpc.bootstrap.Bootstrap;
import krpc.rpc.bootstrap.RegistryConfig;
import krpc.rpc.bootstrap.RpcApp;
import krpc.rpc.bootstrap.ServiceConfig;
import krpc.rpc.core.RpcContextData;
import krpc.rpc.core.ServerContext;

public class RpcServerTest {

	static Logger log = LoggerFactory.getLogger(RpcServerTest.class);
	
	public static void main(String[] args) throws Exception {
		
		UserServiceImpl impl = new UserServiceImpl(); // user code is here

		RpcApp app = new Bootstrap() 
			.addRegistry(new RegistryConfig().setType("consul").setAddrs("192.168.31.144:8500"))			
			.addService(new ServiceConfig().setInterfaceName(UserService.class.getName()).setImpl(impl).setRegistryNames("consul")) 
			.build();
		
		app.initAndStart();
		
		Thread.sleep(3000000);

		app.stopAndClose();

	}	
}

class UserServiceImpl implements UserService {
	
	static Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

	public LoginRes login(LoginReq req) {
		
		RpcContextData ctx = ServerContext.get();
		log.info("login received, peers="+ctx.getMeta().getPeers());
		return LoginRes.newBuilder().setRetCode(0).setRetMsg("hello, friend. receive req ").build();
	}
	
	public UpdateProfileRes updateProfile(UpdateProfileReq req) {
		UpdateProfileRes res = UpdateProfileRes.newBuilder().setRetCode(-100002).build();
		return res;
	}


}