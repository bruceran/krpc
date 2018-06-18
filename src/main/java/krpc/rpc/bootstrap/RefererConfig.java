package krpc.rpc.bootstrap;

import java.util.ArrayList;
import java.util.List;

public class RefererConfig  {

	String id;
	
	String interfaceName;
	int serviceId;

	String transport; // refer to a client or a server
	boolean reverse = false;
	
	String direct; // direct url
	String registryName; // refer to only one registry
	String group;

	int timeout = 3000;
	String retryLevel = "no_retry";
	int retryCount = 0;

	String loadBalance = "roundrobin"; // can be empty (use client default), or random, roundrobin, ...
	
	boolean breakerEnabled = false ;
	int breakerWindowSeconds = 5;
	int breakerCloseBy = 1; // 1=errorRate 2=timeoutRate
	int breakerCloseRate  = 50; // 50% in 5 seconds to close the addr
	int breakerWaitSeconds = 5;
	int breakerSuccMills = 500;
	
	String zip;
	int minSizeToZip = 10000;
	
	List<MethodConfig> methods = new ArrayList<MethodConfig>();

	public RefererConfig() {
	}

	public RefererConfig(String id) {
		this.id = id;
	}

	public RefererConfig addMethod(MethodConfig method) {
		methods.add(method);		
		return this;
	}

	public String getId() {
		return id;
	}

	public RefererConfig setId(String id) {
		this.id = id;
		return this;
	}

	public String getTransport() {
		return transport;
	}

	public RefererConfig setTransport(String transport) {
		this.transport = transport;
		return this;
	}

	public String getInterfaceName() {
		return interfaceName;
	}

	public RefererConfig setInterfaceName(String interfaceName) {
		this.interfaceName = interfaceName;
		return this;
	}

	public String getRegistryName() {
		return registryName;
	}

	public RefererConfig setRegistryName(String registryName) {
		this.registryName = registryName;
		return this;
	}

	public int getTimeout() {
		return timeout;
	}

	public RefererConfig setTimeout(int timeout) {
		this.timeout = timeout;
		return this;
	}

	public String getRetryLevel() {
		return retryLevel;
	}

	public RefererConfig setRetryLevel(String retryLevel) {
		this.retryLevel = retryLevel;
		return this;
	}

	public int getRetryCount() {
		return retryCount;
	}

	public RefererConfig setRetryCount(int retryCount) {
		this.retryCount = retryCount;
		return this;
	}

	public String getLoadBalance() {
		return loadBalance;
	}

	public RefererConfig setLoadBalance(String loadBalance) {
		this.loadBalance = loadBalance;
		return this;
	}

	public boolean isReverse() {
		return reverse;
	}

	public RefererConfig setReverse(boolean reverse) {
		this.reverse = reverse;
		return this;
	}

	public String getGroup() {
		return group;
	}

	public RefererConfig setGroup(String group) {
		this.group = group;
		return this;
	}

	public String getDirect() {
		return direct;
	}

	public RefererConfig setDirect(String direct) {
		this.direct = direct;
		return this;
	}

	public List<MethodConfig> getMethods() {
		return methods;
	}

	public RefererConfig setMethods(List<MethodConfig> methods) {
		this.methods = methods;
		return this;
	}

	public String getZip() {
		return zip;
	}

	public RefererConfig setZip(String zip) {
		this.zip = zip;
		return this;
	}

	public int getMinSizeToZip() {
		return minSizeToZip;
	}

	public RefererConfig setMinSizeToZip(int minSizeToZip) {
		this.minSizeToZip = minSizeToZip;
		return this;
	}

	public int getServiceId() {
		return serviceId;
	}

	public RefererConfig setServiceId(int serviceId) {
		this.serviceId = serviceId;
		return this;
	}

	public boolean isBreakerEnabled() {
		return breakerEnabled;
	}

	public RefererConfig setBreakerEnabled(boolean breakerEnabled) {
		this.breakerEnabled = breakerEnabled;
		return this;
	}

	public int getBreakerWindowSeconds() {
		return breakerWindowSeconds;
	}

	public RefererConfig setBreakerWindowSeconds(int breakerWindowSeconds) {
		this.breakerWindowSeconds = breakerWindowSeconds;
		return this;
	}

	public int getBreakerCloseRate() {
		return breakerCloseRate;
	}

	public RefererConfig setBreakerCloseRate(int breakerCloseRate) {
		this.breakerCloseRate = breakerCloseRate;
		return this;
	}

	public int getBreakerCloseBy() {
		return breakerCloseBy;
	}

	public RefererConfig setBreakerCloseBy(int breakerCloseBy) {
		this.breakerCloseBy = breakerCloseBy;
		return this;
	}

	public int getBreakerWaitSeconds() {
		return breakerWaitSeconds;
	}

	public RefererConfig setBreakerWaitSeconds(int breakerWaitSeconds) {
		this.breakerWaitSeconds = breakerWaitSeconds;
		return this;
	}

	public int getBreakerSuccMills() {
		return breakerSuccMills;
	}

	public RefererConfig setBreakerSuccMills(int breakerSuccMills) {
		this.breakerSuccMills = breakerSuccMills;
		return this;
	}
	
}
