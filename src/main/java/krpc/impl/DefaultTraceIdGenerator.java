package krpc.impl;

import java.util.UUID;

import krpc.core.RpcServerContextData;
import krpc.core.TraceIdGenerator;

public class DefaultTraceIdGenerator implements TraceIdGenerator {

    public String nextId(RpcServerContextData data) {
    	if( data == null ) {
            return nextId();
    	} else {
    		return data.getMeta().getTraceId()+":"+data.nextCall();
    	}
    }

    private String nextId() {
        String s = UUID.randomUUID().toString();
        return s.replaceAll("-", "")+":1";
    }
}


