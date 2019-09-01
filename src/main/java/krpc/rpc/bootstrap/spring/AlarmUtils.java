package krpc.rpc.bootstrap.spring;

public class AlarmUtils {

    public static void alarm(String type, String msg) {
        SpringBootstrap.instance.getRpcApp().getMonitorService().reportAlarm(type, msg);
    }

    public static void alarm(String type, String msg, String target, String addrs) {
        SpringBootstrap.instance.getRpcApp().getMonitorService().reportAlarm(type, msg, target, addrs);
    }
}
