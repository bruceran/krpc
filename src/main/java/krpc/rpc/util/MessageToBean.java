package krpc.rpc.util;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MessageToBean {

    static Logger log = LoggerFactory.getLogger(MessageToBean.class);

    static Object notFound = new Object();
    static ConcurrentHashMap<String,Object> fieldClsCache = new ConcurrentHashMap<>(); // notFound or Class
    static ConcurrentHashMap<String,Object> fieldCache = new ConcurrentHashMap<>(); // notFound or Field

    static public <T>  T toBean(Message message,Class<T> beanCls) {
        try {
            T obj = beanCls.newInstance();
            copyToBean(message,obj);
            return obj;
        } catch(Exception e) {
            log.error("toBean exception, e="+e.getMessage()+",beanCls="+beanCls.getName());
            return null;
        }
    }

    static public void copyToBean(Message message, Object bean) {
        copyProperties(message, bean);
    }

    static public void copyProperties(Message message, Object bean) {
        Map<Descriptors.FieldDescriptor, Object> fields = getFields(message,true);
        for (Map.Entry<Descriptors.FieldDescriptor, Object> i : fields.entrySet()) {

            Descriptors.FieldDescriptor field = i.getKey();
            Object value = i.getValue();

            if (field.isRepeated()) {
                for (Object element : (List<?>) value) {
                    parseSingleField(field, element, bean, true);
                }
            } else {
                parseSingleField(field, value, bean, false);
            }

        }
    }

    static void parseSingleField(Descriptors.FieldDescriptor field, Object value, Object bean, boolean isArray) {

        String name = field.getName();

        switch (field.getType()) {
            case INT32:
            case SINT32:
            case SFIXED32:
                addToResults(name, value, bean, isArray);
                break;

            case INT64:
            case SINT64:
            case SFIXED64:
                addToResults(name, value, bean, isArray);
                break;

            case BOOL:
                addToResults(name,value, bean, isArray);
                break;

            case FLOAT:
                addToResults(name,value, bean, isArray);
                break;

            case DOUBLE:
                addToResults(name,value, bean, isArray);
                break;

            case UINT32:
            case FIXED32:
                addToResults(name, unsignedToLong((Integer) value), bean, isArray);
                break;

            case UINT64:
            case FIXED64:
                addToResults(name, unsignedToBigInteger((Long) value), bean, isArray);
                break;

            case STRING:
                addToResults(name,value, bean, isArray);
                break;

            case BYTES: {
                if (value instanceof ByteString) {
                    addToResults(name, value, bean, isArray);
                }
                if (value instanceof String) {
                    byte[] bb = getBytes((String) value);
                    if (bb != null) {
                        addToResults(name, ByteString.copyFrom(bb), bean, isArray);
                    }
                }
                if (value instanceof byte[]) {
                    addToResults(name, ByteString.copyFrom((byte[]) value), bean, isArray);
                }
            }
            break;

            case ENUM:
                addToResults(name, ((Descriptors.EnumValueDescriptor) value).getNumber(), bean, isArray);
                break;

            case MESSAGE:
                Object sub = getFieldInstance(name,bean);
                copyProperties((Message) value, sub);
                addToResults(name, sub, bean, isArray);
                break;

            default:
                break;
        }
    }

    static void addToResults(String name, Object value, Object bean, boolean isArray) {
        try {
            Object fieldObj = getField(name,bean);
            if( fieldObj == notFound ) return;
            Field field = (Field)fieldObj;

            if (!isArray) {
                Object newValue = convertType(field.getType(), value);
                field.set(bean, newValue);
            } else {
                Object list = field.get(bean);
                if( list == null ) {
                    Class cls = field.getType();
                    if( cls.equals(List.class) || cls.equals(ArrayList.class) ) {
                        list = ArrayList.class.newInstance();
                        field.set(bean,list);
                    }
                    else if( cls.equals(LinkedList.class) ) {
                        list = LinkedList.class.newInstance();
                        field.set(bean,list);
                    } else {
                        return;
                    }
                }
                if( list instanceof List ) {
                    ((List)list).add(value);
                }
                else if( list instanceof ArrayList ) {
                    ((ArrayList)list).add(value);
                }
                else if( list instanceof LinkedList ) {
                    ((LinkedList)list).add(value);
                }
            }
        } catch(Exception e) {
        }
    }

    static Object convertType(Class fieldCls, Object v) {
        String type = fieldCls.getName();

        switch (type) {
            case "boolean":
            case "java.lang.Boolean":
                return TypeSafe.anyToBool(v);
            case "char":
            case "java.lang.Character":
                return (char) TypeSafe.anyToInt(v);
            case "byte":
            case "java.lang.Byte":
                return (byte) TypeSafe.anyToInt(v);
            case "short":
            case "java.lang.Short":
                return (short) TypeSafe.anyToInt(v);
            case "int":
            case "java.lang.Integer":
                return TypeSafe.anyToInt(v);
            case "long":
            case "java.lang.Long":
                return TypeSafe.anyToLong(TypeSafe.anyToString(v));
            case "float":
            case "java.lang.Float":
                return TypeSafe.anyToFloat(TypeSafe.anyToString(v));
            case "double":
            case "java.lang.Double":
                return TypeSafe.anyToDouble(TypeSafe.anyToString(v));
            case "java.lang.String":
                return TypeSafe.anyToString(v);
            case "java.util.Date":
                return TypeSafe.anyToDate(v);
            case "java.sql.Date": {
                Date d = TypeSafe.anyToDate(v);
                if( d == null ) return null;
                return new java.sql.Date(d.getTime());
            }
            case "java.sql.Timestamp": {
                Date d = TypeSafe.anyToDate(v);
                if (d == null) return null;
                return new java.sql.Timestamp(d.getTime());
            }
        }
        return null;
    }

    static Object getFieldCls(String name, Object bean) {
        String key = bean.getClass().getName()+":"+name;
        Object obj = fieldClsCache.get(key);
        if( obj != null ) return obj;

        try {
            Object fieldObj = getField(name,bean);
            if( fieldObj != notFound ) {
                Field field = (Field) fieldObj;
                Class cls = field.getType();
                if (cls.equals(List.class) || cls.equals(ArrayList.class) || cls.equals(LinkedList.class)) {
                    String typeName = field.getGenericType().getTypeName();
                    String itemClsName = getItemClsName(typeName);
                    if (itemClsName != null) {
                        cls = Class.forName(itemClsName);
                        fieldClsCache.put(key, cls);
                        return cls;
                    }
                } else {
                    fieldClsCache.put(key, cls);
                    return cls;
                }
            }
        } catch(Exception e) {
        }
        fieldClsCache.put(key,notFound);
        return notFound;
    }

    static Object getFieldInstance(String name, Object bean) {
        try {
            Object obj = getFieldCls(name,bean);
            if( obj == notFound ) return null;
            return ((Class)obj).newInstance();
        } catch(Exception e) {
            String key = bean.getClass().getName()+":"+name;
            fieldClsCache.put(key,notFound);
            return null;
        }
    }

    static String getItemClsName(String typeName) {
        int p1 = typeName.indexOf("<");
        int p2 = typeName.lastIndexOf(">");
        if (p1 >= 0 && p2 > p1) return typeName.substring(p1 + 1, p2);
        return null;
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

    static public Map<Descriptors.FieldDescriptor, Object> getFields(MessageOrBuilder message, boolean withDefaultValue) {
        if (!withDefaultValue) {
            return message.getAllFields();
        }
        Map<Descriptors.FieldDescriptor, Object> fieldsToPrint = new LinkedHashMap<>();
        for (Descriptors.FieldDescriptor field : message.getDescriptorForType().getFields()) {
            if (field.isOptional()) {
                if (field.getJavaType() == Descriptors.FieldDescriptor.JavaType.MESSAGE && !message.hasField(field)) {
                    continue;
                }
//                if (field.getJavaType() == Descriptors.FieldDescriptor.JavaType.STRING && !message.hasField(field)) {
//                    continue;
//                }
            }
            fieldsToPrint.put(field, message.getField(field));
        }
        return fieldsToPrint;
    }

    static byte[] getBytes(String s) {
        if (s == null) return null;
        try {
            return s.getBytes("utf-8");
        } catch (Exception e) {
            return null;
        }
    }

    static public Long unsignedToLong(final int value) {
        if (value >= 0) {
            return Long.valueOf(value);
        } else {
            return value & 0x00000000FFFFFFFFL;
        }
    }

    static public BigInteger unsignedToBigInteger(final long value) {
        if (value >= 0) {
            return BigInteger.valueOf(value);
        } else {
            return BigInteger.valueOf(value & 0x7FFFFFFFFFFFFFFFL).setBit(63);
        }
    }

    public static String unsignedToString(final int value) {
        if (value >= 0) {
            return Integer.toString(value);
        } else {
            return String.valueOf(unsignedToLong(value));
        }
    }

    public static String unsignedToString(final long value) {
        if (value >= 0) {
            return Long.toString(value);
        } else {
            return String.valueOf(unsignedToBigInteger(value));
        }
    }

}

