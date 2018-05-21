package krpc.web.impl;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import krpc.web.JsonConverter;

public class JacksonJsonConverter implements JsonConverter {

	static Logger log = LoggerFactory.getLogger(JacksonJsonConverter.class);
	
	ObjectMapper mapper = new ObjectMapper();
    
    public JacksonJsonConverter() {
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
    
    @SuppressWarnings("unchecked")
	public Map<String,Object> toMap(String s) {
        try {
	    	Map<String,Object> results = mapper.readValue(s, HashMap.class);
	    	return results;
        } catch (Exception e) {
        	log.error("json convert exception",e);
            return null;
        }
    }
    
    public String fromMap(Map<String,Object> map) {
        try {
            return mapper.writeValueAsString(map);
        } catch (Exception e) {
        	log.error("json convert exception",e);
            return null;
        }
    }
    
}
