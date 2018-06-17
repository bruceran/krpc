package krpc.rpc.dynamicroute;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import krpc.common.InitClose;
import krpc.common.Json;
import krpc.rpc.core.DynamicRouteConfig;
import krpc.rpc.core.DynamicRoutePlugin;
import krpc.rpc.core.Plugin;

public class ZooKeeperDynamicRoutePlugin  implements InitClose,DynamicRoutePlugin  {

	static Logger log = LoggerFactory.getLogger(ZooKeeperDynamicRoutePlugin.class);

	String addrs;

    int interval = 15;
    
    // create /dynamicroutes/default/100/routes.json.version 1
    // create /dynamicroutes/default/100/routes.json '{"serviceId":100,"disabled":false,"weights":[{"addr":"192.168.31.27","weight":50},{"addr":"192.168.31.28","weight":50}],"rules":[{"from":"host = 192.168.31.27","to":"host = 192.168.31.27","priority":2},{"from":"host = 192.168.31.28","to":"host = $host","priority":1}]}'
    
    CuratorFramework client;

    ConcurrentHashMap<String,String> versionCache = new ConcurrentHashMap<>();
    
    public void init() {

		client = CuratorFrameworkFactory.builder().connectString(addrs)  
		        .sessionTimeoutMs(60000)  
		        .connectionTimeoutMs(3000)  
		        .canBeReadOnly(false)  
		        .retryPolicy(new RetryOneTime(1000))  
		        .build();  
		client.start();  		
    }	
    
	public void config(String paramsStr) {

		Map<String,String> params = Plugin.defaultSplitParams(paramsStr);
		
		addrs = params.get("addrs");

		String s = params.get("intervalSeconds");
		if( !isEmpty(s) ) interval = Integer.parseInt(s);	
	}
    
    public void close() {
    	client.close();
    }    
    
 	boolean isEmpty(String s) {
 		return s == null || s.isEmpty();
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
	    	if( config == null ) {
	    		log.error("invalid routes json for service "+serviceName+", json="+json);
	    		return null;
	    	}
		} catch(Exception e) {
				log.error("cannot get routes json for service "+serviceName+", exception="+e.getMessage());
				return null;
		}    	
		
		versionCache.put(key,newVersion);
		return config;
	}
	
}

