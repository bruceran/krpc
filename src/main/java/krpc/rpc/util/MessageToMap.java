package krpc.rpc.util;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;

public class MessageToMap {
	
	static Logger log = LoggerFactory.getLogger(MessageToMap.class);
	
	static public void parseMessage(Message message,Map<String,Object> results) {
		parseMessage(message,results,true,Integer.MAX_VALUE);
	}
	
	static public void parseMessage(Message message,Map<String,Object> results,boolean withDefault,int maxRepeatedSizeToGet) {
		Map<FieldDescriptor, Object> fields = getFields(message,withDefault);
		for ( Map.Entry<FieldDescriptor, Object> i: fields.entrySet() ) {
			
			FieldDescriptor field = i.getKey();
			Object value = i.getValue();
			
			if (field.isRepeated()) {
				int count = 0;
				for (Object element : (List<?>) value) {
					count++;
					if( count <= maxRepeatedSizeToGet ) {
						parseSingleField(field, element, results,true,withDefault,maxRepeatedSizeToGet);
					}
				}
			} else {
				parseSingleField(field, value, results,false,withDefault,maxRepeatedSizeToGet);
			}
			
		}
	}

	@SuppressWarnings("unchecked")
	static void addToResults(String name,Object value, Map<String,Object> results,boolean isArray) {
		if(!isArray) {
			results.put(name, value);
		} else {
			ArrayList<Object> list = (ArrayList<Object>)results.get(name);
			if( list == null ) { 
				list = new ArrayList<Object>();
				results.put(name, list);
			}
			list.add(value);
		}
	}

	static void parseSingleField(FieldDescriptor field, Object value, Map<String,Object> results,boolean isArray,boolean withDefault,int maxRepeatedSizeToGet)  {

		String name = field.getName();
		
		switch (field.getType()) {
		case INT32:
		case SINT32:
		case SFIXED32:
			addToResults(name, (Integer) value,results,isArray);
			break;

		case INT64:
		case SINT64:
		case SFIXED64:
			addToResults(name, (Long) value,results,isArray);
			break;

		case BOOL:
			addToResults(name, (Boolean) value,results,isArray);
			break;

		case FLOAT:
			addToResults(name, (Float) value,results,isArray);
			break;

		case DOUBLE:
			addToResults(name, (Double) value,results,isArray);
			break;

		case UINT32:
		case FIXED32:
			addToResults(name, unsignedToLong((Integer) value),results,isArray);
			break;

		case UINT64:
		case FIXED64:
			addToResults(name, unsignedToBigInteger((Long) value),results,isArray);
			break;

		case STRING:
			addToResults(name, (String) value,results,isArray);
			break;

		case BYTES:
			{
				if( value instanceof ByteString) {
					addToResults(name, (ByteString) value,results, isArray);
				}
				if( value instanceof String ) {
					byte[] bb = getBytes((String)value);
					if( bb != null ) {
						addToResults(name, ByteString.copyFrom(bb),results, isArray);
					}
				}
				if( value instanceof byte[] ) {
					addToResults(name, ByteString.copyFrom((byte[])value),results, isArray);
				}
			}
			break;

		case ENUM:
			addToResults(name, ((EnumValueDescriptor) value).getNumber(),results,isArray);
			break;

		case MESSAGE:
			HashMap<String,Object> sub = new HashMap<>();
			parseMessage((Message) value,sub,withDefault,maxRepeatedSizeToGet);
			addToResults(name,sub,results,isArray);
			break;
			
		default:
			break;
		}
	}
	
	static public Map<FieldDescriptor, Object> getFields(MessageOrBuilder message, boolean withDefaultValue) {
		Map<FieldDescriptor, Object> fieldsToPrint = null;
		if (withDefaultValue) {
			fieldsToPrint = new TreeMap<FieldDescriptor, Object>(message.getAllFields());
			for (FieldDescriptor field : message.getDescriptorForType().getFields()) {
				if (field.isOptional()) {
					if (field.getJavaType() == FieldDescriptor.JavaType.MESSAGE && !message.hasField(field)) {
						continue;
					}
					if (field.getJavaType() == FieldDescriptor.JavaType.STRING && !message.hasField(field)) {
						continue;
					}
				}
				if (!fieldsToPrint.containsKey(field) ) {
					fieldsToPrint.put(field, message.getField(field));
				}
			}
		} else {
			fieldsToPrint = message.getAllFields();
		}
		return fieldsToPrint;
	}

	static byte[] getBytes(String s) {
		if( s == null ) return null;
		try {
			return s.getBytes("utf-8");
		} catch(Exception e) {
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

