package krpc.rpc.registry;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import krpc.common.Json;
import krpc.httpclient.HttpClientReq;
import krpc.httpclient.HttpClientRes;
import krpc.rpc.core.Plugin;

public class ConsulRegistry extends AbstractHttpRegistry {

	static Logger log = LoggerFactory.getLogger(ConsulRegistry.class);
	
	String registerUrlTemplate;
	String keepAliveUrlTemplate;
	String degisterUrlTemplate;
	String discoverUrlTemplate;

	int ttl = 90;
	int interval = 15;
	
	HashSet<String> registeredServiceNames = new HashSet<>();
	
    public void init() {
    	registerUrlTemplate = "http://%s/v1/agent/service/register";
    	keepAliveUrlTemplate = "http://%s/v1/agent/check/pass/service:%s";
    	degisterUrlTemplate = "http://%s/v1/agent/service/deregister";
    	discoverUrlTemplate = "http://%s/v1/health/service/%s?passing";
		super.init();
    }	

	public void config(String paramsStr) {
		Map<String,String> params = Plugin.defaultSplitParams(paramsStr);
		String s = params.get("ttlSeconds");
		if( !isEmpty(s) ) ttl = Integer.parseInt(s);	
		s = params.get("pingSeconds");
		if( !isEmpty(s) ) interval = Integer.parseInt(s);	
		
		super.config(params);
	}
    
    public int getCheckIntervalSeconds() {
    	return interval;
    }
    
	public void register(int serviceId,String serviceName,String group,String addr) {
		if( !enableRegist ) return;
		if( hc == null ) return;
	
		if( registeredServiceNames.contains(serviceName) ) {
			String url = String.format(keepAliveUrlTemplate, addr(), serviceName);
			HttpClientReq req = new HttpClientReq("PUT",url);

			HttpClientRes res = hc.call(req);
			if( res.getRetCode() != 0 || res.getHttpCode() != 200 ) {
				log.error("cannot call keep alive url service "+serviceName);
				nextAddr();
				return;
			} 
			registeredServiceNames.remove(serviceName);
			return;
		}
		
		HashMap<String,Object> m = new HashMap<>();
		m.put("ID", serviceName);
		m.put("Name", serviceName);
		m.put("Tags", Arrays.asList(group));
		
		int p = addr.lastIndexOf(":");
		String address = addr.substring(0,p);
		int port = Integer.parseInt( addr.substring(p+1) );
		
		m.put("Address", address);
		m.put("Port", port);
		HashMap<String,Object> check = new HashMap<>();
		check.put("Name", "check "+serviceName);
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
		
		registeredServiceNames.add(serviceName);
	}
	
	public void deregister(int serviceId,String serviceName,String group) {
		if( !enableRegist ) return;
		if( hc == null ) return;
		
		String url = String.format(degisterUrlTemplate, addr()) + "/" + serviceName;
		HttpClientReq req = new HttpClientReq("PUT",url);
		HttpClientRes res = hc.call(req);
		if( res.getRetCode() != 0 || res.getHttpCode() != 200 ) {
			log.error("cannot deregister service "+serviceName+", content="+res.getContent());
			nextAddr();
			return;
		}
		
		registeredServiceNames.remove(serviceName);
	}	
	
	@SuppressWarnings("unchecked")
	public String discover(int serviceId,String serviceName,String group) {	
		if( !enableDiscover ) return null;
		if( hc == null ) return null;
		
		String url = String.format(discoverUrlTemplate, addr(), serviceName);
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
			List<String> tags = (List<String>)service.get("Tags");
			if( !tags.contains(group) ) continue;
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


