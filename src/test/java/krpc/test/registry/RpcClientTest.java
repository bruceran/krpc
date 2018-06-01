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
				.addRegistry(new RegistryConfig().setType("consul").setAddrs("192.168.31.144:8500"))			
				.addReferer(new RefererConfig("us").setInterfaceName(UserService.class.getName()).setRegistryName("consul")) 
				.build();
		
		app.initAndStart();

		UserService us = app.getReferer("us");

		LoginReq req = LoginReq.newBuilder().setUserName("abc").setPassword("mmm").build();
		LoginRes res = us.login(req);
		log.info("res="+res.getRetCode()+","+res.getRetMsg());

		Thread.sleep(120000);
		
		app.stopAndClose();
	}	
	
		
}

