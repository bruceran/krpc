package com.krpc.boot2;
 
import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.xxx.pushservice.proto.PushService;
import com.xxx.userservice.proto.LoginReq;
import com.xxx.userservice.proto.LoginRes;
import com.xxx.userservice.proto.UpdateProfileReq;
import com.xxx.userservice.proto.UpdateProfileRes;
import com.xxx.userservice.proto.UserService;

@Component("userService99")
public class UserServiceImpl implements UserService {

	String s = "Redis is an open source (BSD licensed), in-memory data structure store, used as a database, cache and message broker. It supports data structures such as strings, hashes, lists, sets, sorted sets with range queries, bitmaps, hyperloglogs and geospatial indexes with radius";
	
	@Autowired
	PushService pushService;
	
	public UserServiceImpl() {
			System.out.println("UserServiceImpl called");		
	}
	
	@PostConstruct
	public void init() {
			System.out.println("pushService 1="+pushService);
	}
	
	public LoginRes login(LoginReq req) {
		return LoginRes.newBuilder().setRetCode(0).setRetMsg("service receive req "+s).build();
	}
	
	public UpdateProfileRes updateProfile(UpdateProfileReq req) {
		return null;
	}
	

}