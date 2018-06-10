package krpc.test.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xxx.userservice.proto.UserService;

import krpc.rpc.bootstrap.Bootstrap;
import krpc.rpc.bootstrap.RegistryConfig;
import krpc.rpc.bootstrap.RpcApp;
import krpc.rpc.bootstrap.ServiceConfig;

public class RpcServerTest2 {

	static Logger log = LoggerFactory.getLogger(RpcServerTest2.class);
	
	public static void main(String[] args) throws Exception {
		
		UserServiceImpl impl = new UserServiceImpl(); // user code is here

		RpcApp app = new Bootstrap() 
			.addServer(5601)
			.addRegistry(new RegistryConfig().setType("consul").setAddrs("192.168.31.144:8500"))			
			.addService(new ServiceConfig().setInterfaceName(UserService.class.getName()).setImpl(impl).setRegistryNames("consul").setGroup("test")) 
			//.addRegistry(new RegistryConfig().setType("etcd").setAddrs("192.168.31.144:2379"))			
			//.addService(new ServiceConfig().setInterfaceName(UserService.class.getName()).setImpl(impl).setRegistryNames("etcd")) 
			.build();
		
		app.initAndStart();
		
		Thread.sleep(5000);

		app.stopAndClose();

	}	
}

