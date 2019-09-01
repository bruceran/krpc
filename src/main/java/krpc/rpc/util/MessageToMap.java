package krpc.rpc.util;

import com.google.protobuf.*;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;

public class MessageToMap {

    static public Map<String, Object> toMap(Message message) {
        Map<String, Object> results = new LinkedHashMap<>();
        parseMessage(message, results, true, Integer.MAX_VALUE);
        return results;
    }

    static public void parseMessage(Message message, Map<String, Object> results) {
        parseMessage(message, results, true, Integer.MAX_VALUE);
    }

    static public void parseMessage(Message message, Map<String, Object> results, boolean withDefault, int maxRepeatedSizeToGet) {
        Map<FieldDescriptor, Object> fields = getFields(message, withDefault);
        for (Map.Entry<FieldDescriptor, Object> i : fields.entrySet()) {

            FieldDescriptor field = i.getKey();
            Object value = i.getValue();

            if (field.isMapField()) {
                parseMapFieldValue(field, value, results, withDefault, maxRepeatedSizeToGet);
            } else if (field.isRepeated()) {
                int count = 0;
                for (Object element : (List<?>) value) {
                    count++;
                    if (count <= maxRepeatedSizeToGet) {
                        parseSingleField(field, element, results, true, withDefault, maxRepeatedSizeToGet);
                    }
                }
            } else {
                parseSingleField(field, value, results, false, withDefault, maxRepeatedSizeToGet);
            }

        }
    }

    private static boolean isSimpleType(FieldDescriptor field) {
        switch( field.getType()) {
            case MESSAGE:
            case BYTES:
            case ENUM:
                return false;
            default:
                return true;
        }
    }

    private static void parseMapFieldValue(FieldDescriptor field, Object value, Map<String, Object> results, boolean withDefault, int maxRepeatedSizeToGet)   {
        Descriptors.Descriptor type = field.getMessageType();
        FieldDescriptor keyField = type.findFieldByName("key");
        FieldDescriptor valueField = type.findFieldByName("value");
        if (keyField != null && valueField != null) {

            Iterator listItem = ((List)value).iterator();

            Map map = new LinkedHashMap();
            Map map0 = null;
            while(listItem.hasNext()) {
                Object element = listItem.next();
                Message entry = (Message)element;
                Object entryKey = entry.getField(keyField);
                Object entryValue = entry.getField(valueField);

                Object k = null;
                Object v = null;

                if( !isSimpleType(keyField) ) {
                    if( map0 == null )
                        map0 = new HashMap();
                    parseSingleField(keyField, entryKey, map0, false, withDefault, maxRepeatedSizeToGet);
                    k  = map0.remove(keyField.getName());
                } else {
                    k = entryKey;
                }

                if(  !isSimpleType(valueField) ) {
                    if( map0 == null )
                        map0 = new HashMap();
                    parseSingleField(valueField, entryValue, map0, false, withDefault, maxRepeatedSizeToGet);
                    v  = map0.remove(valueField.getName());
                } else {
                    v = entryValue;
                }

                map.put(k,v);
            }

            results.put( field.getName(), map);
        } else {
            throw new RuntimeException("Invalid map field");
        }
    }

    static void addToResults(String name, Object value, Map<String, Object> results, boolean isArray) {
        if (!isArray) {
            results.put(name, value);
        } else {
            ArrayList<Object> list = (ArrayList<Object>) results.get(name);
            if (list == null) {
                list = new ArrayList<>();
                results.put(name, list);
            }
            list.add(value);
        }
    }

    static void parseSingleField(FieldDescriptor field, Object value, Map<String, Object> results, boolean isArray, boolean withDefault, int maxRepeatedSizeToGet) {

        String name = field.getName();

        switch (field.getType()) {
            case INT32:
            case SINT32:
            case SFIXED32:
                addToResults(name,value, results, isArray);
                break;

            case INT64:
            case SINT64:
            case SFIXED64:
                addToResults(name,value, results, isArray);
                break;

            case BOOL:
                addToResults(name,value, results, isArray);
                break;

            case FLOAT:
                addToResults(name,value, results, isArray);
                break;

            case DOUBLE:
                addToResults(name,value, results, isArray);
                break;

            case UINT32:
            case FIXED32:
                addToResults(name, unsignedToLong((Integer) value), results, isArray);
                break;

            case UINT64:
            case FIXED64:
                addToResults(name, unsignedToBigInteger((Long) value), results, isArray);
                break;

            case STRING:
                addToResults(name,value, results, isArray);
                break;

            case BYTES: {
                if (value instanceof ByteString) {
                    addToResults(name, value, results, isArray);
                }
                if (value instanceof String) {
                    byte[] bb = getBytes((String) value);
                    if (bb != null) {
                        addToResults(name, ByteString.copyFrom(bb), results, isArray);
                    }
                }
                if (value instanceof byte[]) {
                    addToResults(name, ByteString.copyFrom((byte[]) value), results, isArray);
                }
            }
            break;

            case ENUM:
                addToResults(name, ((EnumValueDescriptor) value).getNumber(), results, isArray);
                break;

            case MESSAGE:
                HashMap<String, Object> sub = new LinkedHashMap<>();
                parseMessage((Message) value, sub, withDefault, maxRepeatedSizeToGet);
                addToResults(name, sub, results, isArray);
                break;

            default:
                break;
        }
    }

    static public Map<FieldDescriptor, Object> getFields(MessageOrBuilder message, boolean withDefaultValue) {
        if (!withDefaultValue) {
            return message.getAllFields();
        }
        Map<FieldDescriptor, Object> fieldsToPrint = new LinkedHashMap<>();
        for (FieldDescriptor field : message.getDescriptorForType().getFields()) {
            if (field.isOptional()) {
                if (field.getJavaType() == FieldDescriptor.JavaType.MESSAGE && !message.hasField(field)) {
                    continue;
                }
                if (field.getJavaType() == FieldDescriptor.JavaType.STRING && !message.hasField(field)) {
                    continue;
                }
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

