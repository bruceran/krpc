package krpc.rpc.registry;

import java.util.Map;

import krpc.common.InitClose;
import krpc.httpclient.DefaultHttpClient;
import krpc.rpc.core.Plugin;
import krpc.rpc.core.Registry;

abstract public class AbstractHttpRegistry implements Registry,InitClose {

	String instanceId;
	String addrs;
	boolean enableRegist = true;
	boolean enableDiscover = true;
	
	DefaultHttpClient hc;	
	
	public void config(String paramsStr) {

		Map<String,String> params = Plugin.defaultSplitParams(paramsStr);
		
		instanceId = params.get("instanceId");
		addrs = params.get("addrs");
		
		String s = params.get("enableRegist");
		if( !isEmpty(s) ) enableRegist = Boolean.parseBoolean(s);	

		s = params.get("enableDiscover");
		if( !isEmpty(s) ) enableDiscover = Boolean.parseBoolean(s);	
	}

    public void init() {
		hc = new DefaultHttpClient();
		hc.init();
    }	
    
    public void close() {
		if( hc == null ) return;
		hc.close();
		hc = null;
    }    
    
	boolean isEmpty(String s) {
		return s == null || s.isEmpty();
	}    

}

