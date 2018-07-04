package krpc.rpc.registry;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import krpc.common.Json;
import krpc.common.Plugin;
import krpc.httpclient.HttpClientReq;
import krpc.httpclient.HttpClientRes;
import krpc.rpc.core.DynamicRouteConfig;
import krpc.rpc.core.DynamicRoutePlugin;

public class ConsulRegistry extends AbstractHttpRegistry implements DynamicRoutePlugin  {

	static Logger log = LoggerFactory.getLogger(ConsulRegistry.class);
	
	String registerUrlTemplate;
	String keepAliveUrlTemplate;
	String degisterUrlTemplate;
	String discoverUrlTemplate;
	
	String routesUrlTemplate;

	int ttl = 90;
	int interval = 15;
	
	HashSet<Integer> registeredServiceIds = new HashSet<>();
	
	// curl "http://192.168.31.144:8500/v1/agent/services"
	// curl "http://192.168.31.144:8500/v1/catalog/services"
	// curl "http://192.168.31.144:8500/v1/health/service/100"
	// curl -X PUT http://192.168.31.144:8500/v1/kv/dynamicroutes/default/100/routes.json.version -d 1
	// curl -X PUT http://192.168.31.144:8500/v1/kv/dynamicroutes/default/100/routes.json -d '{"serviceId":100,"disabled":false,"weights":[{"addr":"192.168.31.27","weight":50},{"addr":"192.168.31.28","weight":50}],"rules":[{"from":"host = 192.168.31.27","to":"host = 192.168.31.27","priority":2},{"from":"host = 192.168.31.28","to":"host = $host","priority":1}]}'
	
    ConcurrentHashMap<String,String> versionCache = new ConcurrentHashMap<>();
    	
    public void init() {
    	registerUrlTemplate = "http://%s/v1/agent/service/register";
    	keepAliveUrlTemplate = "http://%s/v1/agent/check/pass/service:%s";
    	degisterUrlTemplate = "http://%s/v1/agent/service/deregister";
    	discoverUrlTemplate = "http://%s/v1/health/service/%d?passing";
    	routesUrlTemplate = "http://%s/v1/kv/dynamicroutes";
		super.init();
    }	

	public void config(String paramsStr) {
		Map<String,String> params = Plugin.defaultSplitParams(paramsStr);
		String s = params.get("ttlSeconds");
		if( !isEmpty(s) ) ttl = Integer.parseInt(s);	
		s = params.get("intervalSeconds");
		if( !isEmpty(s) ) interval = Integer.parseInt(s);	
		
		super.config(params);
	}
    
    public int getCheckIntervalSeconds() {
    	return interval;
    }
    
    public int getRefreshIntervalSeconds() {
    	return interval;
    }
	
    public DynamicRouteConfig getConfig(int serviceId,String serviceName,String group) {

    	String path = routesUrlTemplate+"/"+group+"/"+serviceId+"/routes.json";
    	String versionPath = path+".version";
    	
    	String key = serviceId + "." + group;
    	String oldVersion = versionCache.get(key);
    	
    	String newVersion = getData(versionPath);
    	if( newVersion == null || newVersion.isEmpty() ) {
			log.error("cannot get routes json version for service "+serviceName);
			return null;
    	}    	

		if( oldVersion != null && newVersion != null && oldVersion.equals(newVersion) ) {
			return null; // no change
		}
		
		String json = getData(path);
    	if( json == null  || json.isEmpty()  ) {
			log.error("cannot get routes json for service "+serviceName);
			return null;
    	}
    	
    	DynamicRouteConfig config = Json.toObject(json,DynamicRouteConfig.class);			
    	if( config == null ) return null;
    	
		versionCache.put(key,newVersion);
		return config;
	}

	public String getData(String path) {	

		if( hc == null ) return null;
		
		String url = String.format(path, addr());
		url += "?raw"; // return json only
		HttpClientReq req = new HttpClientReq("GET",url);
		HttpClientRes res = hc.call(req);
		if( res.getRetCode() != 0 || res.getHttpCode() != 200 ) {
			log.error("cannot get config "+path);
			if( res.getHttpCode() != 404 ) nextAddr();
			return null;
		}
		
		String data = res.getContent();
		return data;
	}
	
	public void register(int serviceId,String serviceName,String group,String addr) {
		if( !enableRegist ) return;
		if( hc == null ) return;
	
		String instanceId = addr;
		
		if( registeredServiceIds.contains(serviceId) ) {
			String url = String.format(keepAliveUrlTemplate, addr(), instanceId);
			HttpClientReq req = new HttpClientReq("PUT",url);

			HttpClientRes res = hc.call(req);
			if( res.getRetCode() != 0 || res.getHttpCode() != 200 ) {
				log.error("cannot call keep alive url service "+serviceName);
				nextAddr();
				return;
			} 
			registeredServiceIds.remove(serviceId);
			return;
		}
		
		HashMap<String,Object> m = new HashMap<>();
		m.put("ID", instanceId);
		m.put("Name", String.valueOf(serviceId));
		
		HashMap<String,Object> meta = new HashMap<>();
		meta.put("group", group);
		meta.put("serviceName", serviceName);
		m.put("Meta", meta);
		
		m.put("Tags", Arrays.asList(serviceName)); //  displayed in ui;
		
		int p = addr.lastIndexOf(":");
		String address = addr.substring(0,p);
		int port = Integer.parseInt( addr.substring(p+1) );
		
		m.put("Address", address);
		m.put("Port", port);
		HashMap<String,Object> check = new HashMap<>();
		check.put("Name", "check_"+serviceId);
		check.put("Status", "passing");
		check.put("DeregisterCriticalServiceAfter", "3m");
		check.put("TTL", ttl+"s");
		m.put("Check", check);
		
		String json = Json.toJson(m);
		String url = String.format(registerUrlTemplate, addr());
		HttpClientReq req = new HttpClientReq("PUT",url).setContent(json);

		HttpClientRes res = hc.call(req);
		if( res.getRetCode() != 0 || res.getHttpCode() != 200 ) {
			log.error("cannot register service "+serviceName+", content="+res.getContent());
			nextAddr();
			return;
		} 
		
		registeredServiceIds.add(serviceId);
	}
	
	public void deregister(int serviceId,String serviceName,String group,String addr) {
		if( !enableRegist ) return;
		if( hc == null ) return;
		
		String instanceId = addr;
		
		String url = String.format(degisterUrlTemplate, addr()) + "/" + instanceId;
		HttpClientReq req = new HttpClientReq("PUT",url);
		HttpClientRes res = hc.call(req);
		if( res.getRetCode() != 0 || res.getHttpCode() != 200 ) {
			log.error("cannot deregister service "+serviceName+", content="+res.getContent());
			nextAddr();
			return;
		}
		
		registeredServiceIds.remove(serviceId);
	}	
	
	@SuppressWarnings("unchecked")
	public String discover(int serviceId,String serviceName,String group) {	
		if( !enableDiscover ) return null;
		if( hc == null ) return null;
		
		String url = String.format(discoverUrlTemplate, addr(), serviceId);
		HttpClientReq req = new HttpClientReq("GET",url);
		HttpClientRes res = hc.call(req);
		if( res.getRetCode() != 0 || res.getHttpCode() != 200 ) {
			log.error("cannot discover service "+serviceName);
			nextAddr();
			return null;
		}
		
		String json = res.getContent();
		List<Object> list = Json.toList(json);
		if( list == null ) {
			nextAddr();
			return null;
		}
		
		TreeSet<String> set = new TreeSet<>();
		for(Object o:list) {
			Map<String,Object> m = (Map<String,Object>)o;
			Map<String,Object> service = (Map<String,Object>)m.get("Service");
			Map<String,String> meta = (Map<String,String>)service.get("Meta");
			if( !meta.containsKey("group") ) continue;
			if( !meta.get("group").equals(group) ) continue;
			String address = (String)service.get("Address");
			int port = (Integer)service.get("Port");
			set.add(address+":"+port);
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


