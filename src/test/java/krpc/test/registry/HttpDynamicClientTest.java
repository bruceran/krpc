package krpc.test.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import krpc.rpc.bootstrap.Bootstrap;
import krpc.rpc.bootstrap.RefererConfig;
import krpc.rpc.bootstrap.RegistryConfig;
import krpc.rpc.bootstrap.RpcApp;

public class HttpDynamicClientTest {

	static Logger log = LoggerFactory.getLogger(HttpDynamicClientTest.class);
	
	public static void main(String[] args) throws Exception {

		RpcApp app = new Bootstrap()
			.addWebServer(8888) 
			.addRegistry(new RegistryConfig().setType("consul").setAddrs("192.168.31.144:8500"))			
			.addReferer(new RefererConfig("us").setServiceId(100).setRegistryName("consul")) 
			//.addReferer("us",100,"127.0.0.1:5600") 			
			.setTraceAdapter("skywalking")
			.build();
		
		app.initAndStart();
		
		Thread.sleep(120000);

		app.stopAndClose();

	}	
		
}

