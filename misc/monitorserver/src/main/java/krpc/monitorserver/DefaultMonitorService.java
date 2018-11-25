package krpc.monitorserver;

import com.google.protobuf.TextFormat;
import krpc.rpc.monitor.proto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultMonitorService implements MonitorService {

    static Logger log = LoggerFactory.getLogger(Main.class);

    @Override
    public ReportRpcStatRes reportRpcStat(ReportRpcStatReq req) {
        System.out.println(TextFormat.shortDebugString(req));
        return ReportRpcStatRes.newBuilder().setRetCode(0).build();
    }

    @Override
    public ReportSystemInfoRes reportSystemInfo(ReportSystemInfoReq req) {
        System.out.println(TextFormat.shortDebugString(req));
        return ReportSystemInfoRes.newBuilder().setRetCode(0).build();
    }

    @Override
    public ReportAlarmRes reportAlarm(ReportAlarmReq req) {
        System.out.println(TextFormat.shortDebugString(req));
        return ReportAlarmRes.newBuilder().setRetCode(0).build();
    }
}
