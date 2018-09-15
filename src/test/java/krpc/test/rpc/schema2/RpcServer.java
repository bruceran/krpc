package krpc.test.rpc.schema2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class RpcServer {

    static Logger log = LoggerFactory.getLogger(RpcServer.class);

    // test for "Cyclic Dependency"

    public static void main(String[] args) throws Exception {

        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("spring-schema-server2.xml");
        Thread.sleep(30000);

        context.close();
        ((ch.qos.logback.classic.Logger) log).getLoggerContext().stop();
    }

}

