package krpc.monitorserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import krpc.rpc.bootstrap.Bootstrap;
import krpc.rpc.bootstrap.RpcApp;

public class Main {

	static Logger log = LoggerFactory.getLogger(Main.class);
	
	public static void main(String[] args) throws Exception {
		
		DefaultMonitorService impl = new DefaultMonitorService();

		RpcApp app = new Bootstrap()
			.addServer(8864)
			.addService(MonitorService.class,impl) 
			.build();
		
		app.initAndStart();
		
		Thread.sleep(300000);

		app.stopAndClose();
	}	
		
}
