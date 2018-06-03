package com.krpc.boot1;
 
import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import com.xxx.pushservice.proto.PushService;
import com.xxx.userservice.proto.UserService;

@Component
public class EchoService  {

	@Resource(name="userService")
	UserService userService;
	
	@Resource(name="pushService")
	PushService pushService;
	
	@PostConstruct
	public void init() {
		System.out.println("userService 1="+userService);
		System.out.println("pushService 1="+pushService);
	}

}