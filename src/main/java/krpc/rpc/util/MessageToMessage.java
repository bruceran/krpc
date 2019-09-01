package krpc.rpc.util;

import com.google.protobuf.*;
import com.google.protobuf.Message.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MessageToMessage {

    static Logger log = LoggerFactory.getLogger(MessageToMessage.class);
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

    static public <T> T toMessage(Class<T> messageCls, Message src) {
        Builder b = generateBuilder(messageCls);
        if( b == null ) return null;
        return (T)toMessage(b,src);
    }

    static public Message toMessage(Builder b,  Message src) {
        try {
            for (Descriptors.FieldDescriptor field : b.getDescriptorForType().getFields()) {
                String name = field.getName();
                Object value = getValue(src, name);
                if (value == null) continue;
                if (field.isMapField()) {
                    objToMap(b, field, value);
                } else if (field.isRepeated()) {
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

    private static boolean isSimpleType(Descriptors.FieldDescriptor field) {
        switch( field.getType()) {
            case MESSAGE:
            case BYTES:
            case ENUM:
                return false;
            default:
                return true;
        }
    }

    private static void objToMap(Builder b, Descriptors.FieldDescriptor field, Object v0)   {

        if( !(v0 instanceof Collection) ) return;

        Descriptors.Descriptor type = field.getMessageType();
        Descriptors.FieldDescriptor keyField = type.findFieldByName("key");
        Descriptors.FieldDescriptor valueField = type.findFieldByName("value");
        if (keyField != null && valueField != null) {

            Collection v0c  = (Collection)v0;
            for(Object e: v0c ) {
                MapEntry entry = (MapEntry)e;
                Object key = entry.getKey();
                Object value = entry.getValue();

                com.google.protobuf.Message.Builder entryBuilder = b.newBuilderForField(field);

                Object k = objToMessageObjInner(entryBuilder,key,keyField,false);
                Object v = objToMessageObjInner(entryBuilder,value,valueField,false);

                if(k == null || v == null ) continue;

                entryBuilder.setField(keyField, k);
                entryBuilder.setField(valueField, v);
                b.addRepeatedField(field, entryBuilder.build());
            }

        } else {
            throw new RuntimeException("Invalid map field");
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

                Message src = (Message)value;

                Builder b2 = isRepeated ?
                        getRepeatedFieldBuilder(b, field.getName()) :
                        getFieldBuilder(b, field);

                for (Descriptors.FieldDescriptor subfield : b2.getDescriptorForType().getFields()) {
                    String subName = subfield.getName();
                    Object subValue = getValue(src, subName);
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

    static Object getField(String name, Message src) {
        String key = src.getClass().getName()+":"+name;
        Object obj = fieldCache.get(key);
        if( obj != null ) return obj;

        for (Descriptors.FieldDescriptor field : src.getDescriptorForType().getFields()) {
            if( name.equals(field.getName()) ) {
                fieldCache.put(key, field);
                return field;
            }
        }

        fieldCache.put(key,notFound);
        return notFound;
    }

    static Object getValue(Message src, String name) {
        try {
            Object fieldObj = getField(name, src);
            if (fieldObj == notFound) return null;
            Descriptors.FieldDescriptor field = (Descriptors.FieldDescriptor)fieldObj;
            return src.getField(field);
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

    static Builder getFieldBuilder(Builder b, Descriptors.FieldDescriptor f) {
        try {
            if(  b instanceof MapEntry.Builder ) {
                MapEntry.Builder bb = (MapEntry.Builder)b;
                return bb.newBuilderForField(f);
            }

            String fieldName = f.getName();
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

    static ThreadLocal<SimpleDateFormat> f = ThreadLocal.withInitial(()->new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

    public static String formatDate(Date d) {
        return f.get().format(d);
    }

}

