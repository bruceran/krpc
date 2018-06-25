package com.krpc.boot1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import krpc.rpc.bootstrap.RpcApp;

@SpringBootApplication
public class Boot1Application {

	public static void main(String[] args) throws Exception {
		
    	System.out.println("in Boot1Application: "+Boot1Application.class.getClassLoader().getClass().getName());
    	System.out.println("in Boot1Application: "+Boot1Application.class.getClassLoader());
    	
		SpringApplication a = new SpringApplication();
		AnnotationConfigApplicationContext  ctx = (AnnotationConfigApplicationContext)a.run(Boot1Application.class, args);
		
		Thread.sleep(5000);
		
		RpcApp app = (RpcApp)ctx.getBean("rpcApp");
		app.stop();
		
		ctx.close();
		
	}
}
