package krpc.test.rpc.javaconfig.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.ComponentScan.Filter;
 
import com.xxx.userservice.proto.*;

import krpc.rpc.bootstrap.Bootstrap;
import krpc.rpc.bootstrap.RpcApp;

@Configuration
@ComponentScan(basePackages = "krpc.test.rpc.javaconfig.client", excludeFilters = { @Filter(type = FilterType.ANNOTATION, value = Configuration.class) })
public class MyClientJavaConfig   {

    @Bean(initMethod = "init", destroyMethod = "close")
    public RpcApp rpcApp(PushService pushService) {
		RpcApp app = new Bootstrap() 
				.addReferer("us",UserService.class,"127.0.0.1:5600") 
				.addReverseService(PushService.class,pushService)	
				.build();
		return app;
    }
    
    @Bean
    public UserService userService(RpcApp app) {
    	UserService us = app.getReferer("us");
    	return us;
    }

    @Bean
    public UserServiceAsync userServiceAsync(RpcApp app) {
    	UserServiceAsync usa = app.getReferer("usAsync");
    	return usa;
    }    
}
