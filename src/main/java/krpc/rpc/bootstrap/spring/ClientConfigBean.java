package krpc.rpc.bootstrap.spring;

import krpc.rpc.bootstrap.ClientConfig;

public class ClientConfigBean extends ClientConfig {

	public ClientConfigBean() {
		SpringBootstrap.instance.getBootstrap().addClient(this);
	}
 
}
