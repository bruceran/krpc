package krpc.web;

import krpc.core.MonitorService;

public interface WebMonitorService extends MonitorService {
    void webReqDone(WebClosure closure);
}
