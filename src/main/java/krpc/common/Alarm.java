package krpc.common;

public interface Alarm {

    String ALARM_TYPE_REGDIS = "001";
    String ALARM_TYPE_RPCSERVER = "002";
    String ALARM_TYPE_DISKIO = "003";

    // 004 db
    // 005 redis
    // 006 rabbitmq
    // 007 http resttemplate/urlclient
    // 008 kafka
    // 009 emq
    // 010 cfgservice

    String ALARM_TYPE_APM = "011";
    String ALARM_TYPE_QUEUEFULL = "012";
    String ALARM_TYPE_MONITOR = "013";

    // 014 healthdetector process not found
    // 015 hbase
    // 016 curve 1
    // 017 curve 2
    // 018 mongodb

    String getAlarmId(String type);
    void alarm(String type,String msg);
    void alarm(String type,String msg,String target,String addrs);

    String getAlarmPrefix();
    void alarm4rpc(String alarmId,String msg,String target,String addrs);
}
