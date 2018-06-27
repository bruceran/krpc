package krpc.rpc.bootstrap;

import java.util.ArrayList;
import java.util.List;

import krpc.rpc.web.WebConstants;

public class WebServerConfig  {

	String id;
	
	int port = 8600;
	String host = "*";
	int backlog = 128;
	int idleSeconds = 30;
	int maxContentLength = 1000000;
	int maxConns = 500000;
	int ioThreads = 0;	// auto

	int threads = 0; // workthreads, 0=auto -1=no workthreads,use iothreads n=workthreads
	int maxThreads = 0;
	int queueSize = 10000;

	String routesFile = "webroutes.xml";
	String protoDir = "proto";
	String sessionIdCookieName = WebConstants.DefaultSessionIdCookieName;
	String sessionIdCookiePath = "";
	int expireSeconds = 0;
	
	int sampleRate = 1; // todo doc  sample if hash(traceId) % sampleRate == 0, now only for webserver

	String defaultSessionService = "memorysessionservice";
	List<String> pluginParams  = new ArrayList<>(); // config WebPlugins if needed
	
	public WebServerConfig addPluginParams(String params) {
		pluginParams.add(params);
		return this;
	}
	
	public WebServerConfig() {
	}
	
	public WebServerConfig(int port) {
		this.port = port;
	}

	public WebServerConfig(String id) {
		this.id = id;
	}

	public String getId() {
		return id;
	}
	public WebServerConfig setId(String id) {
		this.id = id;
		return this;
	}
	public int getPort() {
		return port;
	}
	public WebServerConfig setPort(int port) {
		this.port = port;
		return this;
	}
	public String getHost() {
		return host;
	}
	public WebServerConfig setHost(String host) {
		this.host = host;
		return this;
	}
	public int getIdleSeconds() {
		return idleSeconds;
	}
	public WebServerConfig setIdleSeconds(int idleSeconds) {
		this.idleSeconds = idleSeconds;
		return this;
	}
	public int getMaxConns() {
		return maxConns;
	}
	public WebServerConfig setMaxConns(int maxConns) {
		this.maxConns = maxConns;
		return this;
	}
	public int getIoThreads() {
		return ioThreads;
	}
	public WebServerConfig setIoThreads(int ioThreads) {
		this.ioThreads = ioThreads;
		return this;
	}

	public int getThreads() {
		return threads;
	}
	public WebServerConfig setThreads(int threads) {
		this.threads = threads;
		return this;
	}
	public int getQueueSize() {
		return queueSize;
	}
	public WebServerConfig setQueueSize(int queueSize) {
		this.queueSize = queueSize;
		return this;
	}
	public int getMaxContentLength() {
		return maxContentLength;
	}
	public WebServerConfig setMaxContentLength(int maxContentLength) {
		this.maxContentLength = maxContentLength;
		return this;
	}

	public int getMaxThreads() {
		return maxThreads;
	}

	public WebServerConfig setMaxThreads(int maxThreads) {
		this.maxThreads = maxThreads;
		return this;
	}

	public String getRoutesFile() {
		return routesFile;
	}

	public WebServerConfig setRoutesFile(String routesFile) {
		this.routesFile = routesFile;
		return this;
	}

	public String getSessionIdCookieName() {
		return sessionIdCookieName;
	}

	public WebServerConfig setSessionIdCookieName(String sessionIdCookieName) {
		this.sessionIdCookieName = sessionIdCookieName;
		return this;
	}

	public String getProtoDir() {
		return protoDir;
	}

	public WebServerConfig setProtoDir(String protoDir) {
		this.protoDir = protoDir;
		return this;
	}

	public String getSessionIdCookiePath() {
		return sessionIdCookiePath;
	}

	public WebServerConfig setSessionIdCookiePath(String sessionIdCookiePath) {
		this.sessionIdCookiePath = sessionIdCookiePath;
		return this;
	}

	public int getSampleRate() {
		return sampleRate;
	}

	public WebServerConfig setSampleRate(int sampleRate) {
		this.sampleRate = sampleRate;
		return this;
	}

	public int getBacklog() {
		return backlog;
	}

	public WebServerConfig setBacklog(int backlog) {
		this.backlog = backlog;
		return this;
	}

	public String getDefaultSessionService() {
		return defaultSessionService;
	}

	public WebServerConfig setDefaultSessionService(String defaultSessionService) {
		this.defaultSessionService = defaultSessionService;
		return this;
	}

	public List<String> getPluginParams() {
		return pluginParams;
	}

	public WebServerConfig setPluginParams(List<String> pluginParams) {
		this.pluginParams = pluginParams;
		return this;
	}

	public int getExpireSeconds() {
		return expireSeconds;
	}

	public WebServerConfig setExpireSeconds(int expireSeconds) {
		this.expireSeconds = expireSeconds;
		return this;
	}

}
