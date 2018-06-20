package krpc.rpc.web.impl;

import static krpc.rpc.web.WebConstants.*;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.Message;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message.Builder;

import krpc.rpc.core.ReflectionUtils;
import krpc.rpc.core.ServiceMetas;
import krpc.rpc.util.MapToMessage;
import krpc.rpc.util.MessageToMap;
import krpc.rpc.web.DefaultWebReq;
import krpc.rpc.web.DefaultWebRes;
import krpc.rpc.web.RpcDataConverter;
import krpc.rpc.web.WebContextData;

public class DefaultRpcDataConverter implements RpcDataConverter {
	
	static Logger log = LoggerFactory.getLogger(DefaultRpcDataConverter.class);
	
	ServiceMetas serviceMetas;

    public DefaultRpcDataConverter(ServiceMetas serviceMetas) {
    	this.serviceMetas = serviceMetas;
    }
    
	public Message generateData(WebContextData ctx,DefaultWebReq req,boolean dynamic) {
		
		Builder b = null;
		
		if( dynamic ) {
			Descriptor desc = serviceMetas.findDynamicReqDescriptor(ctx.getMeta().getServiceId(),ctx.getMeta().getMsgId());
			if( desc == null ) return null;
			b = ReflectionUtils.generateDynamicBuilder(desc); 				
		} else {
			Class<?> cls = serviceMetas.findReqClass(ctx.getMeta().getServiceId(),ctx.getMeta().getMsgId());
			if( cls == null ) return null;
			b = ReflectionUtils.generateBuilder(cls); 			
		}

		Map<String,Object> ctxMap = getCtxMap(b,ctx,req);
		return MapToMessage.toMessage(b, req.getParameters(), ctxMap);
	}
	
	Map<String,Object> getCtxMap(Builder b, WebContextData ctx,DefaultWebReq req) {
		
		Map<String,Object> m = null;
		
		for (FieldDescriptor field : b.getDescriptorForType().getFields()) {
			String name = field.getName();
			Object value = getValue(ctx,req,name);
			if( value != null ) {
				if( m == null ) m = new HashMap<>();
				m.put(name, value);
			}			
		}
		
		return m;
	}

	Object getValue(WebContextData ctx,DefaultWebReq req,String name) {

		switch(name) {
			case "httpMethod":
				String method = req.getMethodString();
				if( method.equalsIgnoreCase("head")) 
					return "get";
				else
					return method;
			case "httpSchema":
				return req.isHttps()? "https" : "http";
			case "httpPath":
				return req.getPath();
			case "httpHost":
				return req.getHost();
			case "httpQueryString":
				return req.getQueryString();
			case "httpContentType":
				return req.getContentType();
			case "httpContent":
				return req.getContent();
			case "session":
				return ctx.getSession();
			default:
				if( name.equals(SessionIdName)) {
					return ctx.getSessionId(); 
				}
				if( name.startsWith(HeaderPrefix)) {
					return req.getHeader(toHeaderName(name.substring(HeaderPrefix.length())));
				}
				if( name.startsWith(CookiePrefix)) {
					return req.getCookie( name.substring(CookiePrefix.length())) ;
				}
				if( ctx.getRoute().needLoadSession()) {
					if( ctx.getSession() != null ) {
						return ctx.getSession().get(name); // todo always first  field option [(from)=default,client]
					}
				}
				return req.getParameter(name);
		}
		
	}

	public void parseData(WebContextData ctx,Message message,DefaultWebRes res) {
		HashMap<String,Object> results = new HashMap<>();
		MessageToMap.parseMessage(message,results);
		res.setResults(results);
	}

	public ServiceMetas getServiceMetas() {
		return serviceMetas;
	}

	public void setServiceMetas(ServiceMetas serviceMetas) {
		this.serviceMetas = serviceMetas;
	}

}

