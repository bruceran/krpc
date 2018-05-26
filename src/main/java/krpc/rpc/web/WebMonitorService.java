package krpc.rpc.web;

import krpc.rpc.core.MonitorService;

public interface WebMonitorService extends MonitorService {
	void webReqDone(WebClosure closure);
}
