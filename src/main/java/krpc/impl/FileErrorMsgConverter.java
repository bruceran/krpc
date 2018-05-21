package krpc.impl;

import java.io.InputStreamReader;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import krpc.core.ErrorMsgConverter;
import krpc.core.InitClose;
import krpc.core.Plugin;

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
		Map<String,String> params = Plugin.defaultSplitParams(paramsStr);
		if( params.containsKey("location")) {
			location = params.get("location");
		}
	}
	
	public void init() {
		try {
			prop = new Properties();      
			prop.load(new InputStreamReader(this.getClass().getClassLoader().getResourceAsStream(location), "UTF-8"));
		} catch(Exception e) {
			log.error("error code message file cannnot be loaded, location="+location);
			prop = null;
		}		
	}
	
	public void close() {
	}

    public String getErrorMsg(int retCode) {
    	if( prop == null ) return null;
    	return prop.getProperty(String.valueOf(retCode));
    }

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

}

