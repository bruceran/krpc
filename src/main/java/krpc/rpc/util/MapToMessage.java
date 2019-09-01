package krpc.rpc.util;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.MapEntry;
import com.google.protobuf.Message;
import com.google.protobuf.Message.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.*;

public class MapToMessage {

    static Logger log = LoggerFactory.getLogger(MapToMessage.class);

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

    static public <T> T toMessage(Class<T> messageCls, Map<String, Object> map) {
        Builder b = generateBuilder(messageCls);
        if( b == null ) return null;
        return (T)toMessage(b,map);
    }

    static public Message toMessage(Builder b, Map<String, Object> map) {
        return toMessage(b, map, null);
    }

    static public Message toMessage(Builder b, Map<String, Object> params, Map<String, Object> ctx) {
        for (FieldDescriptor field : b.getDescriptorForType().getFields()) {
            String name = field.getName();
            Object value = getValue(params, ctx, name);
            if (value == null) continue;
            if (field.isMapField()) {
                objToMap(b, field, value);
            } else if (field.isRepeated()) {
                objToMessageObjRepeated(b, value, field);
            } else {
                if (value instanceof List) {
                    value = ((List) value).get(0);
                    if (value == null) continue;
                }
                objToMessageObj(b, value, field);
            }
        }

        return b.build();
    }

    private static void objToMap(Builder b, FieldDescriptor field, Object map0)   {

        if( !(map0 instanceof  Map) ) return;

        Descriptors.Descriptor type = field.getMessageType();
        FieldDescriptor keyField = type.findFieldByName("key");
        FieldDescriptor valueField = type.findFieldByName("value");
        if (keyField != null && valueField != null) {

            Map map  = (Map)map0;
            for(Object e: map.entrySet() ) {
                Map.Entry entry = (Map.Entry)e;
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

    static void objToMessageObjRepeated(Builder b, Object value, FieldDescriptor field) {
        List<Object> list = TypeSafe.anyToList(value);
        if (list == null) return;

        for (Object o : list) {
            Object newObj = objToMessageObjInner(b, o, field, true);
            if (newObj != null)
                b.addRepeatedField(field, newObj);
        }
    }

    static void objToMessageObj(Builder b, Object value, FieldDescriptor field) {
        Object newObj = objToMessageObjInner(b, value, field, false);
        if (newObj != null)
            b.setField(field, newObj);
    }

    static Object objToMessageObjInner(Builder b, Object value, FieldDescriptor field, boolean isRepeated) {

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

                    return null;
                }

            case ENUM: {
                    EnumDescriptor ed = field.getEnumType();
                    EnumValueDescriptor evd = ed.findValueByName(value.toString());
                    if (evd == null) {
                        evd = ed.findValueByNumber(TypeSafe.anyToInt(value));
                    }
                    if (evd == null) return null;
                    return evd;
                }

            case MESSAGE:

                Map<String, Object> map = TypeSafe.anyToMap(value);
                if (map == null) {
                    if( value instanceof MapConvertable) {
                        map = ((MapConvertable)value).toMap();
                    }
                    if( map == null ) {
                        return null;
                    }
                }

                Builder b2 = isRepeated ?
                        getRepeatedFieldBuilder(b, field.getName()) :
                                getFieldBuilder(b, field);

                for (FieldDescriptor subfield : b2.getDescriptorForType().getFields()) {
                    String subName = subfield.getName();
                    Object subValue = getValue(map, null, subName);
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

    static Object getValue(Map<String, Object> params, Map<String, Object> ctx, String name) {
        if (ctx != null) {
            Object o = ctx.get(name);
            if (o != null) return o;
        }
        return params.get(name);
    }

    static byte[] getBytes(String s) {
        if (s == null) return null;
        try {
            return s.getBytes("utf-8");
        } catch (Exception e) {
            return null;
        }
    }

    static Builder getFieldBuilder(Builder b, FieldDescriptor f) {

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

