package krpc.monitorserver;

import com.google.protobuf.TextFormat;
import krpc.rpc.monitor.proto.ReportRpcStatReq;
import krpc.rpc.monitor.proto.ReportRpcStatRes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultMonitorService implements MonitorService {

    static Logger log = LoggerFactory.getLogger(Main.class);

    public ReportRpcStatRes reportRpcStat(ReportRpcStatReq req) {
        System.out.println(TextFormat.shortDebugString(req));
        return ReportRpcStatRes.newBuilder().setRetCode(0).build();
    }
}
