package krpc.monitorserver;

import krpc.rpc.monitor.proto.ReportRpcStatReq;
import krpc.rpc.monitor.proto.ReportRpcStatRes;

public interface MonitorService {
    static int serviceId = 2;

    ReportRpcStatRes reportRpcStat(ReportRpcStatReq req);

    static int reportRpcStatMsgId = 1;
}
