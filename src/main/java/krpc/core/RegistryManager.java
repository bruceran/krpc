package krpc.core;

public interface RegistryManager {

	void addRegistry(String registryName, Registry impl);
	
	// for service
    void register(int serviceId,String registryName,String group);

    // for referer
    void addDiscover(int serviceId,String registryName,String group,RegistryManagerCallback callback);
    void addDirect(int serviceId,String direct,RegistryManagerCallback callback);
}

