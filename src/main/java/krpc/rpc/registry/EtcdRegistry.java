package krpc.rpc.registry;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import krpc.common.Json;
import krpc.httpclient.HttpClientReq;
import krpc.httpclient.HttpClientRes;
import krpc.rpc.core.Plugin;

public class EtcdRegistry extends AbstractHttpRegistry {

	static Logger log = LoggerFactory.getLogger(EtcdRegistry.class);
	
	String basePath;
	
    int ttl = 90;
	
    public void init() {
    	basePath = "http://"+addrs+"/v2/keys/services";
		super.init();
    }	
    
	public void config(String paramsStr) {
		Map<String,String> params = Plugin.defaultSplitParams(paramsStr);
		String s = params.get("ttl");
		if( !isEmpty(s) ) ttl = Integer.parseInt(s);	
		
		super.config(params);
	}
	
	public void register(int serviceId,String serviceName,String group,String addr) {
		if( !enableRegist ) return;
		String url = basePath +"/"+group+"/"+serviceName+"/"+instanceId +"?ttl="+ttl;		
        String value = "value="+addr;
		HttpClientReq req = new HttpClientReq("PUT",url).addHeader("content-type", "application/x-www-form-urlencoded").setContent(value);

		HttpClientRes res = hc.call(req);
		if( res.getRetCode() != 0 || res.getHttpCode() != 201 && res.getHttpCode() != 200 ) {
			log.error("cannot register service "+serviceName+", content="+res.getContent());
			return;
		} 		
		
		String json = res.getContent();
		Map<String,Object> m = Json.toMap(json);
		if( m == null ) return;
		
        if( m.containsKey("errorCode") || !"set".equals(m.get("action")) ) {
        	log.error("cannot register service "+serviceName+", content="+res.getContent());
        }		
	}
	
	public void deregister(int serviceId,String serviceName,String group) {
		String url = basePath +"/"+group+"/"+serviceName+"/"+instanceId;		
		HttpClientReq req = new HttpClientReq("DELETE",url);

		HttpClientRes res = hc.call(req);
		if( res.getRetCode() != 0 || res.getHttpCode() != 200 ) {
			log.error("cannot deregister service "+serviceName+", content="+res.getContent());
			return;
		} 		
		
		String json = res.getContent();
		Map<String,Object> m = Json.toMap(json);
		if( m == null ) return;
		
        if( m.containsKey("errorCode") || !"delete".equals(m.get("action")) ) {
        	log.error("cannot deregister service "+serviceName+", content="+res.getContent());
        }
	}	
	
	@SuppressWarnings("rawtypes")
	public String discover(int serviceId,String serviceName,String group) {	
		if( !enableDiscover ) return null;
		String url = basePath +"/"+group+"/"+serviceName;		
		HttpClientReq req = new HttpClientReq("GET",url);

		HttpClientRes res = hc.call(req);
		if( res.getRetCode() != 0 || res.getHttpCode() != 200 ) {
			log.error("cannot discover service "+serviceName+", content="+res.getContent());
			return null;
		} 		
		
		String json = res.getContent();
		Map<String,Object> m = Json.toMap(json);
		if( m == null ) return null;
		
        if( m.containsKey("errorCode") || !"get".equals(m.get("action")) ) {
        	log.error("cannot discover service "+serviceName+", content="+res.getContent());
        	return null;
        }
        
        TreeMap<String,String> set = new TreeMap<>();
        
        Map node = (Map)m.get("node");
        if( node == null || node.size() == 0 ) return "";
        List nodelist = (List)node.get("nodes");
        if( nodelist == null || nodelist.size() == 0 ) return "";

        for( Object o : nodelist ) {
        	if( o instanceof Map ) {
        		Map mm = (Map)o;
            	if(mm != null) {
            		set.put((String)mm.get("value"),"1");
            	}
        	}
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

