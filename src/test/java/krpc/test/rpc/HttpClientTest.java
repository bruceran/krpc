package krpc.test.rpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xxx.userservice.proto.UserService;
import com.xxx.userservice.proto.UserServiceAsync;

import krpc.rpc.bootstrap.Bootstrap;
import krpc.rpc.bootstrap.RpcApp;

public class HttpClientTest {

	static Logger log = LoggerFactory.getLogger(HttpClientTest.class);
	
	public static void main(String[] args) throws Exception {

		RpcApp app = new Bootstrap()
			.addWebServer(8888) 
			.addReferer("us",UserService.class,"127.0.0.1:5600") 
			//.setTraceAdapter("zipkin:server=127.0.0.1:9411")
			//.setTraceAdapter("cat:server=192.168.213.128:8080")
			//.setTraceAdapter("skywalking:server=127.0.0.1:10800")
			.setName("gate")
			.build();
		
		app.initAndStart();
		
		Thread.sleep(12000000);

		app.stopAndClose();

	}	
		
}

