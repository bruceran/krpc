package krpc.bootstrap;

import java.util.ArrayList;
import java.util.HashMap;

import krpc.core.ErrorMsgConverter;
import krpc.core.FlowControl;
import krpc.core.InitClose;
import krpc.core.InitCloseUtils;
import krpc.core.MockService;
import krpc.core.ProxyGenerator;
import krpc.core.RegistryManager;
import krpc.core.RpcCodec;
import krpc.core.ServiceMetas;
import krpc.core.StartStop;
import krpc.core.TraceIdGenerator;
import krpc.impl.RpcClient;
import krpc.impl.RpcServer;
import krpc.web.WebMonitorService;
import krpc.web.impl.WebServer;

public class RpcApp implements InitClose,StartStop {

	String name;

	TraceIdGenerator traceIdGenerator;
	ServiceMetas serviceMetas;
	RpcCodec codec;
	ProxyGenerator proxyGenerator;
	FlowControl flowControl;
	ErrorMsgConverter errorMsgConverter;
	MockService mockService;
	WebMonitorService monitorService;
	RegistryManager registryManager;
	
	HashMap<String,RpcServer> servers = new HashMap<>();
	HashMap<String,WebServer> webServers = new HashMap<>();
	HashMap<String,RpcClient> clients = new HashMap<>();
	
	HashMap<String,Object> services = new HashMap<>();
	HashMap<String,Object> referers = new HashMap<>();

	ArrayList<Object> resources = new ArrayList<Object>();
	
	public RpcApp() {
	}

	public RpcApp(String name) {
		this.name = name;
	}


	public RpcServer getServer() {
		return servers.get("default");
	}
	
	public RpcServer getServer(String name) {
		return servers.get(name);
	}

	public WebServer getWebServer() {
		return webServers.get("default");
	}
	
	public WebServer getWebServer(String name) {
		return webServers.get(name);
	}
	
	public RpcClient getClient() {
		return clients.get("default");
	}

	public RpcClient getClient(String name) {
		return clients.get(name);
	}
	
	@SuppressWarnings("unchecked")
	public <T> T getService(String name) {
		return (T)services.get(name);
	}
	
	@SuppressWarnings("unchecked")
	public <T> T getReferer(String name) {
		return (T)referers.get(name);
	}
	
	void initInner() {
		
		resources.add(serviceMetas);
		resources.add(codec);
		resources.add(proxyGenerator);
		resources.add(flowControl);
		resources.add(errorMsgConverter);
		resources.add(monitorService);
		resources.add(mockService);

		for(RpcClient client:clients.values()) {
			resources.add(client);
		}

		resources.add(registryManager);

		for(RpcServer server:servers.values()) {
			resources.add(server);
		}		
		
		for(WebServer webServer:webServers.values()) {
			resources.add(webServer);
		}		
	}
	
	public void init() {
		initInner();
		InitCloseUtils.init(resources);
	}
	
	public void start() {
		InitCloseUtils.start(resources);
	}

	public RpcApp initAndStart() {
		init();
		start();
		return this;
	}
	
	public void stop() {
		InitCloseUtils.stop(resources);
	}	
	
	public void close() {
		InitCloseUtils.close(resources);
	}	
	
	public void stopAndClose() {
		stop();
		close();
	}

}
