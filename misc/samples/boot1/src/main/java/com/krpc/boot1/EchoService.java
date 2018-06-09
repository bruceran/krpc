package com.krpc.boot1;
 
import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.xxx.userservice.proto.PushService;
import com.xxx.userservice.proto.PushServiceAsync;
import com.xxx.userservice.proto.UserService;

@Component
public class EchoService  {

	@Autowired
	UserService userService;
	
	@Autowired
	PushService pushService;
	
	@Autowired
	PushServiceAsync pushServiceAsync;
	
	@PostConstruct
	public void init() {
		System.out.println("userService 1="+userService);
		System.out.println("pushService 1="+pushService);
		System.out.println("pushService 2="+pushServiceAsync);
	}

}