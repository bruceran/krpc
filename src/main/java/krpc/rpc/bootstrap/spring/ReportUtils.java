package krpc.rpc.bootstrap.spring;

import krpc.rpc.core.VirtualServiceClosure;

public class ReportUtils {

    public static void report(VirtualServiceClosure closure) {
        if( SpringBootstrap.instance.getRpcApp() == null ) return;
        if( SpringBootstrap.instance.getRpcApp().getMonitorService() == null ) return;
        SpringBootstrap.instance.getRpcApp().getMonitorService().virtualServiceDone(closure);
    }

}
