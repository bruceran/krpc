package krpc.rpc.web;

import java.util.Map;

import krpc.rpc.core.Plugin;

public interface JsonConverter extends Plugin {

    Map<String,Object> toMap(String s);
    String fromMap(Map<String,Object> map);
    
}
