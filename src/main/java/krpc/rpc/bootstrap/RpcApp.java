package krpc.rpc.bootstrap;

import java.util.ArrayList;
import java.util.HashMap;

import krpc.common.InitClose;
import krpc.common.InitCloseUtils;
import krpc.rpc.core.ErrorMsgConverter;
import krpc.rpc.core.FlowControl;
import krpc.rpc.core.MockService;
import krpc.rpc.core.ProxyGenerator;
import krpc.rpc.core.RegistryManager;
import krpc.rpc.core.RpcCodec;
import krpc.rpc.core.ServiceMetas;
import krpc.rpc.core.StartStop;
import krpc.rpc.impl.RpcClient;
import krpc.rpc.impl.RpcServer;
import krpc.rpc.web.WebMonitorService;
import krpc.rpc.web.impl.WebServer;
import krpc.trace.TraceAdapter;

public class RpcApp implements InitClose,StartStop {

	String name;
	String instanceId;
	
	ServiceMetas serviceMetas;
	RpcCodec codec;
	ProxyGenerator proxyGenerator;
	FlowControl flowControl;
	ErrorMsgConverter errorMsgConverter;
	MockService mockService;
	WebMonitorService monitorService;
	RegistryManager registryManager;
	TraceAdapter traceAdapter;
	
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
		resources.add(traceAdapter);

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
