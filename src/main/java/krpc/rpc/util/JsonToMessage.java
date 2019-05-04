package krpc.rpc.util;

import com.google.protobuf.Message;
import com.google.protobuf.Message.Builder;
import com.google.protobuf.util.JsonFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

public class JsonToMessage {

    static Logger log = LoggerFactory.getLogger(JsonToMessage.class);

    static Class<?>[] dummyTypes = new Class<?>[0];
    static Object[] dummyParameters = new Object[0];

    public static Builder generateBuilder(Class<?> messageCls) {
        try {
            Method method = messageCls.getDeclaredMethod("newBuilder", dummyTypes);
            Builder builder = (Builder) method.invoke(null, dummyParameters);
            return builder;
        } catch (Exception e) {
            log.error("generateBuilder exception, e="+e.getMessage()+",messageCls="+messageCls);
            return null;
        }
    }

    static public <T> T toMessage(Class<T> messageCls, String json) {
        Builder b = generateBuilder(messageCls);
        if( b == null ) return null;
        return (T)toMessage(b,json);
    }

    static public Message toMessage(Builder b,  String json) {
        try {
            JsonFormat.parser().merge(json, b);
            return b.build();
        } catch(Exception e) {
            log.error("toMessage exception, e="+e.getMessage());
            return null;
        }
    }

}

