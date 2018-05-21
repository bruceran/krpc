package krpc.web;

import java.util.Map;

import krpc.core.Plugin;

public interface JsonConverter extends Plugin {

    Map<String,Object> toMap(String s);
    String fromMap(Map<String,Object> map);
    
}
