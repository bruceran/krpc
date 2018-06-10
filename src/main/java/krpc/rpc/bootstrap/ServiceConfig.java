package krpc.rpc.bootstrap;

import java.util.ArrayList;
import java.util.List;

public class ServiceConfig  {

	String id;
	String interfaceName;
	Object impl;
	
	String transport; // refer to a client or a server
	boolean reverse = false;

	String registryNames; // refer to multiple registry
	String group;

	String flowControlParams;

	int threads = -1;
	int maxThreads = 0;
	int queueSize = 10000;
	
	List<MethodConfig> methods = new ArrayList<MethodConfig>();
	
	public ServiceConfig() {
	}

	public ServiceConfig(String id) {
		this.id = id;
	}

	public ServiceConfig addMethod(MethodConfig method) {
		methods.add(method);		
		return this;
	}
	
	public String getId() {
		return id;
	}

	public ServiceConfig setId(String id) {
		this.id = id;
		return this;
	}

	public String getTransport() {
		return transport;
	}

	public ServiceConfig setTransport(String transport) {
		this.transport = transport;
		return this;
	}

	public String getInterfaceName() {
		return interfaceName;
	}

	public ServiceConfig setInterfaceName(String interfaceName) {
		this.interfaceName = interfaceName;
		return this;
	}

	public Object getImpl() {
		return impl;
	}

	public ServiceConfig setImpl(Object impl) {
		this.impl = impl;
		return this;
	}

	public String getRegistryNames() {
		return registryNames;
	}

	public ServiceConfig setRegistryNames(String registryNames) {
		this.registryNames = registryNames;
		return this;
	}

	public int getThreads() {
		return threads;
	}

	public ServiceConfig setThreads(int threads) {
		this.threads = threads;
		return this;
	}

	public int getQueueSize() {
		return queueSize;
	}

	public ServiceConfig setQueueSize(int queueSize) {
		this.queueSize = queueSize;
		return this;
	}

	public boolean isReverse() {
		return reverse;
	}

	public ServiceConfig setReverse(boolean reverse) {
		this.reverse = reverse;
		return this;
	}

	public String getGroup() {
		return group;
	}

	public ServiceConfig setGroup(String group) {
		this.group = group;
		return this;
	}

	public List<MethodConfig> getMethods() {
		return methods;
	}

	public ServiceConfig setMethods(List<MethodConfig> methods) {
		this.methods = methods;
		return this;
	}

	public int getMaxThreads() {
		return maxThreads;
	}

	public ServiceConfig setMaxThreads(int maxThreads) {
		this.maxThreads = maxThreads;
		return this;
	}

	public String getFlowControlParams() {
		return flowControlParams;
	}

	public ServiceConfig setFlowControlParams(String flowControlParams) {
		this.flowControlParams = flowControlParams;
		return this;
	}	
	
	
}
