package krpc.rpc.bootstrap.spring;

public class AlarmUtils {

    public static void alarm(String type,String msg) {
        SpringBootstrap.instance.getRpcApp().getMonitorService().reportAlarm(type,msg);
    }

}
