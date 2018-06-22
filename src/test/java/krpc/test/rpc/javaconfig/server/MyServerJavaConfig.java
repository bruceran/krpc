package krpc.test.rpc.javaconfig.server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.ComponentScan.Filter;

import com.xxx.userservice.proto.*;

import krpc.rpc.bootstrap.Bootstrap;
import krpc.rpc.bootstrap.RpcApp;

@Configuration
@ComponentScan(basePackages = "krpc.test.rpc.javaconfig.server", excludeFilters = { @Filter(type = FilterType.ANNOTATION, value = Configuration.class) })
public class MyServerJavaConfig  {

    @Bean(initMethod = "init", destroyMethod = "close")
    public RpcApp rpcApp(UserService userService) {
		RpcApp app = new Bootstrap() 
				.addService(UserService.class,userService) 
				.addReverseReferer("push",PushService.class)
				.build();
		return app;
    }
    
    @Bean
    public PushService push(RpcApp app) {
    	PushService push = app.getReferer("push");
    	return push;
    }
}
