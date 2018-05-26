package krpc.rpc.registry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import krpc.common.InitClose;
import krpc.common.InitCloseUtils;
import krpc.rpc.core.Registry;
import krpc.rpc.core.RegistryManager;
import krpc.rpc.core.RegistryManagerCallback;
import krpc.rpc.core.StartStop;

public class DefaultRegistryManager implements RegistryManager,InitClose,StartStop {
	
	HashMap<String,Registry> registries = new HashMap<String,Registry>();
	HashMap<Integer,String> serviceAddrs = new HashMap<Integer,String>();
	HashMap<Integer,RegistryManagerCallback> serviceCallback = new HashMap<Integer,RegistryManagerCallback>();
	
	static class Bind {
		RegistryManagerCallback callback;
		HashSet<Integer> serviceIds = new HashSet<Integer>();
		Bind(RegistryManagerCallback callback,int serviceId) {
			this.callback = callback;
			serviceIds.add(serviceId);
		}
	}
	
	ArrayList<Bind> binds = new ArrayList<Bind>();

	void addBind(RegistryManagerCallback callback,int serviceId) {
		for(Bind b:binds) {
			if( b.callback == callback) {
				b.serviceIds.add(serviceId);
				return;
			}
		}
		binds.add( new Bind(callback,serviceId) );
	}
	
	public void addRegistry(String registryName, Registry impl) {
		registries.put(registryName,impl);
	}

    public void register(int serviceId,String registryName,String group) {
    	
    }

    public void addDiscover(int serviceId,String registryName,String group,RegistryManagerCallback callback) {
    	String addrs = loadAddr(serviceId,registryName,group);
    	serviceAddrs.put(serviceId,addrs);
    	serviceCallback.put(serviceId,callback);
    	addBind(callback,serviceId);
    }
    
    public void addDirect(int serviceId,String direct,RegistryManagerCallback callback) {
    	serviceAddrs.put(serviceId,direct);
    	serviceCallback.put(serviceId,callback);
    	addBind(callback,serviceId);
    }

    public void init() {
		for(Registry r:registries.values()) {
			InitCloseUtils.init(r);
		}	
		
		notifyAddrChanged();
    }
    
    public void start() {
		for(Registry r:registries.values()) {
			InitCloseUtils.start(r);
		}		    	
    }
    
    public void stop() {
		for(Registry r:registries.values()) {
			InitCloseUtils.stop(r);
		}		    	
    }
    
    public void close() {
		for(Registry r:registries.values()) {
			InitCloseUtils.close(r);
		}		    	
    }
    
    void notifyAddrChanged() {
    	for(Bind b:binds) {
        	HashMap<Integer,String> results = new HashMap<Integer,String>();
    		for(int serviceId:b.serviceIds) {
    			results.put(serviceId, serviceAddrs.get(serviceId));
    		}
    		b.callback.addrChanged(results);
    	}
    }
    
    String loadAddr(int serviceId,String registryName,String group) {
    	return ""; // todo
    }
}

