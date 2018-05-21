package krpc.test.call;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xxx.userservice.proto.UserService;
import com.xxx.userservice.proto.UserServiceAsync;

import krpc.bootstrap.Bootstrap;
import krpc.bootstrap.RpcApp;

public class HttpClientTest {

	static Logger log = LoggerFactory.getLogger(HttpClientTest.class);
	
	public static void main(String[] args) throws Exception {

		RpcApp app = new Bootstrap()
			.addWebServer(8888) 
			.addReferer("us",UserService.class,"127.0.0.1:5600") 
			.addReferer("usa",UserServiceAsync.class,"127.0.0.1:5600") 
			.build();
		
		app.initAndStart();
		
		Thread.sleep(120000);

		app.stopAndClose();

	}	
		
}

