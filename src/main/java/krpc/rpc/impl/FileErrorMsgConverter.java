package krpc.rpc.impl;

import krpc.common.InitClose;
import krpc.common.Plugin;
import krpc.rpc.core.ErrorMsgConverter;
import krpc.rpc.util.TypeSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public class FileErrorMsgConverter implements ErrorMsgConverter, InitClose {

    static Logger log = LoggerFactory.getLogger(FileErrorMsgConverter.class);

    String location = "error.properties";
    Properties prop = new Properties();

    public FileErrorMsgConverter() {
    }

    public FileErrorMsgConverter(String location) {
        this.location = location;
    }

    public void config(String paramsStr) {
        Map<String, String> params = Plugin.defaultSplitParams(paramsStr);
        if (params.containsKey("location")) {
            location = params.get("location");
        }
    }

    public void init() {

        try (InputStreamReader in = new InputStreamReader(this.getClass().getClassLoader().getResourceAsStream(location), "UTF-8");) {
            prop = new Properties();
            prop.load(in);
        } catch (Exception e) {
            log.error("error code message file cannnot be loaded, location=" + location);
            prop = null;
        }
    }

    public void close() {
    }

    public String getErrorMsg(int retCode) {
        if (prop == null) return null;
        return prop.getProperty(String.valueOf(retCode));
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Map<String,String> getAllMsgs() {
        LinkedHashMap<String,String> map = new LinkedHashMap<>();
        prop.forEach( (k,v)-> {
            int retCode = TypeSafe.anyToInt(k);
            if( retCode <= -1000 ) { // 仅限业务层错误码
                map.put(String.valueOf(k),String.valueOf(v));
            }
        });
        return map;
    }
}

