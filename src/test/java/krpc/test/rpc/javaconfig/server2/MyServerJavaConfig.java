package krpc.test.rpc.javaconfig.server2;

import com.xxx.userservice.proto.PushService;
import com.xxx.userservice.proto.PushServicev2;
import com.xxx.userservice.proto.UserService;
import krpc.rpc.bootstrap.Bootstrap;
import krpc.rpc.bootstrap.RpcApp;
import krpc.rpc.bootstrap.RpcAppInitBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

@Configuration
@ComponentScan(basePackages = "krpc.test.rpc.javaconfig.server", excludeFilters = {@Filter(type = FilterType.ANNOTATION, value = Configuration.class)})
public class MyServerJavaConfig {

    // test for "Cyclic Dependency"

    @Bean
    public Bootstrap bootstrap() {
        return new Bootstrap().setTwoPhasesBuild(true);
    }

    @Bean
    public RpcApp rpcApp(Bootstrap bootstrap) {
        RpcApp app = bootstrap.addReverseReferer("push", PushService.class)
                .addReferer("pushv2",PushServicev2.class,"127.0.0.1:8300")
                .addService(UserService.class,"userService")
                .build();
        return app;
    }

    @Bean
    public PushService push(RpcApp app) {
        PushService push = app.getReferer("push");
        return push;
    }

    @Bean
    public PushServicev2 pushv2(RpcApp app) {
        PushServicev2 push = app.getReferer("pushv2");
        return push;
    }

    @Bean(initMethod = "init", destroyMethod = "close")
    public RpcAppInitBean rpcAppInitBean(Bootstrap bootstrap, RpcApp rpcApp, UserService userService) {
        RpcAppInitBean b = new RpcAppInitBean(bootstrap, rpcApp);
        b.addServiceImpl(userService);
        b.postBuild();
        return b;
    }


}
