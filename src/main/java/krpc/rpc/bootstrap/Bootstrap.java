package krpc.rpc.bootstrap;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import krpc.common.Plugin;
import krpc.rpc.cluster.BreakerInfo;
import krpc.rpc.cluster.DefaultClusterManager;
import krpc.rpc.cluster.DefaultRouter;
import krpc.rpc.cluster.LoadBalance;
import krpc.rpc.cluster.Router;
import krpc.rpc.core.ClusterManager;
import krpc.rpc.core.DataManager;
import krpc.rpc.core.DataManagerCallback;
import krpc.rpc.core.DynamicRoutePlugin;
import krpc.rpc.core.DynamicRouteManager;
import krpc.rpc.core.ErrorMsgConverter;
import krpc.rpc.core.ExecutorManager;
import krpc.rpc.core.FallbackPlugin;
import krpc.rpc.core.RpcPlugin;
import krpc.rpc.core.ProxyGenerator;
import krpc.rpc.core.ReflectionUtils;
import krpc.rpc.core.Registry;
import krpc.rpc.core.RegistryManager;
import krpc.rpc.core.RpcCodec;
import krpc.rpc.core.RpcFutureFactory;
import krpc.rpc.core.ServiceMetas;
import krpc.rpc.core.ServiceMetasAware;
import krpc.rpc.core.TransportChannel;
import krpc.rpc.core.Validator;
import krpc.rpc.core.proto.RpcMetas;
import krpc.rpc.dynamicroute.DefaultDynamicRouteManager;
import krpc.rpc.impl.DefaultDataManager;
import krpc.rpc.impl.DefaultExecutorManager;
import krpc.rpc.impl.DefaultProxyGenerator;
import krpc.rpc.impl.DefaultRpcFutureFactory;
import krpc.rpc.impl.DefaultServiceMetas;
import krpc.rpc.impl.DefaultValidator;
import krpc.rpc.impl.RpcCallableBase;
import krpc.rpc.impl.RpcClient;
import krpc.rpc.impl.RpcServer;
import krpc.rpc.impl.transport.DefaultRpcCodec;
import krpc.rpc.impl.transport.NettyClient;
import krpc.rpc.impl.transport.NettyServer;
import krpc.rpc.monitor.DefaultMonitorService;
import krpc.rpc.monitor.LogFormatter;
import krpc.rpc.monitor.MonitorPlugin;
import krpc.rpc.registry.DefaultRegistryManager;
import krpc.rpc.util.IpUtils;
import krpc.rpc.web.WebRoute;
import krpc.rpc.web.WebRouteService;
import krpc.rpc.web.RpcDataConverter;
import krpc.rpc.web.SessionService;
import krpc.rpc.web.WebDir;
import krpc.rpc.web.WebMonitorService;
import krpc.rpc.web.WebPlugin;
import krpc.rpc.web.WebPlugins;
import krpc.rpc.web.WebUrl;
import krpc.rpc.web.impl.DefaultWebRouteService;
import krpc.rpc.web.impl.DefaultRpcDataConverter;
import krpc.rpc.web.impl.NettyHttpServer;
import krpc.rpc.web.impl.WebServer;
import krpc.trace.Trace;
import krpc.trace.TraceAdapter;

import krpc.trace.sniffer.Advice;
import krpc.trace.sniffer.AdviceInstance;

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
	private HashSet<String> serviceInterfaces = new HashSet<>();
	private HashMap<String, RefererConfig> referers = new HashMap<>();
	private HashSet<String> refererInterfaces = new HashSet<>();
	private HashMap<String, WebServerConfig> webServers = new HashMap<>();

	@SuppressWarnings("rawtypes")
	static public class PluginInfo {
		Class cls;
		Class impCls;
		Object bean;
		Set<String> matchNames = new HashSet<>();
		
		public PluginInfo(Class cls,Class impCls) {
			this(cls,impCls,null,null);
		}
		
		public PluginInfo(Class cls,Class impCls,Object bean,String beanName) {
			this.cls = cls;
			this.impCls = impCls;
			this.bean = bean;
	
			
			String suffix = cls.getSimpleName().toLowerCase();
			
			if( beanName != null )
				matchNames.add(beanName);
			matchNames.add(impCls.getName().toLowerCase());
			matchNames.add(impCls.getSimpleName().toLowerCase());
			matchNames.add(removeSuffix(impCls.getSimpleName().toLowerCase(),suffix));	
		}
	
		String removeSuffix(String s, String suffix) {
			if (s.endsWith(suffix)) {
				return s.substring(0, s.length() - suffix.length());
			}
			return s;
		}		
	}

	HashMap<String, List<PluginInfo>> plugins = new HashMap<>();

	RpcApp app = newRpcApp();
	
	public Bootstrap() {
		initSniffer();
		loadSpi();
	}

	public Bootstrap(String name) {
		appConfig.name = name;
	}

	public RpcApp newRpcApp() {
		return new RpcApp();
	}

	public ServiceMetas newServiceMetas(Validator v) {
		DefaultServiceMetas s = new DefaultServiceMetas();
		s.setValidator(v);
	    return s;
	}

	public Validator newValidator() {
		return new DefaultValidator();
	}	

	public RpcCodec newRpcCodec(ServiceMetas serviceMetas) {
		DefaultRpcCodec o = new DefaultRpcCodec(serviceMetas);
		return o;
	}

	public ProxyGenerator newProxyGenerator() {
		return new DefaultProxyGenerator();
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

	public RegistryManager newRegistryManager(ServiceMetas serviceMetas,String tempDir) {
		DefaultRegistryManager m = new DefaultRegistryManager(tempDir);
		m.setServiceMetas(serviceMetas);
		return m;
	}
	
	public DynamicRouteManager newDynamicRouteManager(ServiceMetas serviceMetas,String tempDir) {
		DefaultDynamicRouteManager m = new DefaultDynamicRouteManager(tempDir);
		m.setServiceMetas(serviceMetas);
		return m;
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
		m.setAccessLog(c.accessLog);
		
		if (!isEmpty(c.serverAddr)) {
			m.setServerAddr(c.serverAddr);
		}

		LogFormatter lf = getPlugin(LogFormatter.class,parseType(monitorConfig.logFormatter));
		String params = parseParams(monitorConfig.logFormatter);
		params += "maskFields="+(c.maskFields==null?"":c.maskFields)+ ";maxRepeatedSizeToLog="+c.maxRepeatedSizeToLog+";printDefault="+c.printDefault;
		lf.config(params);
		m.setLogFormatter(lf);
		
		if (!isEmpty(monitorConfig.plugins) ) {
			String[] ss = monitorConfig.plugins.split(",");
			List<MonitorPlugin> plugins = new ArrayList<>();
			for(String s:ss)  {
				plugins.add( getPlugin(MonitorPlugin.class,s) );
			}
			m.setPlugins(plugins);
		}
		
		return m;
	}

	public WebRouteService newRouteService(WebServerConfig c,String dataDir) {
		DefaultWebRouteService rs = new DefaultWebRouteService();
		rs.setDataDir(dataDir);

		if (!isEmpty(c.routesFile)) {
			loadRoutes(rs, c.routesFile);
		}

		return rs;
	}

	public BreakerInfo newBreakerInfo(RefererConfig c) {
		BreakerInfo bi = new BreakerInfo();
		bi.setEnabled(c.breakerEnabled);
		bi.setWindowSeconds(c.breakerWindowSeconds);
		bi.setWindowMinReqs(c.breakerWindowMinReqs);
		bi.setCloseBy(c.breakerCloseBy);
		bi.setCloseRate(c.breakerCloseRate);
		bi.setSleepMillis(c.breakerSleepSeconds*1000);
		bi.setSuccMills(c.breakerSuccMills);
		bi.setForceClose(c.breakerForceClose);
		return bi;
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

	public Router newRouter(int serviceId,String application) {
		return new DefaultRouter(serviceId,application);
	}
	
	public RpcApp build() {
		initSniffer();
		prepare();
		return doBuild();
	}

	public void initSniffer() {
		AdviceInstance.instance  = new Advice() {
	
			public void start(String type, String action) {
	System.out.println("TraceSniffer start called in app");			
				Trace.start(type, action);
			}
	
			public long stop(boolean ok) {
	System.out.println("TraceSniffer stop called in app");			
				return Trace.stop(ok);
			}
	
			public void logException(Throwable e) {
	System.out.println("TraceSniffer logException called in app");		
				Trace.logException(e);
			}
			
		};
	}
	
	public TraceAdapter newTraceAdapter() {
		Trace.setAppName(appConfig.name);
		return getPlugin(TraceAdapter.class,appConfig.traceAdapter);
	}
	
	private void prepare() {

		if (isEmpty(appConfig.name))
			throw new RuntimeException("app name must be specified");
		
		if( isEmpty(appConfig.dataDir) ) {
			appConfig.dataDir = ".";
		}
		
		if (!isEmpty(appConfig.errorMsgConverter)) {
			if (getPlugin(ErrorMsgConverter.class,appConfig.errorMsgConverter) == null) {
				throw new RuntimeException("unknown errorMsgConverter type, type=" + appConfig.errorMsgConverter);
			}		
		}
		
		if(!isEmpty(appConfig.fallbackPlugin)) {
			if (getPlugin(FallbackPlugin.class, appConfig.fallbackPlugin)== null) {
				throw new RuntimeException("unknown fallback plugin type, type=" + appConfig.fallbackPlugin);
			}		
		}
		
		if (isEmpty(monitorConfig.logFormatter)) {
			monitorConfig.logFormatter = "simple";
		}
		
		if (  getPlugin(LogFormatter.class,  parseType(monitorConfig.logFormatter)) == null) {
			throw new RuntimeException("log formatter not registered");
		}

		if( monitorConfig.pluginParams != null ) {
			for(String s:monitorConfig.pluginParams) {
				if (getPlugin(MonitorPlugin.class,s) == null)
					throw new RuntimeException(String.format("unknown monitor plugin %s", s));
			}
		}
					
		if (!isEmpty(monitorConfig.plugins) ) {
			String[] ss = monitorConfig.plugins.split(",");
			for(String s:ss)  {
				if (getPlugin(MonitorPlugin.class,s) == null)
					throw new RuntimeException("monitor plugin not registered");
			}
		}		

		if (serviceList.size() == 0 && refererList.size() == 0 && webServerList.size() == 0)
			throw new RuntimeException("service or referer or webserver must be specified");

		ServerConfig lastServer = null;
		//WebServerConfig lastWebServer = null;
		ClientConfig lastClient = null;
		
		String defaultRegistry  = null;
		
		for (RegistryConfig c : registryList) {
			if (  getPlugin(Registry.class, c.type) == null)
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
		if( defaultRegistry == null && registryList.size() == 1 ) defaultRegistry = registryList.get(0).type;

		for (ServerConfig c : serverList) {
			if (isEmpty(c.id))
				c.id = "default";
			if (servers.containsKey(c.id))
				throw new RuntimeException(String.format("server id %s duplicated", c.id));

			if( c.pluginParams != null ) {
				for(String s:c.pluginParams) {
					if ( getPlugin(RpcPlugin.class,s) == null)
						throw new RuntimeException(String.format("unknown rpc plugin %s", s));
				}
			}
						
			if (!isEmpty(c.plugins) ) {
				String[] ss = c.plugins.split(",");
				for(String s:ss)  {
					if ( getPlugin(RpcPlugin.class,s) == null  )
						throw new RuntimeException("rpc plugin not registered");
				}
			}

			servers.put(c.id, c);
			lastServer = c;
		}

		for (WebServerConfig c : webServerList) {
			if (isEmpty(c.id))
				c.id = "default";
			if (webServers.containsKey(c.id))
				throw new RuntimeException(String.format("web server id %s duplicated", c.id));
			
			if ( getPlugin(WebPlugin.class,c.defaultSessionService) == null)
				throw new RuntimeException(String.format("unknown session service %s", c.defaultSessionService));

			if( c.pluginParams != null ) {
				for(String s:c.pluginParams) {
					if (getPlugin(WebPlugin.class,s) == null)
						throw new RuntimeException(String.format("unknown web plugin %s", s));
				}
			}
			
			webServers.put(c.id, c);
			//lastWebServer = c;
		}

		for (ClientConfig c : clientList) {
			if (isEmpty(c.id))
				c.id = "default";
			if (clients.containsKey(c.id))
				throw new RuntimeException(String.format("client id %s duplicated", c.id));
			
			if( c.pluginParams != null ) {
				for(String s:c.pluginParams) {
					if (getPlugin(RpcPlugin.class,s) == null)
						throw new RuntimeException(String.format("unknown rpc plugin %s", s));
				}
			}
			
			if (!isEmpty(c.plugins) ) {
				String[] ss = c.plugins.split(",");
				for(String s:ss)  {
					if ( getPlugin(RpcPlugin.class,s)  == null )
						throw new RuntimeException("rpc plugin not registered");
				}
			}

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
			if( serviceInterfaces.contains(c.interfaceName)) 
				throw new RuntimeException(String.format("service %s duplicated", c.interfaceName));
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

					if (!webServers.containsKey(c.transport)) { // don't create  tcp server if  binds to http
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
			} else {
				if( defaultRegistry != null ) {
					c.registryNames = defaultRegistry;
				}
			}
			if (isEmpty(c.group)) {
				c.group = "default";
			}

			for (MethodConfig mc : c.getMethods()) {
				if (isEmpty(mc.pattern))
					throw new RuntimeException(String.format("method pattern not specified"));
			}

			serviceInterfaces.add(c.interfaceName);
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
					c.id = generateBeanName(c.interfaceName);
			} else { // dynamic message
				if (isEmpty(c.id))
					c.id = "referer"+c.serviceId;
				if( c.isReverse() )
					throw new RuntimeException("isReverse is not allowed in referer specified by serviceId");
			}
			if (referers.containsKey(c.id))
				throw new RuntimeException(String.format("referer id %s duplicated", c.id));
			if( refererInterfaces.contains(c.interfaceName)) 
				throw new RuntimeException(String.format("referer %s duplicated", c.interfaceName));
			
			if (isEmpty(c.transport)) {
				c.transport = "default";
			}

			if ( c.breakerCloseBy != 1 && c.breakerCloseBy != 2 ) {
				throw new RuntimeException("breakerCloseBy  not correct");
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
					throw new RuntimeException(String.format("client id %s loadbalance not specified", c.id));
				}

				if (getPlugin(LoadBalance.class,   c.loadBalance) == null)
					throw new RuntimeException(String.format("client id %s loadbalance not correct", c.id));
			}

			if (!c.isReverse()) {

				if (!isEmpty(c.registryName) && !isEmpty(c.direct)) {
					throw new RuntimeException(
							String.format("referer registry and direct cannot be specified at the same time", c.id));
				}

				if (!isEmpty(c.registryName)) {
					if (!registries.containsKey(c.registryName))
						throw new RuntimeException(String.format("service registry %s not found", c.registryName));
				} else {
					if(  isEmpty(c.direct)  ) {
						if( defaultRegistry != null ) {
							c.registryName = defaultRegistry;
						} else {
							c.direct = "127.0.0.1:5600";
						}
					}
				}

				if (isEmpty(c.group)) {
					c.group = "default";
				}
			}

			for (MethodConfig mc : c.getMethods()) {
				if (isEmpty(mc.pattern))
					throw new RuntimeException(String.format("method pattern not specified"));
			}

			refererInterfaces.add(c.interfaceName);
			referers.put(c.id, c);
		}

	}

	private RpcApp doBuild() {

		app.name = appConfig.name;
		app.instanceId = UUID.randomUUID().toString().replaceAll("-", "");
		
		TraceAdapter traceAdapter = newTraceAdapter();
		Trace.setAdapter(traceAdapter);
		app.traceAdapter = traceAdapter;

		app.validator = newValidator();
		app.serviceMetas = newServiceMetas(app.validator);
		app.codec = newRpcCodec(app.serviceMetas);
		app.proxyGenerator = newProxyGenerator();
		app.registryManager = newRegistryManager(app.serviceMetas,appConfig.dataDir);
		app.monitorService = newMonitorService(app.codec, app.serviceMetas, monitorConfig);

		if (!isEmpty(appConfig.errorMsgConverter)) {
			ErrorMsgConverter p = getPlugin(ErrorMsgConverter.class,  appConfig.errorMsgConverter);
			app.errorMsgConverter = p;
		}

		if(!isEmpty(appConfig.fallbackPlugin)) {
			FallbackPlugin p = getPlugin(FallbackPlugin.class, appConfig.fallbackPlugin);
			if( p instanceof ServiceMetasAware ) {
				((ServiceMetasAware)p).setServiceMetas(app.serviceMetas);
			}
			app.fallbackPlugin = p;
		}

		int processors = Runtime.getRuntime().availableProcessors();

		Map<String,Registry> regMap = new HashMap<>();
		for (String name : registries.keySet()) {
			RegistryConfig c = registries.get(name);
			Registry impl = getPlugin(Registry.class,parseType(c.type));
			String params = parseParams(c.type);
			params += "instanceId="+app.instanceId+";addrs="+c.addrs+";enableRegist="+c.enableRegist+";enableDiscover="+c.enableDiscover;
			if(!isEmpty(c.params))
				params += ";" + c.params;
			impl.config(params);
			app.registryManager.addRegistry(c.id, impl);
			regMap.put(parseType(c.type), impl);
		}

		if (!isEmpty(appConfig.dynamicRoutePlugin)) {
			
			app.dynamicRouteManager = newDynamicRouteManager(app.serviceMetas,appConfig.dataDir);
			
			Registry regPlugin = regMap.get(parseType(appConfig.dynamicRoutePlugin));
			if( regPlugin != null && regPlugin instanceof DynamicRoutePlugin ) { // use registry plugin first if the plugin has implemented DynamicRoute interface
				app.dynamicRouteManager.setDynamicRoutePlugin((DynamicRoutePlugin)regPlugin);
			} else {
				DynamicRoutePlugin dynamicRoutePlugin = getPlugin(DynamicRoutePlugin.class, appConfig.dynamicRoutePlugin);
				if (dynamicRoutePlugin == null)
					throw new RuntimeException("unknown dynamicRoutePlugin type, type=" + appConfig.dynamicRoutePlugin);
				app.dynamicRouteManager.setDynamicRoutePlugin(dynamicRoutePlugin);
			}
		}

		for (String name : servers.keySet()) {
			ServerConfig c = servers.get(name);
			RpcServer server = newRpcServer();
			server.setServiceMetas(app.serviceMetas);
			
			if (!isEmpty(c.plugins)) {
				List<RpcPlugin> plugins = new ArrayList<>();
				String[] ss = c.plugins.split(",");
				for(String s:ss)  {
					RpcPlugin p = getPlugin(RpcPlugin.class,s);
					plugins.add(p);
				}
				server.setPlugins(plugins);
			}
			
			server.setSampleRate(appConfig.sampleRate);
			server.setErrorMsgConverter(app.errorMsgConverter);
			server.setMonitorService(app.monitorService);
			server.setValidator(app.validator);
			server.setFallbackPlugin(app.fallbackPlugin);

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

		for (String name : webServers.keySet()) {
			WebServerConfig c = webServers.get(name);

			SessionService ss = (SessionService)getPlugin(WebPlugin.class,c.defaultSessionService);

			WebServer server = newWebServer();
			server.setSampleRate(appConfig.sampleRate);
			server.setExpireSeconds(c.expireSeconds);
			server.setAutoTrim(c.autoTrim);
			server.setServiceMetas(app.serviceMetas);
			server.setErrorMsgConverter(app.errorMsgConverter);
			server.setMonitorService(app.monitorService);
			server.setRouteService(newRouteService(c,appConfig.dataDir));
			server.setRpcDataConverter(newRpcDataConverter(app.serviceMetas));
			server.setDefaultSessionService(ss);
			server.setSessionIdCookieName(c.sessionIdCookieName);
			server.setSessionIdCookiePath(c.sessionIdCookiePath);
			server.setValidator(app.validator);

			NettyHttpServer ns = newNettyHttpServer();
			ns.setPort(c.port);
			ns.setCallback(server);
			ns.setHost(c.host);
			ns.setBacklog(c.backlog);
			ns.setIdleSeconds(c.idleSeconds);
			ns.setDataDir(appConfig.dataDir);
			ns.setMaxConns(c.maxConns);
			ns.setWorkerThreads(c.ioThreads);
			ns.setMaxContentLength(c.maxContentLength);
			ns.setMaxUploadLength(c.maxUploadLength);
			ns.setMaxInitialLineLength(c.maxInitialLineLength);
			ns.setMaxHeaderSize(c.maxHeaderSize);
			ns.setMaxChunkSize(c.maxChunkSize);

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

		for (String name : clients.keySet()) {
			ClientConfig c = clients.get(name);

			RpcClient client = newRpcClient();
			client.setServiceMetas(app.serviceMetas);
			client.setValidator(app.validator);
			client.setFallbackPlugin(app.fallbackPlugin);
			client.setSampleRate(appConfig.sampleRate);

			if (!isEmpty(c.plugins)) {
				List<RpcPlugin> plugins = new ArrayList<>();
				String[] ss = c.plugins.split(",");
				for(String s:ss)  {
					RpcPlugin p = getPlugin(RpcPlugin.class,s);
					plugins.add(p);
				}
				client.setPlugins(plugins);
			}
						
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

				client.setErrorMsgConverter(app.errorMsgConverter);
			}

			client.setMonitorService(app.monitorService);

			app.clients.put(name, client);
		}

		for (String name : services.keySet()) {
			ServiceConfig c = services.get(name);

			Class<?> cls = ReflectionUtils.getClass(c.interfaceName);
			int serviceId = ReflectionUtils.getServiceId(cls);

			ExecutorManager em = null;
			RpcCallableBase callable = null;
			String addr = null;
			
			if (c.reverse) {
				callable = app.clients.get(c.transport);
				callable.addAllowedService(serviceId);
				em = callable.getExecutorManager();
			} else {
				callable = app.servers.get(c.transport);
				if (callable != null) {
					callable.addAllowedService(serviceId);
					em = callable.getExecutorManager();
					ServerConfig sc = servers.get(c.transport);
					addr = IpUtils.localIp()+":"+sc.port;
				} else {
					WebServer webServer = app.webServers.get(c.transport);
					em = webServer.getExecutorManager();
					//WebServerConfig wsc = webServers.get(c.transport);
					//addr = IpUtils.localIp()+":"+wsc.port;
				}
			}
			
			app.serviceMetas.addService(cls, c.impl, callable);

			if ( !isEmpty(c.registryNames) && addr != null) {
				String[] ss = c.registryNames.split(",");
				for (String s : ss)
					app.registryManager.register(serviceId, s, c.group, addr);
			}

			if (c.threads >= 0) {
				if (c.threads == 0)
					c.threads = processors;
				em.addPool(serviceId, c.threads, c.maxThreads, c.queueSize);
			}

			for (MethodConfig mc : c.getMethods()) {
				int[] msgIds = patternToMsgIds(serviceId, mc.pattern);
				if (msgIds == null || msgIds.length == 0)
					throw new RuntimeException(String.format("no msgId match method pattern " + mc.pattern));
				if (mc.threads >= 0) {
					if (mc.threads == 0)
						mc.threads = processors;
					em.addPool(serviceId, msgIds, mc.threads, mc.maxThreads, mc.queueSize);
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
				Object impl = app.proxyGenerator.generateReferer(cls, callable);
				app.serviceMetas.addReferer(cls, impl, callable);
				app.referers.put(name, impl);
				Class<?> asyncCls = ReflectionUtils.getClass(c.interfaceName+"Async");
				Object asyncImpl = app.proxyGenerator.generateAsyncReferer(asyncCls, callable);
				app.serviceMetas.addAsyncReferer(asyncCls, asyncImpl, callable);
				app.referers.put(name+"Async", asyncImpl);
				
			} else {
				app.serviceMetas.addDynamic(serviceId,callable);
			}
			
			if (callable instanceof RpcClient) {
				RpcClient client = (RpcClient) callable;
				client.addRetryPolicy(serviceId, -1, c.timeout, c.retryCount);

				DefaultClusterManager cmi = (DefaultClusterManager) client.getClusterManager();

				LoadBalance lb = getPlugin(LoadBalance.class,c.loadBalance);
				Router r = newRouter(serviceId,appConfig.name);
				BreakerInfo bi = newBreakerInfo(c);
				cmi.addServiceInfo(serviceId, lb, r,bi);

				if (!isEmpty(c.registryName)) {
					app.registryManager.addDiscover(serviceId, c.registryName, c.group, cmi);
				} else {
					app.registryManager.addDirect(serviceId, c.direct, cmi);
				}

				if( app.dynamicRouteManager != null ) {
					app.dynamicRouteManager.addConfig(serviceId, c.group, cmi);
				}
				
				for (MethodConfig mc : c.getMethods()) {
					int[] msgIds = patternToMsgIds(serviceId, mc.pattern);
					if (msgIds == null || msgIds.length == 0)
						throw new RuntimeException(String.format("no msgId match method pattern " + mc.pattern));

					for (int msgId : msgIds) {
						client.addRetryPolicy(serviceId, msgId, mc.timeout, 
								mc.retryCount);
					}
				}
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
			for(String file:files) {
				loadProtoFile(app,base,proto + file);  
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
	
	List<String> getProtoFiles(String proto) throws IOException {
		List<String> list = new ArrayList<>();
	
		Enumeration<URL> urls = getClass().getClassLoader().getResources(proto);
		while( urls.hasMoreElements() ) {
			URL url = urls.nextElement();
			getProtoFiles(proto,list,url);
		}

        if( list.size() == 0 ) {
        	log.info("no dynamic proto resource loaded from "+proto);
        }
        
        return list;
	}
	
    void  getProtoFiles(String proto,List<String>list,URL url) {
        String path = url.getPath(); 
        if (url.getProtocol().equals("file")) { 

            String classesDirPath = path.substring(path.indexOf("/"));
            File classesDir = new File(classesDirPath); 
            File[] files = classesDir.listFiles();
            if( files != null ) {
                for (File file : files) { 
                    String resourceName = file.getName();  
                    if (!file.isDirectory() && resourceName.endsWith(".proto.pb")) {  
                    	list.add(resourceName);  
                    } 
                } 
            }
            
        } else if (url.getProtocol().equals("jar")) {
 
            String jarPath = path.substring(path.indexOf("/"), path.indexOf("!")); 
            try { 
                JarFile jarFile = new JarFile(URLDecoder.decode(jarPath, "UTF-8"));  
                Enumeration<JarEntry> jarEntries = jarFile.entries(); 
                while (jarEntries.hasMoreElements()) { 
                    JarEntry jarEntry = (JarEntry)jarEntries.nextElement();  
                    String resourceName = jarEntry.getName();  
                    if (resourceName.endsWith(".proto.pb") && !jarEntry.isDirectory()) {  
                    	list.add(resourceName.substring(proto.length()));  
                    } 
                } 
                jarFile.close();
            } catch (Exception e) {  
                throw new RuntimeException("load dynamic proto resource failed",e);
            } 
        } 
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

	int[] patternToMsgIds(int serviceId, String pattern) {
		char ch = pattern.charAt(0);
		boolean byMsgId = false;
		if (ch >= '0' && ch <= '9') {
			byMsgId = true;
		}
		if (byMsgId) {
			return splitMsgIdPattern(serviceId, pattern);
		} else {
			return matchMsgNamePattern(serviceId, pattern);
		}
	}

	int[] splitMsgIdPattern(int serviceId, String pattern) {
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

		Map<Integer, String> msgIdMap = app.serviceMetas.getMsgNames(serviceId);
		for (int i : list) {
			if (!msgIdMap.containsKey(i))
				throw new RuntimeException(String.format("msgId %d not found", i));
		}
		int[] vs = new int[list.size()];
		for (int i = 0; i < vs.length; ++i)
			vs[i] = list.get(i);
		return vs;
	}

	int[] matchMsgNamePattern(int serviceId, String pattern) {
		ArrayList<Integer> list = new ArrayList<Integer>();
		Map<Integer, String> msgNameMap = app.serviceMetas.getMsgNames(serviceId);
		for (Map.Entry<Integer, String> entry: msgNameMap.entrySet() ) {
			int msgId = entry.getKey();
			String msgName = entry.getValue();
			if (msgName.matches(pattern))
				list.add(msgId);
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

	void loadSpi() {
		try {
			loadSpi(LoadBalance.class);
			loadSpi(RpcPlugin.class);
			loadSpi(Registry.class);
			loadSpi(ErrorMsgConverter.class);
			loadSpi(LogFormatter.class);
			loadSpi(WebPlugin.class);
			loadSpi(DynamicRoutePlugin.class);
			loadSpi(MonitorPlugin.class);
			loadSpi(FallbackPlugin.class);
			loadSpi(TraceAdapter.class);
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}

	List<String> readSpiLines(URL url) {
		List<String> lines = new ArrayList<>();

		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
			 String s = in.readLine();
			 while(  s != null ) {
				 if( !s.isEmpty() ) {
					 lines.add(s);
				 }
				 s = in.readLine();
			 }
			 in.close();
		} catch(IOException e) {
			throw new RuntimeException(e);
		}

		return lines;
	}
	
	@SuppressWarnings("rawtypes")
	void loadSpi(Class cls)  throws Exception {
		Enumeration<URL> urls = getClass().getClassLoader().getResources("META-INF/services/"+cls.getName());
		List<PluginInfo> list = new ArrayList<>();
		while( urls.hasMoreElements() ) {
			URL url = urls.nextElement();
			List<String> lines = readSpiLines(url);

			for(String s : lines ) {
				if( s.trim().isEmpty() ) continue;
	
				try {
					Class implCls = Class.forName(s);
					PluginInfo pi = new PluginInfo(cls,implCls);
					list.add(pi);
				} catch(Throwable e) {
					log.info("cannot load plugin: "+s);
				}
				
			}
			
		}
		plugins.put(cls.getName(), list);
	}

	public void mergePlugins(HashMap<String, List<PluginInfo>> map) { // for spring context
		for(Map.Entry<String, List<PluginInfo>> entry: map.entrySet()) {
			String name = entry.getKey();
			List<PluginInfo> list = entry.getValue();
			List<PluginInfo> t = plugins.get(name);
			if( t != null ) {
				list.addAll(t); // spring bean first
			}
			plugins.put(name,list);
		}
	}

	@SuppressWarnings("unchecked")
	<T> T getPlugin(Class<T> cls, String params) {

		List<PluginInfo> list = plugins.get(cls.getName());
		if( list == null ) return null;
		String type = parseType(params);
		for( PluginInfo pi: list ) {
			if( pi.matchNames.contains(type) ) {
				
				if( pi.bean != null ) return (T)pi.bean;
			
				pi.bean = ReflectionUtils.newObject(pi.impCls.getName());
				String t = parseParams(params);
				if( !t.isEmpty() ) {
					Plugin p = (Plugin)pi.bean;
					p.config(t);
				}					
				return (T)pi.bean;
			}
		}
		return null;
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

	private void loadRoutes(DefaultWebRouteService rs, String routesFile) {

		try {
			loadRoutesFileInternal(rs, routesFile);
		} catch (Exception e) {
			throw new RuntimeException("cannot load mapping file, file=" + routesFile, e);
		}

	}

	void loadRoutesFileInternal(DefaultWebRouteService rs, String mappingFile) throws Exception {

		DocumentBuilderFactory docbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder docb = docbf.newDocumentBuilder();
		Document doc = docb.parse(getResource(mappingFile));
		Node root = doc.getChildNodes().item(0);
		NodeList rootChildren = root.getChildNodes();
		
		for (int i = 0; i < rootChildren.getLength(); i++) {
			Node node = rootChildren.item(i);
			if (node.getNodeType() != Node.ELEMENT_NODE ) continue;
			switch( node.getNodeName() ) {
				case "import":
					importRoutes(rs,node);
					break;
				case "group":
					loadGroup(rs,node);
					break;
				case "dir":
					loadDir(rs,node);
					break;
				case "url":
					loadUrl(rs,node);
					break;
				default:
					break;
			}
		}

	}

	private void loadGroup(DefaultWebRouteService rs, Node node) {
		Map<String, String> defaultAttrs = getAttrs(node);

		String defaultHosts = defaultAttrs.getOrDefault("hosts", "");
		String t = defaultAttrs.getOrDefault("path", "");
		if (!t.isEmpty() )
			throw new RuntimeException("mapping path in group is not allowed, use prefix instead");
		String prefix = normalizePath(defaultAttrs.getOrDefault("prefix", ""));
		if (prefix.equals("/"))
			prefix = "";

		String defaultMethods = defaultAttrs.getOrDefault("methods", "");
		String defaultOrigins = defaultAttrs.getOrDefault("origins", "");
		String defaultServiceId = defaultAttrs.getOrDefault("serviceId", "");
		String defaultMsgId = defaultAttrs.getOrDefault("msgId", "");
		if (!isEmpty(defaultMsgId))
			throw new RuntimeException("mapping msgid in group is not allowed");
		String defaultSessionMode = defaultAttrs.getOrDefault("sessionMode", "0");
		WebPlugins defaultPlugins = loadWebPlugins(defaultAttrs.getOrDefault("plugins", ""));

		defaultAttrs.remove("hosts");
		defaultAttrs.remove("prefix");
		defaultAttrs.remove("path");
		defaultAttrs.remove("methods");
		defaultAttrs.remove("origins");
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

				String path = normalizePath(attrs.get("path"));
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

				String origins = attrs.getOrDefault("origins", "");
				if (isEmpty(origins))
					origins = defaultOrigins;
				if (isEmpty(origins))
					origins = "";

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
				if (sessionMode < WebRoute.SESSION_MODE_NO || sessionMode > WebRoute.SESSION_MODE_OPTIONAL)
					throw new RuntimeException("sessionMode is not valid");

				String plugins = attrs.get("plugins");
				WebPlugins pluginsList = loadWebPlugins(plugins);
				if (pluginsList == null)
					pluginsList = defaultPlugins;

				attrs.remove("hosts");
				attrs.remove("path");
				attrs.remove("methods");
				attrs.remove("origins");
				attrs.remove("serviceId");
				attrs.remove("msgId");
				attrs.remove("sessionMode");
				attrs.remove("plugins");
				
				Map<String, String> allAttrs = new HashMap<>();
				allAttrs.putAll(defaultAttrs);
				allAttrs.putAll(attrs);
				
				WebUrl url = new WebUrl(hosts, path);
				url.setMethods(methods).setServiceId(serviceId).setMsgId(msgId).setSessionMode(sessionMode)
						.setPlugins(pluginsList).setAttrs(allAttrs).setOrigins(origins);
				rs.addUrl(url);
			}
		}
	}

	private void loadUrl(DefaultWebRouteService rs, Node node) {
		Map<String, String> attrs = getAttrs(node);

		String hosts = attrs.getOrDefault("hosts", "*");
		if (isEmpty(hosts))
			throw new RuntimeException("hosts can not be empty");
		String path = normalizePath(attrs.get("path"));
		if (isEmpty(path))
			throw new RuntimeException("path can not be empty");

		String methods = attrs.getOrDefault("methods", "get,post").toLowerCase();
		if (!checkMethod(methods))
			throw new RuntimeException("mapping methods is not valid, methods=" + methods);

		String origins = attrs.getOrDefault("origins", "");
		
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
		if (sessionMode < WebRoute.SESSION_MODE_NO || sessionMode > WebRoute.SESSION_MODE_OPTIONAL)
			throw new RuntimeException("sessionMode is not valid");

		WebPlugins pluginList = loadWebPlugins(attrs.get("plugins"));

		attrs.remove("hosts");
		attrs.remove("path");
		attrs.remove("methods");
		attrs.remove("origins");
		attrs.remove("serviceId");
		attrs.remove("msgId");
		attrs.remove("sessionMode");
		attrs.remove("plugins");
		
		WebUrl url = new WebUrl(hosts, path);
		url.setMethods(methods).setServiceId(serviceId).setMsgId(msgId).setPlugins(pluginList)
				.setSessionMode(sessionMode).setAttrs(attrs).setOrigins(origins);
		rs.addUrl(url);
	}

	private void loadDir(DefaultWebRouteService rs, Node node) {
		Map<String, String> attrs = getAttrs(node);

		String hosts = attrs.getOrDefault("hosts", "*");
		if (isEmpty(hosts))
			throw new RuntimeException("hosts can not be empty");
		String path = normalizePath(attrs.get("path"));
		if (isEmpty(path))
			throw new RuntimeException("path can not be empty");

		String staticDir = normalizeDir(attrs.get("staticDir"));
		String templateDir = normalizeDir(attrs.get("templateDir"));

		if (!isEmpty(staticDir) ) {
			if( staticDir.equals("classpath:") ) // too dangerous, can download any file in the jar
				throw new RuntimeException("root classpath is not allowed for staticDir, staticDir=" + staticDir);
			if( !checkDirExist(staticDir) )
				throw new RuntimeException("staticDir is not correct, staticDir=" + staticDir);
		}
		if (!isEmpty(templateDir) && !checkDirExist(templateDir)) {
			throw new RuntimeException("templateDir is not correct, templateDir=" + templateDir);
		}

		if ( isEmpty(staticDir) && isEmpty(templateDir))
			throw new RuntimeException("not a valid dir");

		WebDir dir = new WebDir(hosts, path);
		dir.setStaticDir(staticDir).setTemplateDir(templateDir);
		rs.addDir(dir);
	}

	private void importRoutes(DefaultWebRouteService rs, Node node) throws Exception {
		Map<String, String> attrs = getAttrs(node);
		String file = attrs.get("file");
		if (isEmpty(file))
			throw new RuntimeException("import file must be specified");
		loadRoutesFileInternal(rs, file);
	}

	InputStream getResource(String file) {
		return getClass().getClassLoader().getResourceAsStream(file);
	}

	boolean checkDirExist(String dir) {
		if( dir.startsWith("classpath:")) {
			dir = dir.substring(10);
			if( dir.isEmpty() ) return true;
			return getClass().getClassLoader().getResource(dir) != null;
		} else {
			File f = new File(dir);
			return f.exists() && f.isDirectory();
		}
	}
	
	String generateBeanName(String interfaceName) {
		int p = interfaceName.lastIndexOf(".");
		String name = interfaceName.substring(p+1);
		name = name.substring(0,1).toLowerCase()+name.substring(1);
		return name;
	}
	
	String normalizePath(String path) {
		if (!path.startsWith("/"))
			path = "/" + path;
		if (path.endsWith("/") && !path.equals("/"))
			path = path.substring(0, path.length() - 1);
		return path;
	}

	String normalizeDir(String dir) {
		if( isEmpty(dir) ) return dir;
		dir = dir.replace('\\', '/'); // always linux style
		if( dir.startsWith("classpath:")) {
			dir = dir.substring(10);
			if( dir.startsWith("/")) dir = dir.substring(1);	
			dir = "classpath:" + dir;
		}
		if( dir.endsWith("/")) dir = dir.substring(0, dir.length() - 1);	
		return dir;
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

	WebPlugins loadWebPlugins(String plugins) {
		if (isEmpty(plugins))
			return null;
		String[] ss = plugins.split(",");
		WebPlugin[] array = new WebPlugin[ss.length];
		for (int i = 0; i < ss.length; ++i) {
			array[i] = getPlugin(WebPlugin.class,ss[i]);
			if (array[i] == null)
				throw new RuntimeException("web plugin not found, name=" + array[i]);
		}
		return new WebPlugins(array);
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

	public Bootstrap setDynamicRoutePlugin(String dynamicRoutePlugin) {
		appConfig.dynamicRoutePlugin = dynamicRoutePlugin;
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

	public Bootstrap addReferer(String interfaceName, String direct) {
		RefererConfig c = new RefererConfig();
		c.interfaceName = interfaceName;
		c.direct = direct;
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
	
	public Bootstrap addReferer(Class<?> intf, String direct) {
		RefererConfig c = new RefererConfig();
		c.interfaceName = intf.getName();
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

	public Bootstrap setAppConfig(ApplicationConfig appConfig) {
		this.appConfig = appConfig;
		return this;
	}

	public Bootstrap setTraceAdapter(String traceAdapter) {
		this.appConfig.traceAdapter = traceAdapter;
		return this;
	}

	public Bootstrap setFallbackPlugin(String fallbackPlugin) {
		this.appConfig.fallbackPlugin = fallbackPlugin;
		return this;
	}	
	
	public Bootstrap setMonitorConfig(MonitorConfig monitorConfig) {
		this.monitorConfig = monitorConfig;
		return this;
	}

	public List<RegistryConfig> getRegistryList() {
		return registryList;
	}

	public void setRegistryList(List<RegistryConfig> registryList) {
		this.registryList = registryList;
	}

	public List<ServerConfig> getServerList() {
		return serverList;
	}

	public void setServerList(List<ServerConfig> serverList) {
		this.serverList = serverList;
	}

	public List<ClientConfig> getClientList() {
		return clientList;
	}

	public void setClientList(List<ClientConfig> clientList) {
		this.clientList = clientList;
	}

	public List<ServiceConfig> getServiceList() {
		return serviceList;
	}

	public void setServiceList(List<ServiceConfig> serviceList) {
		this.serviceList = serviceList;
	}

	public List<RefererConfig> getRefererList() {
		return refererList;
	}

	public void setRefererList(List<RefererConfig> refererList) {
		this.refererList = refererList;
	}

	public List<WebServerConfig> getWebServerList() {
		return webServerList;
	}

	public void setWebServerList(List<WebServerConfig> webServerList) {
		this.webServerList = webServerList;
	}

	public ApplicationConfig getAppConfig() {
		return appConfig;
	}

	public MonitorConfig getMonitorConfig() {
		return monitorConfig;
	}
}
