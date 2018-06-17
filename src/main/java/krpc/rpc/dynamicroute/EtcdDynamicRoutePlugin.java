package krpc.rpc.dynamicroute;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import krpc.common.Json;
import krpc.httpclient.HttpClientReq;
import krpc.httpclient.HttpClientRes;
import krpc.rpc.core.DynamicRouteConfig;
import krpc.rpc.core.DynamicRoutePlugin;
import krpc.rpc.core.Plugin;

public class EtcdDynamicRoutePlugin extends AbstractHttpDynamicRoutePlugin implements DynamicRoutePlugin   {

	static Logger log = LoggerFactory.getLogger(EtcdDynamicRoutePlugin.class);
	
	String routesPathTemplate;
	
    int interval = 15;
	
    // curl -X PUT "http://192.168.31.144:2379/v2/keys/dynamicroutes/default/100/routes.json.version" -d  value=1
    // curl -X PUT "http://192.168.31.144:2379/v2/keys/dynamicroutes/default/100/routes.json" -d value=%7B%22serviceId%22%3A100%2C%22disabled%22%3Afalse%2C%22weights%22%3A%5B%7B%22addr%22%3A%22192.168.31.27%22%2C%22weight%22%3A50%7D%2C%7B%22addr%22%3A%22192.168.31.28%22%2C%22weight%22%3A50%7D%5D%2C%22rules%22%3A%5B%7B%22from%22%3A%22host%20%3D%20192.168.31.27%22%2C%22to%22%3A%22host%20%3D%20192.168.31.27%22%2C%22priority%22%3A2%7D%2C%7B%22from%22%3A%22host%20%3D%20192.168.31.28%22%2C%22to%22%3A%22host%20%3D%20%24host%22%2C%22priority%22%3A1%7D%5D%7D
    
    ConcurrentHashMap<String,String> versionCache = new ConcurrentHashMap<>();
        
    public void init() {
    	routesPathTemplate = "http://%s/v2/keys/dynamicroutes";
		super.init();
    }	
    
	public void config(String paramsStr) {
		Map<String,String> params = Plugin.defaultSplitParams(paramsStr);
		String s = params.get("intervalSeconds");
		if( !isEmpty(s) ) interval = Integer.parseInt(s);	
		super.config(params);
	}

    public int getRefreshIntervalSeconds() {
    	return interval;
    }
	
    public DynamicRouteConfig getConfig(int serviceId,String serviceName,String group) {

    	String path = routesPathTemplate+"/"+group+"/"+serviceId+"/routes.json";
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

		String getPath = String.format(path, addr());
		HttpClientReq req = new HttpClientReq("GET",getPath);

		HttpClientRes res = hc.call(req);
		if( res.getRetCode() != 0 || res.getHttpCode() != 200 ) {
			log.error("cannot get data "+getPath+", content="+res.getContent());
			if( res.getHttpCode() != 404 ) nextAddr();
			return null;
		} 		
		
		String json = res.getContent();
		Map<String,Object> m = Json.toMap(json);
		if( m == null ) {
			nextAddr();
			return null;
		}
		
        if( m.containsKey("errorCode") || !"get".equals(m.get("action")) ) {
        	log.error("cannot get data "+getPath+", content="+res.getContent());
        	return null;
        }

        Map node = (Map)m.get("node");
        if( node == null || node.size() == 0 ) return "";

        String value = (String)node.get("value"); 
        if( value == null ) value = "";
		return value;
	}
	
}

