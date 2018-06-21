package com.krpc.boot2;
  
import org.springframework.stereotype.Component;

import com.xxx.userservice.proto.LoginReq;
import com.xxx.userservice.proto.LoginRes;
import com.xxx.userservice.proto.UpdateProfileReq;
import com.xxx.userservice.proto.UpdateProfileRes;
import com.xxx.userservice.proto.UserService;

@Component
public class UserServiceImpl implements UserService {

	String s = "Redis is an open source (BSD licensed), in-memory data structure store, used as a database, cache and message broker. It supports data structures such as strings, hashes, lists, sets, sorted sets with range queries, bitmaps, hyperloglogs and geospatial indexes with radius";

	public LoginRes login(LoginReq req) {
		return LoginRes.newBuilder().setRetCode(0).setRetMsg("service receive req "+s).build();
	}
	
	public UpdateProfileRes updateProfile(UpdateProfileReq req) {
		return UpdateProfileRes.newBuilder().setRetCode(0).build();
	}
	

}