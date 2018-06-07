package krpctest.main;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xxx.userservice.proto.UserService;

import krpc.rpc.bootstrap.Bootstrap;
import krpc.rpc.bootstrap.ClientConfig;
import krpc.rpc.bootstrap.RpcApp;
import krpc.rpc.bootstrap.WebServerConfig;

public class HttpServerTest {

	static Logger log = LoggerFactory.getLogger(HttpServerTest.class);
	
	public static void main(String[] args) throws Exception {
		
		int ioThreads = getProtperty("KRPC_IOTHREADS",1);
		int workThreads = getProtperty("KRPC_WORKTHREADS",-1);

		int clientConns = getProtperty("KRPC_CLIENT_CONNS",1);
		int clientIoThreads = getProtperty("KRPC_CLIENT_IOTHREADS",1);
		int clientNotifyThreads = getProtperty("KRPC_CLIENT_NOTIFYTHREADS",-1);

		RpcApp app = new Bootstrap() 
			.addWebServer(new WebServerConfig().setPort(8888).setIoThreads(ioThreads).setThreads(workThreads))
			.addClient(new ClientConfig().setConnections(clientConns).setIoThreads(clientIoThreads).setNotifyThreads(clientNotifyThreads))
			.addReferer("us",UserService.class,"127.0.0.1:5600") 
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
