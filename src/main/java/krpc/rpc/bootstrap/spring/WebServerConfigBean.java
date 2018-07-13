package krpc.rpc.bootstrap.spring;

import krpc.rpc.bootstrap.WebServerConfig;

public class WebServerConfigBean extends WebServerConfig {

    public WebServerConfigBean() {
        SpringBootstrap.instance.getBootstrap().addWebServer(this);
    }

}
