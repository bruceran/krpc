package krpc.rpc.bootstrap.spring;

import krpc.rpc.bootstrap.MonitorConfig;

public class MonitorConfigBean extends MonitorConfig {

    public MonitorConfigBean() {
        SpringBootstrap.instance.getBootstrap().setMonitorConfig(this);
    }

}
