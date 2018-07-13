package krpc.rpc.bootstrap.spring;

import krpc.rpc.bootstrap.ServerConfig;

public class ServerConfigBean extends ServerConfig {

    public ServerConfigBean() {
        SpringBootstrap.instance.getBootstrap().addServer(this);
    }

}
