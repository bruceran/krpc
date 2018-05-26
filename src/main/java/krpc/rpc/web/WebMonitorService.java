package krpc.rpc.web;

import krpc.rpc.core.MonitorService;

public interface WebMonitorService extends MonitorService {
	void webReqStart(WebClosure closure);
	void webReqDone(WebClosure closure);
}
