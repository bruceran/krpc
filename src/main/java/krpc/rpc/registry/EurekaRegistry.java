package krpc.rpc.registry;

import krpc.rpc.core.Registry;

public class EurekaRegistry implements Registry {

	public void config(String paramsStr) {
		
	}
    
    public int getCheckIntervalSeconds() {
    	return 1;
    }
    
	public void register(int serviceId,String serviceName,String group,String addr) {
		
	}
	public void deregister(int serviceId,String serviceName,String group,String addr) {
		
	}	
	
	public String discover(int serviceId,String serviceName,String group) {	
		return "";
	}	
	
}

