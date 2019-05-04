package krpc.rpc.util;

import com.google.protobuf.Message;

import java.util.Map;

public class MessageUtils {

    static public <T> T jsonToMessage(Class<T> messageCls, String json) {
        return JsonToMessage.toMessage(messageCls,json);
    }
    static public <T> T beanToMessage(Class<T> messageCls, Object bean) {
        return BeanToMessage.toMessage(messageCls,bean);
    }
    static public <T> T mapToMessage(Class<T> messageCls, Map<String, Object> map) {
        return MapToMessage.toMessage(messageCls,map);
    }
    static public <T> T messageToMessage(Class<T> messageCls, Message src) {
        return MessageToMessage.toMessage(messageCls,src);
    }

    static public <T>  T messageToBean(Message message, Class<T> cls) {
        return MessageToBean.toBean(message,cls);
    }
    static public String messageToJson(Message message) {
        return MessageToJson.toJson(message);
    }
    static public Map<String, Object> messageToMap(Message message) {
        return MessageToMap.toMap(message);
    }

}

