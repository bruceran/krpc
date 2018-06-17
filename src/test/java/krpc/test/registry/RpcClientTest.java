package krpc.test.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xxx.userservice.proto.LoginReq;
import com.xxx.userservice.proto.LoginRes;
import com.xxx.userservice.proto.UserService;

import krpc.rpc.bootstrap.Bootstrap;
import krpc.rpc.bootstrap.RefererConfig;
import krpc.rpc.bootstrap.RegistryConfig;
import krpc.rpc.bootstrap.RpcApp;

public class RpcClientTest {

	static Logger log = LoggerFactory.getLogger(RpcClientTest.class);
	
	public static void main(String[] args) throws Exception {
		
		RpcApp app = new Bootstrap() 
				.setDynamicRoutePlugin("zookeeper")
				//.addRegistry(new RegistryConfig().setType("consul").setAddrs("192.168.31.144:8500"))			
				//.addReferer(new RefererConfig("us").setInterfaceName(UserService.class.getName()).setRegistryName("consul")) 
				//.addRegistry(new RegistryConfig().setType("etcd").setAddrs("192.168.31.144:2379"))			
				//.addReferer(new RefererConfig("us").setInterfaceName(UserService.class.getName()).setRegistryName("etcd")) 
				.addRegistry(new RegistryConfig().setType("zookeeper").setAddrs("192.168.31.144:2181"))			
				.addReferer(new RefererConfig("us").setInterfaceName(UserService.class.getName()).setRegistryName("zookeeper")) 				
				//.addRegistry(new RegistryConfig().setType("jedis").setAddrs("127.0.0.1:6379"))			
				//.addReferer(new RefererConfig("us").setInterfaceName(UserService.class.getName()).setRegistryName("jedis")) 				
				//.addReferer(new RefererConfig("us").setInterfaceName(UserService.class.getName())) 				
				.build();
		
		app.initAndStart();

		for(int i=0;i<100;++i) {
			UserService us = app.getReferer("us");

			LoginReq req = LoginReq.newBuilder().setUserName("abc").setPassword("mmm").build();
			LoginRes res = us.login(req);
			log.info("res="+res.getRetCode()+","+res.getRetMsg());

			Thread.sleep(500);
		}

		Thread.sleep(300000);
		
		app.stopAndClose();
	}	
	
		
}

