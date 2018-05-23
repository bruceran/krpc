package krpc.impl;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import krpc.core.RpcServerContextData;
import krpc.core.TraceIdGenerator;

public class EagleTraceIdGenerator implements TraceIdGenerator {

	ThreadLocalRandom t = ThreadLocalRandom.current();

	public String nextTraceId(RpcServerContextData data) {
		if( data == null ) {
	        String s = UUID.randomUUID().toString();
	        return s.replaceAll("-", "");		
		} else {
			return data.getMeta().getTraceId();
		}
	}
	
	public String nextSpanId(RpcServerContextData data,boolean isServer) {
		if (data == null ||  data.getMeta().getSpanId().isEmpty()) {
			return isServer ? "0" : "0.1";
		}
		
		return data.getMeta().getSpanId() + "." + data.nextCall();
	}

}


