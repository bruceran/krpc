package krpc.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

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
            log.error("json convert exception", e);
            return null;
        }
    }

    static public Map<String, Object> toMap(String s) {
        try {
            Map<String, Object> results = mapper.readValue(s, new TypeReference<Map<String, Object>>() {
            });
            return results;
        } catch (Exception e) {
            log.error("json convert exception", e);
            return null;
        }
    }


    static public HashMap<String, Object> toHashMap(String s) {
        try {
            HashMap<String, Object> results = mapper.readValue(s, new TypeReference<HashMap<String, Object>>() {
            });
            return results;
        } catch (Exception e) {
            log.error("json convert exception", e);
            return null;
        }
    }


    static public LinkedHashMap<String, Object> toLinkedHashMap(String s) {
        try {
            LinkedHashMap<String, Object> results = mapper.readValue(s, new TypeReference<LinkedHashMap<String, Object>>() {
            });
            return results;
        } catch (Exception e) {
            log.error("json convert exception", e);
            return null;
        }
    }

    static public TreeMap<String, Object> toTreeMap(String s) {
        try {
            TreeMap<String, Object> results = mapper.readValue(s, new TypeReference<TreeMap<String, Object>>() {
            });
            return results;
        } catch (Exception e) {
            log.error("json convert exception", e);
            return null;
        }
    }


    static public List<Object> toList(String s) {
        try {
            List<Object> results = mapper.readValue(s, new TypeReference<List<Object>>() {
            });
            return results;
        } catch (Exception e) {
            log.error("json convert exception", e);
            return null;
        }
    }
    static public ArrayList<Object> toArrayList(String s) {
        try {
            ArrayList<Object> results = mapper.readValue(s, new TypeReference<ArrayList<Object>>() {
            });
            return results;
        } catch (Exception e) {
            log.error("json convert exception", e);
            return null;
        }
    }
    static public LinkedList<Object> toLinkedList(String s) {
        try {
            LinkedList<Object> results = mapper.readValue(s, new TypeReference<LinkedList<Object>>() {
            });
            return results;
        } catch (Exception e) {
            log.error("json convert exception", e);
            return null;
        }
    }

    static public Object toRawObject(String s, Class cls) {
        try {
            Object results = mapper.readValue(s, cls);
            return results;
        } catch (Exception e) {
            log.error("json convert exception", e);
            return null;
        }
    }

    static public <T> T toObject(String s, Class<T> cls) {
        try {
            T results = mapper.readValue(s, cls);
            return results;
        } catch (Exception e) {
            log.error("json convert exception", e);
            return null;
        }
    }

    static public <T> T toObject(String s, TypeReference<T> tr) {
        try {
            T results = mapper.readValue(s, tr);
            return results;
        } catch (Exception e) {
            log.error("json convert exception", e);
            return null;
        }
    }

}
