package krpc.rpc.util;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageToJson {

    static Logger log = LoggerFactory.getLogger(MessageToJson.class);

    static private JsonFormat.Printer p = JsonFormat.printer().omittingInsignificantWhitespace().includingDefaultValueFields();

    static public String toJson(Message message) {
        try {
            return p.print(message);
        } catch(Exception e) {
            log.error("toJson exception, e="+e.getMessage());
            return null;
        }
    }

}

