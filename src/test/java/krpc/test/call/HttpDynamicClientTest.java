package krpc.test.call;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xxx.userservice.proto.UserService;

import krpc.bootstrap.Bootstrap;
import krpc.bootstrap.RpcApp;
import krpc.bootstrap.WebServerConfig;

public class HttpDynamicClientTest {

	static Logger log = LoggerFactory.getLogger(HttpDynamicClientTest.class);
	
	public static void main(String[] args) throws Exception {

		RpcApp app = new Bootstrap()
			.addWebServer(8888) 
			.addReferer("us",100,"127.0.0.1:5600") 			
			.build();
		
		app.initAndStart();
		
		Thread.sleep(120000);

		app.stopAndClose();

	}	
		
}

