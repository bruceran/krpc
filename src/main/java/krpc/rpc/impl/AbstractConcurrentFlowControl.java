package krpc.rpc.impl;

import java.util.Map;

abstract  public class AbstractConcurrentFlowControl  {

	void configLimit(Map<String,String> params) {
		for(Map.Entry<String, String> entry: params.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			if( key.startsWith("service.")) {
				String[] keys = key.split("\\.");
				int serviceId = Integer.parseInt( keys[1] );
				int limit =Integer.parseInt( value );
				addLimit(serviceId,limit);
			}
			if( key.startsWith("msg.")) {
				String[] keys = key.split("\\.");
				int serviceId = Integer.parseInt( keys[1] );
				int limit =Integer.parseInt( value );
				String[] mm = keys[2].split("#");
				for(String m:mm) {
					int msgId = Integer.parseInt(m);
					addLimit(serviceId,msgId,limit);
				}
			}			
		}
	}

	abstract public  void addLimit(int serviceId,int limit);
	abstract public  void addLimit(int serviceId,int msgId,int limit);
}

