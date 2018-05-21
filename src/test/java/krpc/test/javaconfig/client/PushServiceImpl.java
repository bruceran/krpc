package krpc.test.javaconfig.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.xxx.pushservice.proto.PushReq;
import com.xxx.pushservice.proto.PushRes;
import com.xxx.pushservice.proto.PushService;

@Component("pushService")
class PushServiceImpl implements PushService {

	static Logger log = LoggerFactory.getLogger(PushServiceImpl.class);
	
	public PushRes push(PushReq req) {
		log.debug("received:" + req.getMessage()+","+req.getClientId());
		return PushRes.newBuilder().setRetCode(0).setRetMsg("hello, I have recieved your push!").build();
	}
	
}