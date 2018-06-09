package krpc.rpc.bootstrap;

public class ServerConfig  {

	String id;
	
	int port = 5600;
	String host = "*";
	int backlog = 128;
	int idleSeconds = 180;
	int maxPackageSize = 1000000;
	int maxConns = 500000;
	int ioThreads = 0;	// auto
	
	int notifyThreads = -1; // // for reverse call, future listener, 0=auto -1=no threads
	int notifyMaxThreads = 0;
	int notifyQueueSize = 10000;	
	
	int threads = 0; // workthreads, 0=auto -1=no workthreads,use iothreads n=workthreads
	int maxThreads = 0;
	int queueSize = 10000;
	
	String flowControl = "";
	
	public ServerConfig() {
	}

	public ServerConfig(String id) {
		this.id = id;
	}
	
	public String getId() {
		return id;
	}
	public ServerConfig setId(String id) {
		this.id = id;
		return this;
	}
	public int getPort() {
		return port;
	}
	public ServerConfig setPort(int port) {
		this.port = port;
		return this;
	}
	public String getHost() {
		return host;
	}
	public ServerConfig setHost(String host) {
		this.host = host;
		return this;
	}
	public int getIdleSeconds() {
		return idleSeconds;
	}
	public ServerConfig setIdleSeconds(int idleSeconds) {
		this.idleSeconds = idleSeconds;
		return this;
	}
	public int getMaxPackageSize() {
		return maxPackageSize;
	}
	public ServerConfig setMaxPackageSize(int maxPackageSize) {
		this.maxPackageSize = maxPackageSize;
		return this;
	}
	public int getMaxConns() {
		return maxConns;
	}
	public ServerConfig setMaxConns(int maxConns) {
		this.maxConns = maxConns;
		return this;
	}
	public int getIoThreads() {
		return ioThreads;
	}
	public ServerConfig setIoThreads(int ioThreads) {
		this.ioThreads = ioThreads;
		return this;
	}
	public int getNotifyThreads() {
		return notifyThreads;
	}
	public ServerConfig setNotifyThreads(int notifyThreads) {
		this.notifyThreads = notifyThreads;
		return this;
	}
	public int getNotifyQueueSize() {
		return notifyQueueSize;
	}
	public ServerConfig setNotifyQueueSize(int notifyQueueSize) {
		this.notifyQueueSize = notifyQueueSize;
		return this;
	}
	public int getThreads() {
		return threads;
	}
	public ServerConfig setThreads(int threads) {
		this.threads = threads;
		return this;
	}
	public int getQueueSize() {
		return queueSize;
	}
	public ServerConfig setQueueSize(int queueSize) {
		this.queueSize = queueSize;
		return this;
	}

	public int getNotifyMaxThreads() {
		return notifyMaxThreads;
	}

	public ServerConfig setNotifyMaxThreads(int notifyMaxThreads) {
		this.notifyMaxThreads = notifyMaxThreads;
		return this;
	}

	public int getMaxThreads() {
		return maxThreads;
	}

	public ServerConfig setMaxThreads(int maxThreads) {
		this.maxThreads = maxThreads;
		return this;
	}

	public int getBacklog() {
		return backlog;
	}

	public ServerConfig setBacklog(int backlog) {
		this.backlog = backlog;
		return this;
	}

	public String getFlowControl() {
		return flowControl;
	}

	public ServerConfig setFlowControl(String flowControl) {
		this.flowControl = flowControl;
		return this;
	}	
}
