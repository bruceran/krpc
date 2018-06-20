package krpc.rpc.impl;

import com.google.protobuf.Message;
import com.google.protobuf.Descriptors.FieldDescriptor;

public class FallbackMessageDataProvider implements  FallbackExpr.DataProvider {
	Message req;
	
	FallbackMessageDataProvider(Message req) {
		this.req = req;
	}
	
	public String get(String key) {
		if( key == null || key.isEmpty() ) return null;
		String[] keys = key.split("\\.");
		return get(req,keys,0);
	}
	
	public String get(Message m, String[] keys, int idx) {
		String name = keys[idx];
		FieldDescriptor field = m.getDescriptorForType().findFieldByName(name);
		if( field == null ) return null;
		Object value = m.getField(field);
		if( value == null ) return "";
		if (field.getJavaType() == FieldDescriptor.JavaType.MESSAGE ) {
			Message sub = (Message) value;
			if( idx >= keys.length - 1) {
				return "";
			} else {
				return get(sub,keys,idx+1);
			}
		}
		return value.toString();
	}
	
	
}