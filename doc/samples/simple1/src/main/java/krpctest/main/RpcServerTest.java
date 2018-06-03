package krpctest.main;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xxx.userservice.proto.UserService;

import krpc.rpc.bootstrap.Bootstrap;
import krpc.rpc.bootstrap.RpcApp;
import krpc.rpc.bootstrap.ServerConfig;

public class RpcServerTest {

	static Logger log = LoggerFactory.getLogger(RpcServerTest.class);
	
	public static void main(String[] args) throws Exception {
		
		int ioThreads = getProtperty("KRPC_IOTHREADS",1);
		int workThreads = getProtperty("KRPC_WORKTHREADS",-1);
		
		UserServiceImpl impl = new UserServiceImpl(); // user code is here

		RpcApp app = new Bootstrap() 
			.addServer(new ServerConfig().setPort(5600).setIoThreads(ioThreads).setThreads(workThreads).setNotifyThreads(-1))
			.addService(UserService.class,impl) 
			.build();
		
		app.initAndStart();
		
		Thread.sleep(10000000);

		app.stopAndClose();
		
	}	
		
	static int getProtperty(String key,int defaultValue) {
		String s = System.getProperty(key);
		if( s == null || s.isEmpty() ) return defaultValue;
		return Integer.parseInt(s);
	}
}

