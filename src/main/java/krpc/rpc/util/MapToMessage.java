package krpc.rpc.util;

import java.lang.reflect.Method;
import java.math.BigInteger;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;

import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message.Builder;

public class MapToMessage {
	
	static Logger log = LoggerFactory.getLogger(MapToMessage.class);
	
	static public Message toMessage(Message.Builder b,Map<String,Object> results) {
		return toMessage(b,results,null);
	}

	static public Message toMessage(Message.Builder b,Map<String,Object> params,Map<String,Object> ctx) {
		for (FieldDescriptor field : b.getDescriptorForType().getFields()) {
			String name = field.getName();
			Object value = getValue(params,ctx,name);
			if( value == null ) continue;
			if (field.isRepeated()) {
				objToMessageObjRepeated(b,value,field);
			} else {
				if( value instanceof List ) {
					value = ((List)value).get(0);
				}
				objToMessageObj(b,value,field);
			}
		}
		
		return b.build();
	}
	
	static void objToMessageObjRepeated(Builder b,Object value,FieldDescriptor field) {
		List<Object> list = TypeSafe.anyToList(value);
		if( list == null ) return;

		for(Object o: list) {
			Object newObj = objToMessageObjInner(b,o,field,true);
			if( newObj != null )
				b.addRepeatedField(field, newObj); 
		}
	}
	
	static void objToMessageObj(Builder b,Object value,FieldDescriptor field) {
		Object newObj = objToMessageObjInner(b,value,field,false);
		if( newObj != null )
			b.setField(field, newObj );
	}
	
	static Object objToMessageObjInner(Builder b,Object value,FieldDescriptor field,boolean isRepeated) {
		
			switch (field.getType()) {
			case INT32:
			case SINT32:
			case SFIXED32:
				return TypeSafe.anyToInt(value);

			case INT64:
			case SINT64:
			case SFIXED64:
				return TypeSafe.anyToLong(value);

			case BOOL:
				return TypeSafe.anyToBool(value);

			case FLOAT:
				return TypeSafe.anyToFloat(value);

			case DOUBLE:
				return TypeSafe.anyToDouble(value);

			case UINT32:
			case FIXED32:
				return  (int)( TypeSafe.anyToLong(value) & 0x00000000FFFFFFFFL );

			case UINT64:
			case FIXED64:
				BigInteger bi = new BigInteger(value.toString());
				return bi.longValue();

			case STRING:
				return TypeSafe.anyToString(value);

			case BYTES:
				{
					if( value instanceof ByteString) {
						return (ByteString)value;
					}
					if( value instanceof String ) {
						byte[] bb = getBytes((String)value);
						if( bb == null ) return null;
						return ByteString.copyFrom(bb);
					}
					if( value instanceof byte[] ) {
						ByteString.copyFrom((byte[])value);
					}
				}				
				
				return null; // todo

			case ENUM:
				EnumDescriptor ed = (EnumDescriptor)field.getEnumType();
				EnumValueDescriptor evd = ed.findValueByName(value.toString());
				if( evd == null ) {
					evd = ed.findValueByNumber(TypeSafe.anyToInt(value));
				}
				if( evd == null ) return null;
				return evd;

			case MESSAGE:
				
				Map<String,Object> map = TypeSafe.anyToMap(value);
				if( map == null ) return null;
				
				Builder b2 = isRepeated ?
						getRepeatedFieldBuilder(b,field.getName()) :
						getFieldBuilder(b,field.getName()); 
				
				for (FieldDescriptor subfield : b2.getDescriptorForType().getFields()) {
					String subName = subfield.getName();
					Object subValue = getValue(map,null,subName);
					if( subValue == null ) continue;
					if (subfield.isRepeated()) {
						objToMessageObjRepeated(b2,subValue,subfield);
					} else {
						objToMessageObj(b2,subValue,subfield);
					}
				}
				
				return isRepeated ? null : b2.build();

			default:
				return  null;
			}
	}
	
	static Object getValue(Map<String,Object> params,Map<String,Object> ctx,String name) {
		if( ctx != null ) {
			Object o = ctx.get(name);
			if( o != null ) return o;
		}
		return params.get(name);
	}

	static byte[] getBytes(String s) {
		if( s == null ) return null;
		try {
			return s.getBytes("utf-8");
		} catch(Exception e) {
			return null;
		}
	}
		
	static Class<?>[] dummyTypes = new Class<?>[0];
	static Object[] dummyParameters = new Object[0];
	
	@SuppressWarnings("all")
	static Builder getFieldBuilder(Builder b,String fieldName) {  
	    try {  
	    	String methodName = "get"+fieldName.substring(0,1).toUpperCase()+fieldName.substring(1)+"Builder";
		    Method method = b.getClass().getDeclaredMethod(methodName,dummyTypes);
	    	Builder builder = (Builder)method.invoke(b,dummyParameters); // todo cache
	        return builder;
	    } catch(Exception e) {  
	    	throw new RuntimeException("getFieldBuilder exception",e);
	    }   
	}
	
	@SuppressWarnings("all")
	static Builder getRepeatedFieldBuilder(Builder b,String fieldName) {  
	    try {  
	    	String methodName = "add"+fieldName.substring(0,1).toUpperCase()+fieldName.substring(1)+"Builder";
		    Method method = b.getClass().getDeclaredMethod(methodName,dummyTypes);
		    Object builder = (Object)method.invoke(b,dummyParameters); // todo cache
	        return (Builder)builder;
	    } catch(Exception e) {  
	    	throw new RuntimeException("getFieldBuilder exception",e);
	    }   
	}
	
}

