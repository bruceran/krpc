package krpc.rpc.util;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.google.protobuf.Message.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class BeanToMessage {

    static Logger log = LoggerFactory.getLogger(BeanToMessage.class);
    static Class<?>[] dummyTypes = new Class<?>[0];
    static Object[] dummyParameters = new Object[0];

    static Object notFound = new Object();
    static ConcurrentHashMap<String,Object> fieldCache = new ConcurrentHashMap<>();

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

    static public <T> T toMessage(Class<T> messageCls, Object bean) {
        Builder b = generateBuilder(messageCls);
        if( b == null ) return null;
        return (T)toMessage(b,bean);
    }

    static public Message toMessage(Builder b,  Object bean) {
        try {
            for (Descriptors.FieldDescriptor field : b.getDescriptorForType().getFields()) {
                String name = field.getName();
                Object value = getValue(bean, name);
                if (value == null) continue;
                if (field.isRepeated()) {
                    objToMessageObjRepeated(b, value, field);
                } else {
                    objToMessageObj(b, value, field);
                }
            }

            return b.build();
        } catch(Exception e) {
            log.error("toMessage exception, e="+e.getMessage());
            return null;
        }
    }

    static void objToMessageObjRepeated(Builder b, Object value, Descriptors.FieldDescriptor field) {
        List<Object> list = TypeSafe.anyToList(value);
        if (list == null) return;

        for (Object o : list) {
            Object newObj = objToMessageObjInner(b, o, field, true);
            if (newObj != null)
                b.addRepeatedField(field, newObj);
        }
    }

    static void objToMessageObj(Builder b, Object value, Descriptors.FieldDescriptor field) {
        Object newObj = objToMessageObjInner(b, value, field, false);
        if (newObj != null)
            b.setField(field, newObj);
    }

    static Object objToMessageObjInner(Builder b, Object value, Descriptors.FieldDescriptor field, boolean isRepeated) {

        switch (field.getType()) {
            case INT32:
            case SINT32:
            case SFIXED32:
                return TypeSafe.anyToInt(value);

            case INT64:
            case SINT64:
            case SFIXED64:
                if( value instanceof Date)
                    return ((Date)value).getTime();
                return TypeSafe.anyToLong(value);

            case BOOL:
                return TypeSafe.anyToBool(value);

            case FLOAT:
                return TypeSafe.anyToFloat(value);

            case DOUBLE:
                return TypeSafe.anyToDouble(value);

            case UINT32:
            case FIXED32:
                return (int) (TypeSafe.anyToLong(value) & 0x00000000FFFFFFFFL);

            case UINT64:
            case FIXED64:
                BigInteger bi = new BigInteger(value.toString());
                return bi.longValue();

            case STRING:
                if( value instanceof Date)
                    return formatDate((Date)value);
                return TypeSafe.anyToString(value);

            case BYTES: {
                if (value instanceof ByteString) {
                    return value;
                }
                if (value instanceof String) {
                    byte[] bb = getBytes((String) value);
                    if (bb == null) return null;
                    return ByteString.copyFrom(bb);
                }
                if (value instanceof byte[]) {
                    return ByteString.copyFrom((byte[]) value);
                }
            }

            return null;

            case ENUM:
                Descriptors.EnumDescriptor ed = field.getEnumType();
                Descriptors.EnumValueDescriptor evd = ed.findValueByName(value.toString());
                if (evd == null) {
                    evd = ed.findValueByNumber(TypeSafe.anyToInt(value));
                }
                if (evd == null) return null;
                return evd;

            case MESSAGE:

                Object bean = value;

                Builder b2 = isRepeated ?
                        getRepeatedFieldBuilder(b, field.getName()) :
                        getFieldBuilder(b, field.getName());

                for (Descriptors.FieldDescriptor subfield : b2.getDescriptorForType().getFields()) {
                    String subName = subfield.getName();
                    Object subValue = getValue(bean, subName);
                    if (subValue == null) continue;
                    if (subfield.isRepeated()) {
                        objToMessageObjRepeated(b2, subValue, subfield);
                    } else {
                        objToMessageObj(b2, subValue, subfield);
                    }
                }

                return isRepeated ? null : b2.build();

            default:
                return null;
        }
    }


    static Object getField(String name, Object bean) {
        String key = bean.getClass().getName()+":"+name;
        Object obj = fieldCache.get(key);
        if( obj != null ) return obj;

        Class cls = bean.getClass();
        while(!cls.equals(Object.class)) {
            try {
                Field field = cls.getDeclaredField(name);
                field.setAccessible(true);
                fieldCache.put(key, field);
                return field;
            } catch (Exception e) {
            }
            cls = cls.getSuperclass();
        }

        fieldCache.put(key,notFound);
        return notFound;
    }

    static Object getValue(Object bean, String name) {
        try {
            Object fieldObj = getField(name, bean);
            if (fieldObj == notFound) return null;
            Field field = (Field) fieldObj;
            return field.get(bean);
        } catch(Exception e) {
            return null;
        }
    }

    static byte[] getBytes(String s) {
        if (s == null) return null;
        try {
            return s.getBytes("utf-8");
        } catch (Exception e) {
            return null;
        }
    }

    static Builder getFieldBuilder(Builder b, String fieldName) {
        try {
            String methodName = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1) + "Builder";
            Method method = b.getClass().getDeclaredMethod(methodName, dummyTypes);
            Builder builder = (Builder) method.invoke(b, dummyParameters);
            return builder;
        } catch (Exception e) {
            throw new RuntimeException("getFieldBuilder exception", e);
        }
    }

    static Builder getRepeatedFieldBuilder(Builder b, String fieldName) {
        try {
            String methodName = "add" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1) + "Builder";
            Method method = b.getClass().getDeclaredMethod(methodName, dummyTypes);
            Object builder = method.invoke(b, dummyParameters);
            return (Builder) builder;
        } catch (Exception e) {
            throw new RuntimeException("getFieldBuilder exception", e);
        }
    }

    static SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    public static String formatDate(Date d) {
        return f.format(d);
    }
}

