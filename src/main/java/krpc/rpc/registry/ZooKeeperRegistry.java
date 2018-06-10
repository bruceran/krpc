package krpc.rpc.registry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import krpc.common.Json;
import krpc.rpc.core.Plugin;

public class ZooKeeperRegistry extends AbstractHttpRegistry  {

	static Logger log = LoggerFactory.getLogger(ZooKeeperRegistry.class);

    int interval = 15;
    
    CuratorFramework client;

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

