package krpc.common;

public interface Alarm {

    String ALARM_TYPE_REGDIS = "001";
    String ALARM_TYPE_RPCSERVER = "002";
    String ALARM_TYPE_DISKIO = "003";
    // 004-010 reserved
    String ALARM_TYPE_APM = "011";
    String ALARM_TYPE_QUEUEFULL = "012";
    String ALARM_TYPE_MONITOR = "013";

    String getAlarmId(String type);
    void alarm(String type,String msg);

}
