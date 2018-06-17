package krpc.rpc.monitor;
 
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import krpc.rpc.core.RpcClosure;
import krpc.rpc.web.WebClosure;

public class LogOnlyMonitorPlugin implements MonitorPlugin {
	
	static Logger log = LoggerFactory.getLogger(LogOnlyMonitorPlugin.class);
	
    public void rpcReqDone(RpcClosure closure) {
    	log.info("rpcReqDone called, serviceId="+closure.getCtx().getMeta().getServiceId()+",msgId="+closure.getCtx().getMeta().getMsgId());
    }

    public void rpcCallDone(RpcClosure closure) {
    	log.info("rpcCallDone called, serviceId="+closure.getCtx().getMeta().getServiceId()+",msgId="+closure.getCtx().getMeta().getMsgId());
    }
    
    public void webReqDone(WebClosure closure) {
    	log.info("rpcCallDone called, serviceId="+closure.getCtx().getMeta().getServiceId()+",msgId="+closure.getCtx().getMeta().getMsgId());
    }
}
