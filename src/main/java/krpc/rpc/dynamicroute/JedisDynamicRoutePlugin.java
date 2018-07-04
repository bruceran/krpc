package krpc.rpc.dynamicroute;

import java.util.HashSet;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import krpc.common.InitClose;
import krpc.common.Json;
import krpc.common.Plugin;
import krpc.rpc.core.DynamicRouteConfig;
import krpc.rpc.core.DynamicRoutePlugin;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPool;

public class JedisDynamicRoutePlugin  implements InitClose,DynamicRoutePlugin  {

	static Logger log = LoggerFactory.getLogger(JedisDynamicRoutePlugin.class);

	String addrs;

    int interval = 15;

	private JedisPool jedisPool;
	private JedisCluster jedisCluster;
	private boolean clusterMode = false;
	
    ConcurrentHashMap<String,String> versionCache = new ConcurrentHashMap<>();
    
    // set dynamicroutes.default.100.routes.json.version 1
    // set dynamicroutes.default.100.routes.json '{"serviceId":100,"disabled":false,"weights":[{"addr":"192.168.31.27","weight":50},{"addr":"192.168.31.28","weight":50}],"rules":[{"from":"host = 192.168.31.27","to":"host = 192.168.31.27","priority":2},{"from":"host = 192.168.31.28","to":"host = $host","priority":1}]}'
    
    public void init() {

		if(!clusterMode) {
			String[] ss = addrs.split(":");
			jedisPool = new JedisPool(ss[0],Integer.parseInt(ss[1]));
		} else {
			Set<HostAndPort> hosts = new HashSet<>();
			String[] ss =  addrs.split(",");
			for(String s : ss ) {
				String[] tt = s.split(":");
				hosts.add(new HostAndPort(tt[0],Integer.parseInt(tt[1])));
			}
			try {
				jedisCluster = new JedisCluster(hosts);
			} catch(Exception e) {
				throw new RuntimeException("cannot init jedis cluster",e);
			}
		}
    }	
    
	public void config(String paramsStr) {

		Map<String,String> params = Plugin.defaultSplitParams(paramsStr);
		
		addrs = params.get("addrs");
		String s = params.get("intervalSeconds");
		if( !isEmpty(s) ) interval = Integer.parseInt(s);	
		
		s = params.get("clusterMode");
		if ( s != null && !s.isEmpty() )
			clusterMode = Boolean.parseBoolean(s);				
		
	}
    
    public void close() {
		if(!clusterMode) {
			jedisPool.close();
		} else {
			try {
				jedisCluster.close();
			} catch(Exception e) {
				log.error("close cluster exception, e="+e.getMessage());
			}
		}
    }    
    
 	boolean isEmpty(String s) {
 		return s == null || s.isEmpty();
 	}    
    
    public int getRefreshIntervalSeconds() {
    	return interval;
    }
	
    public DynamicRouteConfig getConfig(int serviceId,String serviceName,String group) {
    	 
    	String path = "dynamicroutes."+group+"."+serviceId+".routes.json";
    	String versionPath = path+".version";
    	
    	String key = serviceId + "." + group;
    	String oldVersion = versionCache.get(key);
    	String newVersion = null;
    	
		if(!clusterMode) {
	        Jedis jedis = null;  
	        try {  
	            jedis = jedisPool.getResource();  
	            newVersion = jedis.get(versionPath);
	            if( newVersion == null ) return null;
	        } catch (Exception e) {  
	        	log.error("cannot get routes json version for service "+serviceName+", exception="+e.getMessage());
	            return null;
	        } finally {  
	        	try {
	        		if( jedis != null )
	        			jedis.close();
	        	} catch(Exception e) {
	        	}
	        }  
		} else {
			try {  
				newVersion = jedisCluster.get(versionPath);
				if( newVersion == null ) return null;
			} catch (Exception e) {  
				log.error("cannot get routes json version for service "+serviceName+", exception="+e.getMessage());
	            return null;
	        }
		}
		
		if( oldVersion != null && newVersion != null && oldVersion.equals(newVersion) ) {
			return null; // no change
		}
		
		DynamicRouteConfig config = null;
		String json = null;
		
		if(!clusterMode) {
	        Jedis jedis = null;  
	        try {  
	            jedis = jedisPool.getResource();  
	            json = jedis.get(path);
	        } catch (Exception e) {  
	        	log.error("cannot get routes json for service "+serviceName+", exception="+e.getMessage());
	            return null;
	        } finally {  
	        	try {
	        		if( jedis != null )
	        			jedis.close();
	        	} catch(Exception e) {
	        	}
	        }  
		} else {
			try {  
				json = jedisCluster.get(path);
			} catch (Exception e) {  
				log.error("cannot get routes json for service "+serviceName+", exception="+e.getMessage());
	            return null;
	        }
		}
		
    	config = Json.toObject(json,DynamicRouteConfig.class);			
    	if( config == null ) {
    		log.error("invalid routes json for service "+serviceName+", json="+json);
    		return null;
    	}

		versionCache.put(key,newVersion);
		return config;

	}
	
}

