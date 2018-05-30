package krpc.rpc.bootstrap;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;
import com.google.protobuf.UnknownFieldSet.Field;

import krpc.KrpcExt;
import krpc.rpc.cluster.DefaultClusterManager;
import krpc.rpc.cluster.LoadBalance;
import krpc.rpc.core.ClusterManager;
import krpc.rpc.core.DataManager;
import krpc.rpc.core.DataManagerCallback;
import krpc.rpc.core.ErrorMsgConverter;
import krpc.rpc.core.ExecutorManager;
import krpc.rpc.core.FlowControl;
import krpc.rpc.core.MockService;
import krpc.rpc.core.Plugin;
import krpc.rpc.core.ProxyGenerator;
import krpc.rpc.core.ReflectionUtils;
import krpc.rpc.core.Registry;
import krpc.rpc.core.RegistryManager;
import krpc.rpc.core.RpcCodec;
import krpc.rpc.core.RpcFutureFactory;
import krpc.rpc.core.ServiceMetas;
import krpc.rpc.core.TransportChannel;
import krpc.rpc.core.proto.RpcMetas;
import krpc.rpc.impl.DefaultDataManager;
import krpc.rpc.impl.DefaultExecutorManager;
import krpc.rpc.impl.DefaultMockService;
import krpc.rpc.impl.DefaultProxyGenerator;
import krpc.rpc.impl.DefaultRpcFutureFactory;
import krpc.rpc.impl.DefaultServiceMetas;
import krpc.rpc.impl.RpcCallableBase;
import krpc.rpc.impl.RpcClient;
import krpc.rpc.impl.RpcServer;
import krpc.rpc.impl.transport.DefaultRpcCodec;
import krpc.rpc.impl.transport.NettyClient;
import krpc.rpc.impl.transport.NettyServer;
import krpc.rpc.monitor.DefaultMonitorService;
import krpc.rpc.monitor.LogFormatter;
import krpc.rpc.registry.DefaultRegistryManager;
import krpc.rpc.web.JsonConverter;
import krpc.rpc.web.Route;
import krpc.rpc.web.RouteService;
import krpc.rpc.web.RpcDataConverter;
import krpc.rpc.web.SessionService;
import krpc.rpc.web.WebDir;
import krpc.rpc.web.WebMonitorService;
import krpc.rpc.web.WebPlugin;
import krpc.rpc.web.WebUrl;
import krpc.rpc.web.impl.DefaultRouteService;
import krpc.rpc.web.impl.DefaultRpcDataConverter;
import krpc.rpc.web.impl.NettyHttpServer;
import krpc.rpc.web.impl.WebServer;
import krpc.trace.CatTraceAdapter;
import krpc.trace.DefaultTraceAdapter;
import krpc.trace.SkyWalkingTraceAdapter;
import krpc.trace.Trace;
import krpc.trace.TraceAdapter;
import krpc.trace.ZipkinTraceAdapter;

public class Bootstrap {

	static Logger log = LoggerFactory.getLogger(Bootstrap.class);

	private ApplicationConfig appConfig = new ApplicationConfig();
	private MonitorConfig monitorConfig = new MonitorConfig();

	private List<RegistryConfig> registryList = new ArrayList<RegistryConfig>();
	private List<ServerConfig> serverList = new ArrayList<ServerConfig>();
	private List<ClientConfig> clientList = new ArrayList<ClientConfig>();
	private List<ServiceConfig> serviceList = new ArrayList<ServiceConfig>();
	private List<RefererConfig> refererList = new ArrayList<RefererConfig>();
	private List<WebServerConfig> webServerList = new ArrayList<WebServerConfig>();

	private HashMap<String, RegistryConfig> registries = new HashMap<>();
	private HashMap<String, ServerConfig> servers = new HashMap<>();
	private HashMap<String, ClientConfig> clients = new HashMap<>();
	private HashMap<String, ServiceConfig> services = new HashMap<>();
	private HashMap<String, RefererConfig> referers = new HashMap<>();
	private HashMap<String, WebServerConfig> webServers = new HashMap<>();

	HashMap<String, LoadBalance> loadBalanceTypes = new HashMap<>(); // not sington
	HashMap<String, Registry> registryTypes = new HashMap<>();
	HashMap<String, FlowControl> flowControlTypes = new HashMap<>();
	HashMap<String, ErrorMsgConverter> errorMsgConverterTypes = new HashMap<>();
	HashMap<String, LogFormatter> logFormatterTypes = new HashMap<>();
	HashMap<String, JsonConverter> jsonConverterTypes = new HashMap<>();
	HashMap<String, SessionService> sessionServiceTypes = new HashMap<>();
	HashMap<String, WebPlugin> webPluginTypes = new HashMap<>();

	public Bootstrap() {
	}

	public Bootstrap(String name) {
		appConfig.name = name;
	}

	public RpcApp newRpcApp() {
		return new RpcApp();
	}

	public ServiceMetas newServiceMetas() {
		return new DefaultServiceMetas();
	}

	public RpcCodec newRpcCodec(ServiceMetas serviceMetas) {
		DefaultRpcCodec o = new DefaultRpcCodec(serviceMetas);
		return o;
	}

	public ProxyGenerator newProxyGenerator() {
		return new DefaultProxyGenerator();
	}

	public MockService newMockService(String file) {
		return new DefaultMockService(file);
	}

	public ExecutorManager newExecutorManager() {
		return new DefaultExecutorManager();
	}

	public DataManager newDataManager(DataManagerCallback callback) {
		return new DefaultDataManager(callback);
	}

	public RpcServer newRpcServer() {
		return new RpcServer();
	}

	public RpcClient newRpcClient() {
		return new RpcClient();
	}

	public NettyServer newNettyServer() {
		return new NettyServer();
	}

	public NettyClient newNettyClient() {
		return new NettyClient();
	}

	public RpcFutureFactory newRpcFutureFactory(ServiceMetas metas, int notifyThreads, int notifyMaxThreads,
			int notifyQueueSize) {
		DefaultRpcFutureFactory ff = new DefaultRpcFutureFactory();
		ff.setNotifyThreads(notifyThreads);
		ff.setNotifyMaxThreads(notifyMaxThreads);
		ff.setNotifyQueueSize(notifyQueueSize);
		return ff;
	}

	public RegistryManager newRegistryManager() {
		return new DefaultRegistryManager();
	}

	public ClusterManager newClusterManager(TransportChannel transportChannel, ClientConfig c) {
		DefaultClusterManager v = new DefaultClusterManager(transportChannel);
		v.setConnections(c.connections);
		return v;
	}

	public WebMonitorService newMonitorService(RpcCodec codec, ServiceMetas serviceMetas, MonitorConfig c) {
		DefaultMonitorService m = new DefaultMonitorService(codec, serviceMetas);
		m.setAppName(appConfig.name);

		m.setLogThreads(c.logThreads);
		m.setLogQueueSize(c.logQueueSize);

		LogFormatter lf = getLogFormatterObj(monitorConfig.logFormatter);
		String params = parseParams(monitorConfig.logFormatter);
		params += "maskFields="+(c.maskFields==null?"":c.maskFields)+ ";maxRepeatedSizeToLog="+c.maxRepeatedSizeToLog+";printDefault="+c.printDefault;
		lf.config(params);

		m.setLogFormatter(lf);
		if (!isEmpty(c.serverAddr)) {
			m.setServerAddr(c.serverAddr);
		}

		return m;
	}

	public RouteService newRouteService(WebServerConfig c) {
		DefaultRouteService rs = new DefaultRouteService();

		if (!isEmpty(c.routesFile)) {
			loadMapping(rs, c.routesFile);
		}

		return rs;
	}

	public RpcDataConverter newRpcDataConverter(ServiceMetas serviceMetas) {
		return new DefaultRpcDataConverter(serviceMetas);
	}

	public NettyHttpServer newNettyHttpServer() {
		return new NettyHttpServer();
	}

	public WebServer newWebServer() {
		return new WebServer();
	}

	public RpcApp build() {
		prepare();
		return doBuild();
	}

	void loadSpi() {
		loadSpi(LoadBalance.class, loadBalanceTypes, "LoadBalance");
		loadSpi(FlowControl.class, flowControlTypes, "FlowControl");
		loadSpi(Registry.class, registryTypes, "Registry");
		loadSpi(ErrorMsgConverter.class, errorMsgConverterTypes, "ErrorMsgConverter");
		loadSpi(ErrorMsgConverter.class, errorMsgConverterTypes, "ErrorMsgConverter");
		loadSpi(LogFormatter.class, logFormatterTypes, "LogFormatter");
		loadSpi(SessionService.class, sessionServiceTypes, "SessionService");
		loadSpi(JsonConverter.class, jsonConverterTypes, "JsonConverter");
		loadSpi(WebPlugin.class, webPluginTypes, "WebPlugin");
	}

	public TraceAdapter newTraceAdapter() {
		Trace.setAppName(appConfig.name);
		
		String type = parseType(appConfig.traceAdapter);
		String params = parseParams(appConfig.traceAdapter);
		Map<String,String> paramsMap = Plugin.defaultSplitParams(params);
		
		switch( type.toLowerCase() ) {
			case "zipkin":
				return new ZipkinTraceAdapter(paramsMap);
			case "skywalking":
				return new SkyWalkingTraceAdapter(paramsMap);
			case "cat":
				return new CatTraceAdapter(paramsMap);
			case "default":
				return new DefaultTraceAdapter(paramsMap);
			default:
				throw new RuntimeException("not supported trace adapter");
		}
	}
	
	private void prepare() {

		loadSpi();

		if (isEmpty(appConfig.name))
			throw new RuntimeException("app name must be specified");

		if (isEmpty(appConfig.errorMsgConverter))
			appConfig.errorMsgConverter = "file";

		if (!isEmpty(appConfig.flowControl) && getFlowControlObj(appConfig.flowControl) == null) {
			throw new RuntimeException("flow control not registered");
		}

		if (isEmpty(monitorConfig.logFormatter))
			monitorConfig.logFormatter = "simple";
		if (getLogFormatterObj(monitorConfig.logFormatter) == null) {
			throw new RuntimeException("log formatter not registered");
		}

		if (serviceList.size() == 0 && refererList.size() == 0 && webServerList.size() == 0)
			throw new RuntimeException("service or referer or webserver must be specified");

		ServerConfig lastServer = null;
		// WebServerConfig lastWebServer = null;
		ClientConfig lastClient = null;

		for (RegistryConfig c : registryList) {
			if (getRegistryObj(c.type) == null)
				throw new RuntimeException(String.format("unknown registry type %s", c.type));
			if (isEmpty(c.id))
				c.id = c.type;
			if (c.id.equals("direct"))
				throw new RuntimeException("registry id 'direct' cannot be used");
			if (isEmpty(c.addrs))
				throw new RuntimeException("registry addrs must be specified");
			if (registries.containsKey(c.id))
				throw new RuntimeException(String.format("registry id %s duplicated", c.id));
			registries.put(c.id, c);
		}

		for (ServerConfig c : serverList) {
			if (isEmpty(c.id))
				c.id = "default";
			if (servers.containsKey(c.id))
				throw new RuntimeException(String.format("server id %s duplicated", c.id));
			servers.put(c.id, c);
			lastServer = c;
		}

		for (WebServerConfig c : webServerList) {
			if (isEmpty(c.id))
				c.id = "default";
			if (webServers.containsKey(c.id))
				throw new RuntimeException(String.format("web server id %s duplicated", c.id));

			if (getSessionServiceObj(c.sessionService) == null)
				throw new RuntimeException(String.format("unknown session service %s", c.sessionService));

			if (!isEmpty(c.jsonConverter) && getJsonConverterObj(c.jsonConverter) == null) {
				throw new RuntimeException("json converter not registered");
			}

			webServers.put(c.id, c);
			// lastWebServer = c;
		}

		for (ClientConfig c : clientList) {
			if (isEmpty(c.id))
				c.id = "default";
			if (clients.containsKey(c.id))
				throw new RuntimeException(String.format("client id %s duplicated", c.id));

			if (isEmpty(c.loadBalance))
				throw new RuntimeException(String.format("default loadbalance policy must be specified"));
			if (!checkLoadBalanceType(c.loadBalance))
				throw new RuntimeException(String.format("client id %s loadbalance not specified", c.id));

			clients.put(c.id, c);
			lastClient = c;
		}

		for (ServiceConfig c : serviceList) {
			if (isEmpty(c.interfaceName))
				throw new RuntimeException("service interface must specified");
			if (ReflectionUtils.getClass(c.interfaceName) == null)
				throw new RuntimeException(String.format("service interface %s must be specified", c.interfaceName));
			if (isEmpty(c.id))
				c.id = c.interfaceName;
			if (services.containsKey(c.id))
				throw new RuntimeException(String.format("service id %s duplicated", c.id));
			if (c.getImpl() == null)
				throw new RuntimeException(String.format("service interface %s must be implemented", c.interfaceName));
			ReflectionUtils.checkInterface(c.interfaceName, c.getImpl());
			if (isEmpty(c.transport)) {
				c.transport = "default";
			}
			if (c.isReverse()) {

				if (!clients.containsKey(c.transport)) {
					if (c.transport.equals("default") && clients.size() == 0) {
						clients.put("default", new ClientConfig("default"));
					} else if (c.transport.equals("default") && clients.size() == 1) {
						c.transport = lastClient.id;
					} else {
						throw new RuntimeException(String.format("service transport %s not found", c.transport));
					}
				}
			} else {

				if (!servers.containsKey(c.transport)) {

					if (!webServers.containsKey(c.transport)) { // don't create
																// tcp server if
																// binds to http
						if (c.transport.equals("default") && servers.size() == 1) {
							c.transport = lastServer.id;
						} else if (c.transport.equals("default") && servers.size() == 0) {
							servers.put("default", new ServerConfig("default"));
						} else {
							throw new RuntimeException(String.format("service transport %s not found", c.transport));
						}
					}

				}
			}

			if (!isEmpty(c.registryNames)) {
				String[] ss = c.registryNames.split(",");
				for (String s : ss) {
					if (!registries.containsKey(s)) {
						throw new RuntimeException(String.format("service registry %s not found", s));
					}
				}
			}
			if (isEmpty(c.group)) {
				c.group = "*";
			}

			for (MethodConfig mc : c.getMethods()) {
				if (isEmpty(mc.pattern))
					throw new RuntimeException(String.format("method pattern not specified"));
			}

			services.put(c.id, c);
		}

		for (RefererConfig c : refererList) {
			
			if (isEmpty(c.interfaceName) && c.serviceId <= 0 ) {
				throw new RuntimeException("referer interface and serviceId must be specified");
			}
			
			if (!isEmpty(c.interfaceName) && c.serviceId > 0 ) {
				throw new RuntimeException("referer interface and serviceId cannot be specified at the same time");
			}
			
			if( c.serviceId <= 0 ) { // static interface
				if (isEmpty(c.interfaceName))
					throw new RuntimeException("referer interface must specified");
				if (ReflectionUtils.getClass(c.interfaceName) == null)
					throw new RuntimeException(String.format("referer interface %s must be specified", c.interfaceName));
				if (isEmpty(c.id))
					c.id = c.interfaceName;
			} else { // dynamic message
				if (isEmpty(c.id))
					c.id = "referer"+c.serviceId;
				if( c.isReverse() )
					throw new RuntimeException("isReverse is not allowed in referer specified by serviceId");
			}
			if (referers.containsKey(c.id))
				throw new RuntimeException(String.format("referer id %s duplicated", c.id));

			if (isEmpty(c.transport)) {
				c.transport = "default";
			}

			if (!isEmpty(c.zip) && getZip(c.zip) == -1) {
				throw new RuntimeException("zip method not correct");
			}

			if (c.isReverse()) {
				if (!servers.containsKey(c.transport)) {
					if (c.transport.equals("default") && servers.size() == 0) {
						servers.put("default", new ServerConfig("default"));
					} else if (c.transport.equals("default") && servers.size() == 1) {
						c.transport = lastServer.id;
					} else {
						throw new RuntimeException(String.format("referer transport %s not found", c.transport));
					}
				}
			} else {
				if (!clients.containsKey(c.transport)) {
					if (c.transport.equals("default") && clients.size() == 0) {
						clients.put("default", new ClientConfig("default"));
					} else if (c.transport.equals("default") && clients.size() == 1) {
						c.transport = lastClient.id;
					} else {
						throw new RuntimeException(String.format("referer transport %s not found", c.transport));
					}
				}

				if (isEmpty(c.loadBalance)) {
					ClientConfig cc = clients.get(c.transport);
					c.loadBalance = cc.loadBalance;
				}

				if (!checkLoadBalanceType(c.loadBalance))
					throw new RuntimeException(String.format("client id %s loadbalance not correct", c.id));

			}

			if (!c.isReverse()) {
				if (isEmpty(c.registryName) && isEmpty(c.direct)) {
					c.direct = "127.0.0.1:5600";
				}
				if (!isEmpty(c.registryName) && !isEmpty(c.direct)) {
					throw new RuntimeException(
							String.format("referer registry and direct cannot be specified at the same time", c.id));
				}

				if (!isEmpty(c.registryName)) {
					if (!registries.containsKey(c.registryName))
						throw new RuntimeException(String.format("service registry %s not found", c.registryName));
				}

				if (isEmpty(c.group)) {
					c.group = "*";
				}
			}

			if (getRetryLevel(c.retryLevel) < 0)
				throw new RuntimeException(String.format("referer retry level %s not valid", c.retryLevel));

			for (MethodConfig mc : c.getMethods()) {
				if (isEmpty(mc.pattern))
					throw new RuntimeException(String.format("method pattern not specified"));
				if (getRetryLevel(mc.retryLevel) < 0)
					throw new RuntimeException(String.format("referer retry level %s not valid", c.retryLevel));
			}

			referers.put(c.id, c);
		}

	}

	private RpcApp doBuild() {

		RpcApp app = newRpcApp();
		app.name = appConfig.name;
		
		TraceAdapter traceAdapter = newTraceAdapter();
		Trace.setAdapter(traceAdapter);
		app.traceAdapter = traceAdapter;
				
		app.serviceMetas = newServiceMetas();
		app.codec = newRpcCodec(app.serviceMetas);
		app.proxyGenerator = newProxyGenerator();
		app.registryManager = newRegistryManager();
		app.monitorService = newMonitorService(app.codec, app.serviceMetas, monitorConfig);

		if (!isEmpty(appConfig.flowControl)) {
			FlowControl fc = getFlowControlObj(appConfig.flowControl);
			fc.config(parseParams(appConfig.flowControl));
			app.flowControl = fc;
		}

		if (!isEmpty(appConfig.errorMsgConverter)) {
			ErrorMsgConverter emc = getErrorMsgConverterObj(appConfig.errorMsgConverter);
			if (emc == null)
				throw new RuntimeException("unknown errorMsgConverter type, type=" + appConfig.errorMsgConverter);
			emc.config(parseParams(appConfig.errorMsgConverter));
			app.errorMsgConverter = emc;
		}
		if (!isEmpty(appConfig.mockFile)) {
			app.mockService = newMockService(appConfig.mockFile);
		}

		int processors = Runtime.getRuntime().availableProcessors();

		for (String name : registries.keySet()) {
			RegistryConfig c = registries.get(name);
			Registry impl = getRegistryObj(c.type);
			String params = parseParams(c.type);
			params += "appName="+appConfig.name+";addrs="+c.addrs;
			impl.config(params);
			app.registryManager.addRegistry(c.id, impl);
		}

		for (String name : servers.keySet()) {
			ServerConfig c = servers.get(name);
			RpcServer server = newRpcServer();
			server.setServiceMetas(app.serviceMetas);
			server.setMockService(app.mockService);
			server.setFlowControl(app.flowControl);
			server.setErrorMsgConverter(app.errorMsgConverter);
			server.setMonitorService(app.monitorService);

			NettyServer ns = newNettyServer();
			ns.setPort(c.port);
			ns.setCallback(server);
			ns.setCodec(app.codec);
			ns.setServiceMetas(app.serviceMetas);
			ns.setHost(c.host);
			ns.setBacklog(c.backlog);
			ns.setIdleSeconds(c.idleSeconds);
			ns.setMaxPackageSize(c.maxPackageSize);
			ns.setMaxConns(c.maxConns);
			ns.setWorkerThreads(c.ioThreads);
			ns.setMonitorService(app.monitorService);
			server.setTransport(ns);

			ExecutorManager em = newExecutorManager();
			if (c.threads >= 0) {
				if (c.threads == 0)
					c.threads = processors;
				em.addDefaultPool(c.threads, c.maxThreads, c.queueSize);
			}
			server.setExecutorManager(em);

			boolean hasReverseReferer = hasReverseReferer(name);
			if (hasReverseReferer) {
				DataManager di = newDataManager(server);
				server.setDataManager(di);

				if (c.notifyThreads == 0)
					c.notifyThreads = processors;
				RpcFutureFactory ff = newRpcFutureFactory(app.serviceMetas, c.notifyThreads, c.notifyMaxThreads,
						c.notifyQueueSize);
				server.setFutureFactory(ff);
			}

			app.servers.put(name, server);
		}

		for (String name : clients.keySet()) {
			ClientConfig c = clients.get(name);

			RpcClient client = newRpcClient();
			client.setServiceMetas(app.serviceMetas);
			client.setMockService(app.mockService);

			NettyClient nc = newNettyClient();
			nc.setCallback(client);
			nc.setCodec(app.codec);
			nc.setServiceMetas(app.serviceMetas);
			nc.setPingSeconds(c.pingSeconds);
			nc.setMaxPackageSize(c.maxPackageSize);
			nc.setConnectTimeout(c.connectTimeout);
			nc.setReconnectSeconds(c.reconnectSeconds);
			nc.setWorkerThreads(c.ioThreads);
			nc.setMonitorService(app.monitorService);
			client.setTransport(nc);

			DataManager di = newDataManager(client);
			client.setDataManager(di);

			if (c.notifyThreads == 0)
				c.notifyThreads = processors;
			RpcFutureFactory ff = newRpcFutureFactory(app.serviceMetas, c.notifyThreads, c.notifyMaxThreads,
					c.notifyQueueSize);
			client.setFutureFactory(ff);

			ClusterManager cmi = newClusterManager(nc, c);
			client.setClusterManager(cmi);

			if (hasReverseService(name)) {
				ExecutorManager em = newExecutorManager();
				if (c.threads >= 0) {
					if (c.threads >= 0)
						c.threads = processors;
					em.addDefaultPool(c.threads, c.maxThreads, c.queueSize);
				}
				client.setExecutorManager(em);

				client.setFlowControl(app.flowControl);
				client.setErrorMsgConverter(app.errorMsgConverter);
			}

			client.setMonitorService(app.monitorService);

			app.clients.put(name, client);
		}

		for (String name : webServers.keySet()) {
			WebServerConfig c = webServers.get(name);

			SessionService ss = getSessionServiceObj(c.sessionService);
			ss.config(parseParams(c.sessionService));
			JsonConverter jc = getJsonConverterObj(c.jsonConverter);
			jc.config(parseParams(c.jsonConverter));
			
			WebServer server = newWebServer();
			server.setSampleRate(c.sampleRate);
			server.setServiceMetas(app.serviceMetas);
			server.setFlowControl(app.flowControl);
			server.setErrorMsgConverter(app.errorMsgConverter);
			server.setMonitorService(app.monitorService);
			server.setRouteService(newRouteService(c));
			server.setRpcDataConverter(newRpcDataConverter(app.serviceMetas));
			server.setSessionService(ss);
			server.setJsonConverter(jc);
			server.setSessionIdCookieName(c.sessionIdCookieName);
			server.setSessionIdCookiePath(c.sessionIdCookiePath);

			NettyHttpServer ns = newNettyHttpServer();
			ns.setPort(c.port);
			ns.setCallback(server);
			ns.setHost(c.host);
			ns.setBacklog(c.backlog);
			ns.setIdleSeconds(c.idleSeconds);
			ns.setMaxContentLength(c.maxContentLength);
			ns.setMaxConns(c.maxConns);
			ns.setWorkerThreads(c.ioThreads);
			server.setHttpTransport(ns);

			ExecutorManager em = newExecutorManager();
			if (c.threads >= 0) {
				if (c.threads == 0)
					c.threads = processors;
				em.addDefaultPool(c.threads, c.maxThreads, c.queueSize);
			}
			server.setExecutorManager(em);

			app.webServers.put(name, server);
			
			loadProtos(app,c.protoDir);
		}

		for (String name : services.keySet()) {
			ServiceConfig c = services.get(name);

			Class<?> cls = ReflectionUtils.getClass(c.interfaceName);
			int serviceId = ReflectionUtils.getServiceId(cls);

			ExecutorManager em = null;
			RpcCallableBase callable = null;
			boolean bindToHttp = false;
			if (c.reverse) {
				callable = app.clients.get(c.transport);
				callable.addAllowedService(serviceId);
				em = callable.getExecutorManager();
			} else {
				callable = app.servers.get(c.transport);
				if (callable != null) {
					callable.addAllowedService(serviceId);
					em = callable.getExecutorManager();
				} else {
					WebServer webServer = app.webServers.get(c.transport);
					em = webServer.getExecutorManager();
					bindToHttp = true;
				}
			}
			app.serviceMetas.addService(cls, c.impl, callable);

			if (!isEmpty(c.registryNames) && !bindToHttp) { // todo
				String[] ss = c.registryNames.split(",");
				for (String s : ss)
					app.registryManager.register(serviceId, s, c.group); // todo  port 
			}

			if (c.threads >= 0) {
				if (c.threads == 0)
					c.threads = processors;
				em.addPool(serviceId, c.threads, c.maxThreads, c.queueSize);
			}

			if (app.flowControl != null && !isEmpty(c.flowControl)) {
				HashMap<Integer, Integer> fcParams = parseFlowControlParams(c.flowControl);
				for (Map.Entry<Integer, Integer> entry : fcParams.entrySet()) {
					app.flowControl.addLimit(serviceId, entry.getKey(), entry.getValue());
				}
			}

			for (MethodConfig mc : c.getMethods()) {
				int[] msgIds = patternToMsgIds(cls, mc.pattern);
				if (msgIds == null || msgIds.length == 0)
					throw new RuntimeException(String.format("no msgId match method pattern " + mc.pattern));
				if (mc.threads >= 0) {
					if (mc.threads == 0)
						mc.threads = processors;
					em.addPool(serviceId, msgIds, mc.threads, mc.maxThreads, mc.queueSize);
				}

				if (app.flowControl != null && !isEmpty(mc.flowControl)) {
					HashMap<Integer, Integer> fcParams = parseFlowControlParams(mc.flowControl);
					for (Map.Entry<Integer, Integer> entry : fcParams.entrySet()) {
						for (int msgId : msgIds)
							app.flowControl.addLimit(serviceId, msgId, entry.getKey(), entry.getValue());
					}
				}
			}

			app.services.put(name, c.impl);
		}

		for (String name : referers.keySet()) {
			RefererConfig c = referers.get(name);

			int serviceId = 0;
			Class<?> cls = null;
			
			if( c.serviceId <= 0 ) {
				cls = ReflectionUtils.getClass(c.interfaceName);
				serviceId = ReflectionUtils.getServiceId(cls);
			} else {
				serviceId = c.serviceId;
			}

			RpcCallableBase callable;
			if (c.reverse) {
				callable = app.servers.get(c.transport);
			} else {
				callable = app.clients.get(c.transport);
			}
			callable.addAllowedReferer(serviceId);

			app.codec.configZip(serviceId, getZip(c.zip), c.minSizeToZip);

			if( cls != null ) {
				Object impl = null;
				if (c.interfaceName.endsWith("Async")) {
					impl = app.proxyGenerator.generateAsyncReferer(cls, callable);
					app.serviceMetas.addAsyncReferer(cls, impl, callable);
				} else {
					impl = app.proxyGenerator.generateReferer(cls, callable);
					app.serviceMetas.addReferer(cls, impl, callable);
				}
				app.referers.put(name, impl);
			} else {
				app.serviceMetas.addDynamic(serviceId,callable);
			}
			
			if (callable instanceof RpcClient) {
				RpcClient client = (RpcClient) callable;
				client.addRetryPolicy(serviceId, -1, c.timeout, getRetryLevel(c.retryLevel), c.retryCount);

				DefaultClusterManager cmi = (DefaultClusterManager) client.getClusterManager();

				if (!isEmpty(c.loadBalance)) {
					LoadBalance policy = newLoadBalanceObj(c.loadBalance);
					policy.config(parseParams(c.loadBalance));
					cmi.addLbPolicy(serviceId, policy);
				}

				if (!isEmpty(c.registryName)) {
					app.registryManager.addDiscover(serviceId, c.registryName, c.group, cmi);
				} else {
					app.registryManager.addDirect(serviceId, c.direct, cmi);
				}

				if( cls != null ) {
					for (MethodConfig mc : c.getMethods()) {
						int[] msgIds = patternToMsgIds(cls, mc.pattern);
						if (msgIds == null || msgIds.length == 0)
							throw new RuntimeException(String.format("no msgId match method pattern " + mc.pattern));
	
						for (int msgId : msgIds) {
							client.addRetryPolicy(serviceId, msgId, mc.timeout, getRetryLevel(mc.retryLevel),
									mc.retryCount);
						}
					}
				} else {
					for (MethodConfig mc : c.getMethods()) {
						throw new RuntimeException(String.format("methods cannot be specified ")); // todo
					}
				}
				
				// todo serviceId not allowed to duplicated
			}

		}

		return app;
	}

	void loadProtos(RpcApp app,String proto) {
		
		if( isEmpty(proto) ) {
			log.info("no dynamic proto resource need to load");
			return;
		}
		
		if( !proto.endsWith("/") ) proto = proto + "/";
				
		try {
			InputStream basein = RpcMetas.class.getResourceAsStream("descriptor.proto.pb");
			FileDescriptorSet baseSet = FileDescriptorSet.parseFrom(basein);  
			basein.close();
			FileDescriptor base = FileDescriptor.buildFrom(baseSet.getFile(0),new FileDescriptor[] {});  
			
			List<String> files = getProtoFiles(proto);
			if( files != null ) {
				for(String file:files) {
					loadProtoFile(app,base,proto + file);  
				}
			}
		 } catch(Exception e) {
			 log.error("load dynamic proto resource failed",e);
		 }		
	}

	private void loadProtoFile(RpcApp app,FileDescriptor base, String file) throws IOException, DescriptorValidationException {
		
		InputStream in = getResource(file);
		FileDescriptorSet descriptorSet = FileDescriptorSet.parseFrom(in);  
		in.close();
		
		Map<String,Descriptor> descriptors = new HashMap<>();
		for (FileDescriptorProto fdp : descriptorSet.getFileList()) {  
		    FileDescriptor fd = FileDescriptor.buildFrom(fdp,new FileDescriptor[] {base});  
  
		    for (Descriptor descriptor : fd.getMessageTypes()) {  
		        String className = descriptor.getName();  
		        descriptors.put(className, descriptor);  
		    }  
		    for (ServiceDescriptor svr : fd.getServices()) {  
		        Field f= svr.getOptions().getUnknownFields().getField(KrpcExt.SERVICEID_FIELD_NUMBER);
		        String serviceName = svr.getName();  
		        int serviceId = f.getVarintList().get(0).intValue();
		        for (MethodDescriptor m : svr.getMethods()) {
			        String msgName = m.getName();  
			        Field f2 = m.getOptions().getUnknownFields().getField(KrpcExt.MSGID_FIELD_NUMBER);
			        int msgId = f2.getVarintList().get(0).intValue();
			        log.info(String.format("dynamic proto resource loaded, serviceId=%d,msgId=%d,serviceName=%s,msgName=%s",serviceId,msgId,serviceName,msgName));
			        Descriptor reqDesc = m.getInputType();
			        Descriptor resDesc = m.getOutputType();
			        app.serviceMetas.addDynamic(serviceId,msgId,reqDesc,resDesc,serviceName,msgName);
		        }
		    }  		    
		}
	}
	
	List<String> getProtoFiles(String proto) {
		List<String> l = new ArrayList<>();
	
        URL classResourceURL = this.getClass().getClassLoader().getResource(proto);  
        if( classResourceURL == null ) {
        	log.info("no dynamic proto resource loaded from "+proto);
        	return null;
        }

        String classResourcePath = classResourceURL.getPath(); 
        if (classResourceURL.getProtocol().equals("file")) { 

            String classesDirPath = classResourcePath.substring(classResourcePath.indexOf("/"));
            File classesDir = new File(classesDirPath); 
            File[] files = classesDir.listFiles();
            if( files != null ) {
                for (File file : files) { 
                    String resourceName = file.getName();  
                    if (!file.isDirectory() && resourceName.endsWith(".proto.pb")) {  
                        l.add(resourceName);  
                    } 
                } 
            }
            
        } else if (classResourceURL.getProtocol().equals("jar")) {
 
            String jarPath = classResourcePath.substring(classResourcePath.indexOf("/"), classResourceURL.getPath().indexOf("!")); 
            try { 
                JarFile jarFile = new JarFile(URLDecoder.decode(jarPath, "UTF-8"));  
                Enumeration<JarEntry> jarEntries = jarFile.entries(); 
                while (jarEntries.hasMoreElements()) { 
                    JarEntry jarEntry = (JarEntry)jarEntries.nextElement();  
                    String resourceName = jarEntry.getName();  
                    if (resourceName.endsWith(".proto.pb") && !jarEntry.isDirectory()) {  
                        l.add(resourceName.substring(proto.length()));  
                    } 
                } 
                jarFile.close();
            } catch (Exception e) {  
                throw new RuntimeException("load dynamic proto resource failed",e);
            } 
        } 

		return l;
	}
	
	boolean hasReverseReferer(String serverName) {
		for (RefererConfig c : refererList) {
			if (c.reverse && c.transport.equals(serverName))
				return true;
		}
		return false;
	}

	boolean hasReverseService(String clientName) {
		for (ServiceConfig c : serviceList) {
			if (c.reverse && c.transport.equals(clientName))
				return true;
		}
		return false;
	}

	int[] patternToMsgIds(Class<?> intf, String pattern) {
		char ch = pattern.charAt(0);
		boolean byMsgId = false;
		if (ch >= '0' && ch <= '9') {
			byMsgId = true;
		}
		if (byMsgId) {
			return splitMsgIdPattern(intf, pattern);
		} else {
			return matchMsgNamePattern(intf, pattern);
		}
	}

	int[] splitMsgIdPattern(Class<?> intf, String pattern) {
		ArrayList<Integer> list = new ArrayList<Integer>();
		String[] ss = pattern.split(",");
		try {
			for (String s : ss) {
				int p = s.indexOf("-");
				if (p >= 0) {
					int min = Integer.parseInt(s.substring(0, p));
					int max = Integer.parseInt(s.substring(p + 1));
					for (int i = min; i <= max; ++i)
						list.add(i);
				} else {
					int v = Integer.parseInt(s);
					list.add(v);
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(String.format("msgId pattern %s not valid", pattern));
		}

		if (list.size() == 0)
			throw new RuntimeException(String.format("msgId not found, pattern=" + pattern));

		HashMap<Integer, String> msgIdMap = ReflectionUtils.getMsgIds(intf);
		for (int i : list) {
			if (!msgIdMap.containsKey(i))
				throw new RuntimeException(String.format("msgId %d not found", i));
		}
		int[] vs = new int[list.size()];
		for (int i = 0; i < vs.length; ++i)
			vs[i] = list.get(i);
		return vs;
	}

	int[] matchMsgNamePattern(Class<?> intf, String pattern) {
		ArrayList<Integer> list = new ArrayList<Integer>();
		HashMap<String, Integer> msgNameMap = ReflectionUtils.getMsgNames(intf);
		for (String msgName : msgNameMap.keySet()) {
			if (msgName.matches(pattern))
				list.add(msgNameMap.get(msgName));
		}

		if (list.size() == 0)
			throw new RuntimeException(String.format("msgId not found, pattern=" + pattern));
		int[] vs = new int[list.size()];
		for (int i = 0; i < vs.length; ++i)
			vs[i] = list.get(i);
		return vs;
	}

	boolean isEmpty(String s) {
		return s == null || s.isEmpty();
	}

	int getRetryLevel(String s) {
		switch (s.toLowerCase()) {
		case "no_retry":
			return 0;
		case "send_failed":
			return 1;
		case "not_available":
			return 2;
		case "disconnect":
			return 3;
		case "timeout":
			return 4;
		default:
			return -1;
		}
	}

	int getZip(String s) {
		if (s == null)
			return 0;
		switch (s.toLowerCase()) {
		case "zlib":
			return 1;
		case "snappy":
			return 2;
		default:
			return 0;
		}
	}

	FlowControl getFlowControlObj(String type) {
		type = parseType(type);
		FlowControl fc = flowControlTypes.get(type);
		if (fc == null)
			return null;
		return fc;
	}

	JsonConverter getJsonConverterObj(String type) {
		type = parseType(type);
		JsonConverter jc = jsonConverterTypes.get(type);
		if (jc == null)
			return null;
		return jc;
	}

	SessionService getSessionServiceObj(String type) {
		type = parseType(type);
		SessionService ss = sessionServiceTypes.get(type);
		if (ss == null)
			return null;
		return ss;
	}

	LogFormatter getLogFormatterObj(String type) {
		type = parseType(type);
		LogFormatter lf = logFormatterTypes.get(type);
		if (lf == null)
			return null;
		return lf;
	}

	ErrorMsgConverter getErrorMsgConverterObj(String type) {
		type = parseType(type);
		ErrorMsgConverter emc = errorMsgConverterTypes.get(type);
		if (emc == null)
			return null;
		return emc;
	}

	WebPlugin getWebPluginObj(String type) {
		type = parseType(type);
		WebPlugin p = webPluginTypes.get(type);
		if (p == null)
			return null;
		return p;
	}

	Registry getRegistryObj(String type) {
		type = parseType(type);
		Registry reg = registryTypes.get(type);
		if (reg == null)
			return null;
		return reg;
	}

	LoadBalance newLoadBalanceObj(String type) {
		type = parseType(type);
		LoadBalance lb = loadBalanceTypes.get(type);
		if (lb == null)
			return null;
		return (LoadBalance) ReflectionUtils.newObject(lb.getClass().getName());
	}

	boolean checkLoadBalanceType(String type) {
		type = parseType(type);
		LoadBalance lb = loadBalanceTypes.get(type);
		return lb != null;
	}

	String parseType(String s) {
		s = s.toLowerCase();
		int p = s.indexOf(":");
		if (p >= 0)
			return s.substring(0, p);
		return s;
	}

	public String parseParams(String s) {
		int p = s.indexOf(":");
		if (p >= 0)
			return s.substring(p+1);
		return "";
	}

	HashMap<Integer, Integer> parseFlowControlParams(String flowControlParams) {
		HashMap<Integer, Integer> params = new HashMap<Integer, Integer>();
		Map<String,String> tmp = Plugin.defaultSplitParams(flowControlParams);
		for (String key : tmp.keySet()) {
			String value = tmp.get(key);
			try {
				params.put(Integer.parseInt(key), Integer.parseInt(value));
			} catch (Exception e) {
				throw new RuntimeException("flow control parameter not valid, param=" + flowControlParams);
			}
		}
		return params;
	}

	<T> void loadSpi(Class<T> cls, HashMap<String, T> map, String suffix) {
		suffix = suffix.toLowerCase();
		ServiceLoader<T> matcher = ServiceLoader.load(cls);
		Iterator<T> iter = matcher.iterator();
		while (iter.hasNext()) {
			try {
				T lb = iter.next();
				map.put(lb.getClass().getName().toLowerCase(), lb);
				map.put(lb.getClass().getSimpleName().toLowerCase(), lb);
				map.put(removeSuffix(lb.getClass().getSimpleName().toLowerCase(), suffix), lb);
			} catch (ServiceConfigurationError e) {
				log.error("load spi error:" + e.getMessage());
			}
		}
	}

	String removeSuffix(String s, String suffix) {
		if (s.endsWith(suffix)) {
			return s.substring(0, s.length() - suffix.length());
		}
		return s;
	}

	private void loadMapping(DefaultRouteService rs, String routesFile) {

		try {
			loadMappingFileInternal(rs, routesFile);
		} catch (Exception e) {
			throw new RuntimeException("cannot load mapping file, file=" + routesFile, e);
		}

	}

	void loadMappingFileInternal(DefaultRouteService rs, String mappingFile) throws Exception {
		DocumentBuilderFactory docbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder docb = docbf.newDocumentBuilder();
		Document doc = docb.parse(getResource(mappingFile));
		Node root = doc.getChildNodes().item(0);
		NodeList rootChildren = root.getChildNodes();
		
		for (int i = 0; i < rootChildren.getLength(); i++) {
			Node node = rootChildren.item(i);
			if (node.getNodeType() != Node.ELEMENT_NODE ) continue;
			switch( node.getNodeName() ) {
				case "plugin":
					configPlugin(node);
					break;
				case "import":
					importRoutes(rs,node);
					break;
				case "dir":
					loadDir(rs,node);
					break;
				case "url":
					loadUrl(rs,node);
					break;
				case "group":
					loadGroup(rs,node);
					break;
				default:
					break;
			}
		}

	}

	private void loadGroup(DefaultRouteService rs, Node node) {
		Map<String, String> defaultAttrs = getAttrs(node);

		String defaultHosts = defaultAttrs.getOrDefault("hosts", "");
		String t = defaultAttrs.getOrDefault("path", "");
		if (!t.isEmpty() )
			throw new RuntimeException("mapping path in group is not allowed, use prefix instead");
		String prefix = sanitizePath(defaultAttrs.getOrDefault("prefix", ""));
		if (prefix.equals("/"))
			prefix = "";

		String defaultMethods = defaultAttrs.getOrDefault("methods", "");
		String defaultServiceId = defaultAttrs.getOrDefault("serviceId", "");
		String defaultMsgId = defaultAttrs.getOrDefault("msgId", "");
		if (!isEmpty(defaultMsgId))
			throw new RuntimeException("mapping msgid in group is not allowed");
		String defaultSessionMode = defaultAttrs.getOrDefault("sessionMode", "");
		WebPlugin[] defaultPlugins = loadPlugins(defaultAttrs.getOrDefault("plugins", ""));

		defaultAttrs.remove("hosts");
		defaultAttrs.remove("prefix");
		defaultAttrs.remove("path");
		defaultAttrs.remove("methods");
		defaultAttrs.remove("serviceId");
		defaultAttrs.remove("msgId");
		defaultAttrs.remove("sessionMode");
		defaultAttrs.remove("plugins");

		NodeList children = node.getChildNodes();
		for (int j = 0; j < children.getLength(); j++) {
			if (children.item(j).getNodeType() == Node.ELEMENT_NODE
					&& children.item(j).getNodeName().equals("url")) {
				Map<String, String> attrs = getAttrs(children.item(j));

				String hosts = attrs.getOrDefault("hosts", "");
				if (isEmpty(hosts))
					hosts = defaultHosts;
				if (isEmpty(hosts))
					hosts = "*";

				String path = sanitizePath(attrs.get("path"));
				if (isEmpty(path))
					throw new RuntimeException("mapping path can not be empty");
				if (!isEmpty(prefix))
					path = prefix + path;

				String methods = attrs.getOrDefault("methods", "");
				if (isEmpty(methods))
					methods = defaultMethods;
				if (isEmpty(methods))
					methods = "get,post";
				if (!checkMethod(methods))
					throw new RuntimeException("mapping methods is not valid, methods=" + methods);

				String serviceIdStr = attrs.getOrDefault("serviceId", "");
				if (isEmpty(serviceIdStr))
					serviceIdStr = defaultServiceId;
				if (isEmpty(serviceIdStr))
					throw new RuntimeException("mapping serviceId can not be empty");

				String msgIdStr = attrs.getOrDefault("msgId", "");
				if (isEmpty(msgIdStr))
					throw new RuntimeException("mapping msgId can not be empty");

				int serviceId = Integer.parseInt(serviceIdStr);
				int msgId = Integer.parseInt(msgIdStr);
				if (serviceId <= 1 || msgId < 1)
					throw new RuntimeException("serviceId or msgId is not valid");

				String sessionModeStr = attrs.getOrDefault("sessionMode", "0");
				if (isEmpty(sessionModeStr))
					sessionModeStr = defaultSessionMode;
				if (isEmpty(sessionModeStr))
					throw new RuntimeException("mapping sessionMode can not be empty");

				int sessionMode = Integer.parseInt(sessionModeStr);
				if (sessionMode < Route.SESSION_MODE_NO || sessionMode > Route.SESSION_MODE_OPTIONAL)
					throw new RuntimeException("sessionMode is not valid");

				String plugins = attrs.get("plugins");
				WebPlugin[] pluginsList = loadPlugins(plugins);
				if (pluginsList == null)
					pluginsList = defaultPlugins;

				attrs.remove("hosts");
				attrs.remove("path");
				attrs.remove("methods");
				attrs.remove("serviceId");
				attrs.remove("msgId");
				attrs.remove("sessionMode");
				attrs.remove("plugins");
				
				Map<String, String> allAttrs = new HashMap<>();
				allAttrs.putAll(defaultAttrs);
				allAttrs.putAll(attrs);
				
				WebUrl url = new WebUrl(hosts, path);
				url.setMethods(methods).setServiceId(serviceId).setMsgId(msgId).setSessionMode(sessionMode)
						.setPlugins(pluginsList).setAttrs(allAttrs);
				rs.addUrl(url);
			}
		}
	}

	private void loadUrl(DefaultRouteService rs, Node node) {
		Map<String, String> attrs = getAttrs(node);

		String hosts = attrs.getOrDefault("hosts", "*");
		if (isEmpty(hosts))
			throw new RuntimeException("hosts can not be empty");
		String path = sanitizePath(attrs.get("path"));
		if (isEmpty(path))
			throw new RuntimeException("path can not be empty");

		String methods = attrs.getOrDefault("methods", "get,post").toLowerCase();
		if (!checkMethod(methods))
			throw new RuntimeException("mapping methods is not valid, methods=" + methods);

		String serviceIdStr = attrs.getOrDefault("serviceId", "");
		if (isEmpty(serviceIdStr))
			throw new RuntimeException("mapping serviceId can not be empty");

		String msgIdStr = attrs.getOrDefault("msgId", "");
		if (isEmpty(msgIdStr))
			throw new RuntimeException("mapping msgId can not be empty");

		int serviceId = Integer.parseInt(serviceIdStr);
		int msgId = Integer.parseInt(msgIdStr);
		if (serviceId <= 1 || msgId < 1)
			throw new RuntimeException("serviceId or msgId is not valid");

		String sessionModeStr = attrs.getOrDefault("sessionMode", "0");
		if (isEmpty(sessionModeStr))
			throw new RuntimeException("mapping sessionMode can not be empty");

		int sessionMode = Integer.parseInt(sessionModeStr);
		if (sessionMode < Route.SESSION_MODE_NO || sessionMode > Route.SESSION_MODE_OPTIONAL)
			throw new RuntimeException("sessionMode is not valid");

		WebPlugin[] pluginList = loadPlugins(attrs.get("plugins"));

		attrs.remove("hosts");
		attrs.remove("path");
		attrs.remove("methods");
		attrs.remove("serviceId");
		attrs.remove("msgId");
		attrs.remove("sessionMode");
		attrs.remove("plugins");
		
		WebUrl url = new WebUrl(hosts, path);
		url.setMethods(methods).setServiceId(serviceId).setMsgId(msgId).setPlugins(pluginList)
				.setSessionMode(sessionMode).setAttrs(attrs);
		rs.addUrl(url);
	}

	private void loadDir(DefaultRouteService rs, Node node) {
		Map<String, String> attrs = getAttrs(node);

		String hosts = attrs.getOrDefault("hosts", "*");
		if (isEmpty(hosts))
			throw new RuntimeException("hosts can not be empty");
		String path = sanitizePath(attrs.get("path"));
		if (isEmpty(path))
			throw new RuntimeException("path can not be empty");

		String baseDir = attrs.get("baseDir");
		String staticDir = attrs.get("staticDir");
		String uploadDir = attrs.get("uploadDir");
		String templateDir = attrs.get("templateDir");

		if (!isEmpty(baseDir) && !checkExist(baseDir))
			throw new RuntimeException("baseDir is not correct, baseDir=" + baseDir);
		if (!isEmpty(staticDir) && !checkExist(staticDir))
			throw new RuntimeException("staticDir is not correct, staticDir=" + staticDir);
		if (!isEmpty(uploadDir) && !checkExist(uploadDir))
			throw new RuntimeException("staticDir is not correct, uploadDir=" + uploadDir);
		if (!isEmpty(templateDir) && !checkExist(templateDir))
			throw new RuntimeException("staticDir is not correct, templateDir=" + templateDir);

		if (isEmpty(baseDir) && isEmpty(staticDir) && isEmpty(uploadDir) && isEmpty(templateDir))
			throw new RuntimeException("not a valid dir");

		WebDir dir = new WebDir(hosts, path);
		dir.setBaseDir(baseDir).setStaticDir(staticDir).setUploadDir(uploadDir).setTemplateDir(templateDir);
		rs.addDir(dir);
	}

	private void importRoutes(DefaultRouteService rs, Node node) throws Exception {
		Map<String, String> attrs = getAttrs(node);
		String file = attrs.get("file");
		if (isEmpty(file))
			throw new RuntimeException("import file must be specified");
		loadMappingFileInternal(rs, file);
	}

	private void configPlugin(Node node) {
		Map<String, String> attrs = getAttrs(node);
		String name = attrs.get("name");
		if (isEmpty(name))
			throw new RuntimeException("plugin name must be specified");

		String params = attrs.get("params");
		if (isEmpty(params))
			throw new RuntimeException("plugin params must be specified");

		WebPlugin p = getWebPluginObj(name);
		if (p == null) {
			throw new RuntimeException("web plugin not found, name=" + name);
		}
		p.config(params);
	}

	InputStream getResource(String file) {
		return getClass().getClassLoader().getResourceAsStream(file);
	}

	boolean checkExist(String dir) {
		return Files.exists(Paths.get(dir));
	}

	String sanitizePath(String path) {
		if (!path.startsWith("/"))
			path = "/" + path;
		if (path.endsWith("/") && !path.equals("/"))
			path = path.substring(0, path.length() - 1);
		return path;
	}

	boolean checkMethod(String methods) {
		if (isEmpty(methods))
			return false;
		String[] ss = methods.split(",");
		for (String s : ss) {
			switch (s.toLowerCase()) {
			case "get":
			case "post":
			case "put":
			case "delete":
			case "head":
				break;
			default:
				return false;
			}
		}
		return true;
	}

	WebPlugin[] loadPlugins(String plugins) {
		if (isEmpty(plugins))
			return null;
		String[] ss = plugins.split(",");
		WebPlugin[] array = new WebPlugin[ss.length];
		for (int i = 0; i < ss.length; ++i) {
			array[i] = getWebPluginObj(ss[i]);
			if (array[i] == null)
				throw new RuntimeException("web plugin not found, name=" + array[i]);
		}
		return array;
	}

	Map<String, String> getAttrs(Node node) {
		Map<String, String> map = new HashMap<String, String>();
		NamedNodeMap attrs = node.getAttributes();
		for (int j = 0; j < attrs.getLength(); j++) {
			Node attr = attrs.item(j);
			map.put(attr.getNodeName(), attr.getNodeValue());
		}
		return map;
	}

	public Bootstrap setName(String name) {
		appConfig.name = name;
		return this;
	}

	public Bootstrap setMonitorServerAddr(String addr) {
		monitorConfig.serverAddr = addr;
		return this;
	}

	public Bootstrap addClient(ClientConfig c) {
		clientList.add(c);
		return this;
	}

	public Bootstrap addServer(ServerConfig c) {
		serverList.add(c);
		return this;
	}

	public Bootstrap addServer(int port) {
		ServerConfig c = new ServerConfig();
		c.port = port;
		addServer(c);
		return this;
	}

	public Bootstrap addServer(String name, int port) {
		ServerConfig c = new ServerConfig();
		c.id = name;
		c.port = port;
		addServer(c);
		return this;
	}

	public Bootstrap addWebServer(WebServerConfig c) {
		webServerList.add(c);
		return this;
	}

	public Bootstrap addWebServer(int port) {
		WebServerConfig c = new WebServerConfig();
		c.port = port;
		addWebServer(c);
		return this;
	}

	public Bootstrap addWebServer(String name, int port) {
		WebServerConfig c = new WebServerConfig();
		c.id = name;
		c.port = port;
		addWebServer(c);
		return this;
	}

	public Bootstrap addRegistry(RegistryConfig c) {
		registryList.add(c);
		return this;
	}

	public Bootstrap addService(ServiceConfig c) {
		serviceList.add(c);
		return this;
	}

	public Bootstrap addService(String interfaceName, Object impl) {
		ServiceConfig c = new ServiceConfig();
		c.interfaceName = interfaceName;
		c.impl = impl;
		serviceList.add(c);
		return this;
	}

	public Bootstrap addService(Class<?> intf, Object impl) {
		ServiceConfig c = new ServiceConfig();
		c.interfaceName = intf.getName();
		c.impl = impl;
		serviceList.add(c);
		return this;
	}

	public Bootstrap addReverseService(String interfaceName, Object impl) {
		ServiceConfig c = new ServiceConfig();
		c.interfaceName = interfaceName;
		c.impl = impl;
		c.reverse = true;
		serviceList.add(c);
		return this;
	}

	public Bootstrap addReverseService(Class<?> intf, Object impl) {
		ServiceConfig c = new ServiceConfig();
		c.interfaceName = intf.getName();
		c.impl = impl;
		c.reverse = true;
		serviceList.add(c);
		return this;
	}

	public Bootstrap addReferer(RefererConfig c) {
		refererList.add(c);
		return this;
	}

	public Bootstrap addReferer(String name, String interfaceName, String direct) {
		RefererConfig c = new RefererConfig();
		c.id = name;
		c.interfaceName = interfaceName;
		c.direct = direct;
		refererList.add(c);
		return this;
	}
	
	public Bootstrap addReferer(String name, int serviceId, String direct) {
		RefererConfig c = new RefererConfig();
		c.id = name;
		c.serviceId = serviceId;
		c.direct = direct;
		refererList.add(c);
		return this;
	}
	
	public Bootstrap addReferer(String name, Class<?> intf, String direct) {
		RefererConfig c = new RefererConfig();
		c.id = name;
		c.interfaceName = intf.getName();
		c.direct = direct;
		refererList.add(c);
		return this;
	}

	public Bootstrap addReverseReferer(String id, String interfaceName) {
		RefererConfig c = new RefererConfig();
		c.id = id;
		c.interfaceName = interfaceName;
		c.reverse = true;
		refererList.add(c);
		return this;
	}

	public Bootstrap addReverseReferer(String id, Class<?> intf) {
		RefererConfig c = new RefererConfig();
		c.id = id;
		c.interfaceName = intf.getName();
		c.reverse = true;
		refererList.add(c);
		return this;
	}

	public Bootstrap setErrorMsgConverter(String errorMsgConverter) {
		appConfig.errorMsgConverter = errorMsgConverter;
		return this;
	}

	public Bootstrap setFlowControl(String flowControl) {
		appConfig.flowControl = flowControl;
		return this;
	}

	public Bootstrap setMockFile(String mockFile) {
		appConfig.mockFile = mockFile;
		return this;
	}

	public Bootstrap setAppConfig(ApplicationConfig appConfig) {
		this.appConfig = appConfig;
		return this;
	}

	public Bootstrap setTraceAdapter(String traceAdapter) {
		this.appConfig.traceAdapter = traceAdapter;
		return this;
	}

	
	public Bootstrap setMonitorConfig(MonitorConfig monitorConfig) {
		this.monitorConfig = monitorConfig;
		return this;
	}
}
