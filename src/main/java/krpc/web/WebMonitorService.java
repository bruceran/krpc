package krpc.web;

import krpc.core.MonitorService;

public interface WebMonitorService extends MonitorService {
	void webReqStart(WebClosure closure);
	void webReqDone(WebClosure closure);
}
