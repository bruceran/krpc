package krpc.test.javaconfig.server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.ComponentScan.Filter;

import com.xxx.pushservice.proto.PushService;
import com.xxx.userservice.proto.UserService;

import krpc.bootstrap.Bootstrap;
import krpc.bootstrap.RpcApp;

@Configuration
@ComponentScan(basePackages = "krpc.test.javaconfig.server", excludeFilters = { @Filter(type = FilterType.ANNOTATION, value = Configuration.class) })
public class MyServerJavaConfig   {

    @Bean(destroyMethod = "stopAndClose")
    public RpcApp rpcApp(UserService userService) {
		RpcApp app = new Bootstrap() 
				.addService(UserService.class,userService) 
				.addReverseReferer("push",PushService.class)
				.build();
		app.initAndStart();
		return app;
    }
    
    @Bean
    public PushService push(RpcApp app) {
    	PushService push = app.getReferer("push");
    	return push;
    }
}
