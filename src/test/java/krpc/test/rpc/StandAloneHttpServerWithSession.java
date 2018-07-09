package krpc.test.rpc;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.xxx.userservice.proto.UserService;

import krpc.rpc.bootstrap.Bootstrap;
import krpc.rpc.bootstrap.RpcApp;
import krpc.rpc.bootstrap.WebServerConfig;


public class StandAloneHttpServerWithSession {

	static Logger log = LoggerFactory.getLogger(StandAloneHttpServerWithSession.class);
	
	public static void main(String[] args) throws Exception {
		
		UserServiceImpl impl = new UserServiceImpl(); // in file: HttpServerTest.java

		RpcApp app = new Bootstrap()
			.addWebServer(new WebServerConfig(8888).setDefaultSessionService("jedissessionservice:addrs=127.0.0.1:6379")) 
			.addServer(5600) 
			.addService(UserService.class,impl) 
			.build();
		
		app.initAndStart();
		
		Thread.sleep(120000);

		app.stopAndClose();
		
		impl.t.interrupt();
		
	}	
		
}

