package krpc.rpc.registry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import krpc.common.Json;
import krpc.rpc.core.DynamicRouteConfig;
import krpc.rpc.core.DynamicRoutePlugin;
import krpc.rpc.core.Plugin;

public class ZooKeeperRegistry extends AbstractHttpRegistry implements DynamicRoutePlugin  {

	static Logger log = LoggerFactory.getLogger(ZooKeeperRegistry.class);

    int interval = 15;
    
    CuratorFramework client;

    ConcurrentHashMap<String,String> versionCache = new ConcurrentHashMap<>();
    
    public void init() {
		super.init();
		
		client = CuratorFrameworkFactory.builder().connectString(addrs)  
		        .sessionTimeoutMs(60000)  
		        .connectionTimeoutMs(5000)  
		        .canBeReadOnly(false)  
		        .retryPolicy(new RetryOneTime(1000))  
		        .build();  
		client.start();  		
    }	
    
	public void config(String paramsStr) {
		Map<String,String> params = Plugin.defaultSplitParams(paramsStr);
		String s = params.get("pingSeconds");
		if( !isEmpty(s) ) interval = Integer.parseInt(s);	
		super.config(params);
	}
    
    public void close() {
    	client.close();
		super.close();
    }    
    
    public int getCheckIntervalSeconds() {
    	return interval;
    }
    
    public int getRefreshIntervalSeconds() {
    	return interval;
    }
	
    public DynamicRouteConfig getConfig(int serviceId,String serviceName,String group) {
    	
    	String path = "/dynamicroutes/"+group+"/"+serviceId+"/routes.json";
    	String versionPath = path+".version";
    	
    	String key = serviceId + "." + group;
    	String oldVersion = versionCache.get(key);
    	String newVersion = null;
    	
		try {
			byte[] bytes = client.getData().forPath(versionPath);
			if( bytes == null ) return null;
			newVersion = new String(bytes);
		} catch(Exception e) {
				log.error("cannot get routes json version for service "+serviceName+", exception="+e.getMessage());
				return null;
		}    	
		if( oldVersion != null && newVersion != null && oldVersion.equals(newVersion) ) {
			return null; // no change
		}
		
		DynamicRouteConfig config = null;
		
		try {
			byte[] bytes = client.getData().forPath(path);
			if( bytes == null ) return null;
	    	String json = new String(bytes);
	    	config = Json.toObject(json,DynamicRouteConfig.class);			
	    	if( config == null ) return null;
		} catch(Exception e) {
				log.error("cannot get routes json for service "+serviceName+", exception="+e.getMessage());
				return null;
		}    	
		
		versionCache.put(key,newVersion);
		return config;
	}
	
	public void register(int serviceId,String serviceName,String group,String addr) {
		String instanceId = addr ;
		String path = "/services/"+group+"/"+serviceId+"/"+instanceId;
		
		HashMap<String,Object> meta = new HashMap<>();
		meta.put("addr", addr);
		meta.put("group", group);
		meta.put("serviceName", serviceName);
		String data = Json.toJson(meta);
		
		try {
			client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath(path, data.getBytes());
		} catch(Exception e) {
			if( e.getMessage().indexOf("NodeExists") < 0 ) {
				log.error("cannot register service "+serviceName+", exception="+e.getMessage());
			}
		}
	}
	public void deregister(int serviceId,String serviceName,String group,String addr) {
		
		String instanceId = addr;
		String path = "/services/"+group+"/"+serviceId+"/"+instanceId;
		
		try {
			client.delete().forPath(path);
		} catch(Exception e) {
			log.error("cannot deregister service "+serviceName+", exception="+e.getMessage());
		}

	}	
	
	public String discover(int serviceId,String serviceName,String group) {	
		String path = "/services/"+group+"/"+serviceId;
		
		List<String> list = null;
		
		try {
			list = client.getChildren().forPath(path);
		} catch(Exception e) {
			log.error("cannot discover service "+serviceName+", exception="+e.getMessage());
			return null;
		}
		
		TreeSet<String> set = new TreeSet<>();
		for(String  addr:list) {
			set.add(addr);
		}
		
		StringBuilder b = new StringBuilder();
		for(String key: set) {
			if( b.length() > 0 ) b.append(",");
			b.append(key);
		}
		String s = b.toString();
		return s;
	}	
	
}

