package krpc.test.rpc.javaconfig.client;

import com.xxx.userservice.proto.PushReq;
import com.xxx.userservice.proto.PushRes;
import com.xxx.userservice.proto.PushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class PushServiceImpl implements PushService {

    static Logger log = LoggerFactory.getLogger(PushServiceImpl.class);

    public PushRes push(PushReq req) {
        log.debug("received:" + req.getMessage() + "," + req.getClientId());
        return PushRes.newBuilder().setRetCode(0).setRetMsg("hello, I have recieved your push!").build();
    }

}