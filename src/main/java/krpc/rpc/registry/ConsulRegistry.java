package krpc.rpc.registry;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import krpc.common.Json;
import krpc.httpclient.HttpClientReq;
import krpc.httpclient.HttpClientRes;

public class ConsulRegistry extends AbstractHttpRegistry {

	static Logger log = LoggerFactory.getLogger(ConsulRegistry.class);
	
	String registerUrl;
	String keepAliveUrl;
	String degisterUrl;
	String discoverUrl;

	HashSet<String> registeredServiceNames = new HashSet<>();
	
    public void init() {
    	registerUrl = "http://"+addrs+"/v1/agent/service/register";
    	keepAliveUrl = "http://"+addrs+"/v1/agent/check/pass/service:%s";
    	degisterUrl = "http://"+addrs+"/v1/agent/service/deregister";
    	discoverUrl = "http://"+addrs+"/v1/health/service/%s?passing";
		super.init();
    }	
    
	public void register(int serviceId,String serviceName,String group,String addr) {
		if( !enableRegist ) return;

		if( registeredServiceNames.contains(serviceName) ) {
			String url = String.format(keepAliveUrl, serviceName);
			HttpClientReq req = new HttpClientReq("PUT",url);

			HttpClientRes res = hc.call(req);
			if( res.getRetCode() != 0 || res.getHttpCode() != 200 ) {
				log.error("cannot call keep alive url service "+serviceName);
			} else {
				registeredServiceNames.remove(serviceName);
			}
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
		check.put("DeregisterCriticalServiceAfter", "1m");
		check.put("TTL", "15s");
		m.put("Check", check);
		
		String json = Json.toJson(m);
		HttpClientReq req = new HttpClientReq("PUT",registerUrl).setContent(json);

		HttpClientRes res = hc.call(req);
		if( res.getRetCode() != 0 || res.getHttpCode() != 200 ) {
			log.error("cannot register service "+serviceName+", content="+res.getContent());
			return;
		} 
		
		registeredServiceNames.add(serviceName);
	}
	
	public void deregister(int serviceId,String serviceName,String group) {
		if( !enableRegist ) return;
		
		String url = degisterUrl + "/" + serviceName;
		HttpClientReq req = new HttpClientReq("PUT",url);
		HttpClientRes res = hc.call(req);
		if( res.getRetCode() != 0 || res.getHttpCode() != 200 ) {
			log.error("cannot deregister service "+serviceName+", content="+res.getContent());
		}
		
		registeredServiceNames.remove(serviceName);
	}	
	
	@SuppressWarnings("unchecked")
	public String discover(int serviceId,String serviceName,String group) {	
		if( !enableDiscover ) return null;
		String url = String.format(discoverUrl, serviceName);
		HttpClientReq req = new HttpClientReq("GET",url);
		HttpClientRes res = hc.call(req);
		if( res.getRetCode() != 0 || res.getHttpCode() != 200 ) {
			log.error("cannot discover service "+serviceName);
			return null;
		}
		
		String json = res.getContent();
		List<Object> list = Json.toList(json);
		if( list == null ) return null;
		
		TreeMap<String,String> set = new TreeMap<>();
		for(Object o:list) {
			Map<String,Object> m = (Map<String,Object>)o;
			Map<String,Object> service = (Map<String,Object>)m.get("Service");
			List<String> tags = (List<String>)service.get("Tags");
			if( !tags.contains(group) ) continue;
			String address = (String)service.get("Address");
			int port = (Integer)service.get("Port");
			set.put(address+":"+port,"1");
		}
		
		StringBuilder b = new StringBuilder();
		for(Map.Entry<String, String> entry: set.entrySet()) {
			if( b.length() > 0 ) b.append(",");
			b.append(entry.getKey());
		}
		String s = b.toString();
		return s;
	}
	
}


