package krpc.rpc.bootstrap;

public class RegistryConfig  {

	String id;
	
	String type; // typeName(etcd,zookeeper,...), must be registered in Bootstrap.registryTypes
	String addrs;

	// todo enableRegist, enableDiscover, checkAliveOnStart
	
	public RegistryConfig() {
	}

	public RegistryConfig(String id) {
		this.id = id;
	}

	public String getId() {
		return id;
	}

	public RegistryConfig setId(String id) {
		this.id = id;
		return this;
	}

	public String getType() {
		return type;
	}

	public RegistryConfig setType(String type) {
		this.type = type;
		return this;
	}

	public String getAddrs() {
		return addrs;
	}

	public RegistryConfig setAddrs(String addrs) {
		this.addrs = addrs;
		return this;
	}
	
}
