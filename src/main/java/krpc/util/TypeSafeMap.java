package krpc.util;

import java.util.Map;
import java.util.Set;

public class TypeSafeMap {

	private Map<String,Object> body;
	
	public TypeSafeMap(Map<String,Object> body) {
		this.body = body;
	}
	
	public Map<String,Object> get() {
		return body;
	}

    public boolean contains(String name) {
    	return body.containsKey(name);
    }

    public void remove(String name) {
    	body.remove(name);
    }
	
    public Set<String> keySet() {
    	return body.keySet();
    }
    
    public String stringValue(String name) {
    	Object v = body.get(name);
    	return TypeSafe.anyToString(v);
    }

    public String stringValue(String name,String defaultValue) {
    	String v = stringValue(name);
    	if(v == null) return defaultValue;
    	return v;
    }
    
    public String notNullString(String name) {
    	String v = stringValue(name);
    	if(v == null) return "";
    	return v;
    }

    public String notNullString(String name,String defaultValue) {
    	String v = stringValue(name);
    	if(v == null || v.equals("")) return defaultValue;
    	return v;
    }

    public int intValue(String name) {
    	Object v = body.get(name);
    	return TypeSafe.anyToInt(v);
    }

    public long longValue(String name) {
    	Object v = body.get(name);
    	return TypeSafe.anyToLong(v);
    }

    public double doubleValue(String name) {
    	Object v = body.get(name);
    	return TypeSafe.anyToDouble(v);
    }
    

    public Map<String,Object> mapValue(String name) {
    	Object v = body.get(name);
    	return TypeSafe.anyToMap(v);
    }
    
}

