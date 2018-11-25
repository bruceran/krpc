package krpc.monitorserver;

public interface MonitorService {

    static final public int serviceId = 2;

    krpc.rpc.monitor.proto.ReportRpcStatRes reportRpcStat(krpc.rpc.monitor.proto.ReportRpcStatReq req);
    static final public int reportRpcStatMsgId = 1;

    krpc.rpc.monitor.proto.ReportSystemInfoRes reportSystemInfo(krpc.rpc.monitor.proto.ReportSystemInfoReq req);
    static final public int reportSystemInfoMsgId = 2;

    krpc.rpc.monitor.proto.ReportAlarmRes reportAlarm(krpc.rpc.monitor.proto.ReportAlarmReq req);
    static final public int reportAlarmMsgId = 3;

}

