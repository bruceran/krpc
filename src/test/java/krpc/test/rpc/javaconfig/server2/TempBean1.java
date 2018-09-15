package krpc.test.rpc.javaconfig.server2;

import com.xxx.userservice.proto.PushServicev2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class TempBean1 {

    static Logger log = LoggerFactory.getLogger(TempBean1.class);

    @Autowired
    private PushServicev2 pushv2;


}
