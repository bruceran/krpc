package krpc.rpc.bootstrap.spring;

import krpc.rpc.bootstrap.RegistryConfig;

public class RegistryConfigBean extends RegistryConfig {
	
    public RegistryConfigBean()   {
        SpringBootstrap.instance.getBootstrap().addRegistry(this);
    } 

}
