package krpc.impl;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import krpc.core.RpcServerContextData;
import krpc.core.TraceIdGenerator;

public class ZipkinTraceIdGenerator implements TraceIdGenerator {

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
		
		if (data == null || data.getMeta().getSpanId().isEmpty() ) {
			if( isServer) return "0";
			else return "0:"+nextInt();
		}

		String spanId = data.getMeta().getSpanId();
		int p = spanId.indexOf(":");
		return spanId.substring(p+1) + ":" + nextInt();
	}

	private int nextInt() {
		for(;;) {
			int v = t.nextInt();
			if( v > 0 ) return v;
		}
	}
}


