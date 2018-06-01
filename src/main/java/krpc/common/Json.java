package krpc.common;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Json {

	static private Logger log = LoggerFactory.getLogger(Json.class);
	
	static private ObjectMapper mapper = new ObjectMapper();
    
    static {
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
    
    static public String toJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
        	log.error("json convert exception",e);
            return null;
        }    	
    }
    
    static public Map<String,Object> toMap(String s) {
        try {
	    	Map<String,Object> results = mapper.readValue(s, new TypeReference<Map<String,Object>>() {});
	    	return results;
        } catch (Exception e) {
        	log.error("json convert exception",e);
            return null;
        }
    }
    

    static public List<Object> toList(String s) {
        try {
        	List<Object> results = mapper.readValue(s, new TypeReference<List<Object>>() {});
	    	return results;
        } catch (Exception e) {
        	log.error("json convert exception",e);
            return null;
        }
    }
    
    static public <T> T toObject(String s,Class<T> cls) {
        try {
        	T results = mapper.readValue(s, cls);
	    	return results;
        } catch (Exception e) {
        	log.error("json convert exception",e);
            return null;
        }
    }    
}
