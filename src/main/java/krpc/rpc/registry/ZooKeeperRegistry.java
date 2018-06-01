package krpc.rpc.registry;

import krpc.rpc.core.Registry;

public class ZooKeeperRegistry implements Registry {

	public void config(String paramsStr) {
		
	}

	public void register(int serviceId,String serviceName,String group,String addr) {
		
	}
	public void deregister(int serviceId,String serviceName,String group) {
		
	}	
	
	public String discover(int serviceId,String serviceName,String group) {	
		return "";
	}	
	
}

