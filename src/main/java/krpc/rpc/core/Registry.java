package krpc.rpc.core;

public interface Registry extends Plugin {
	
	int getCheckIntervalSeconds();
	
	void register(int serviceId,String serviceName,String group,String addr);
	void deregister(int serviceId,String serviceName,String group,String addr);
	String discover(int serviceId,String serviceName,String group);
}
