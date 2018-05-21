package krpc.web.impl;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.Message;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message.Builder;

import krpc.core.ReflectionUtils;
import krpc.core.ServiceMetas;
import krpc.util.MessageToMap;
import krpc.util.TypeSafe;
import krpc.web.DefaultWebReq;
import krpc.web.DefaultWebRes;
import krpc.web.RpcDataConverter;
import krpc.web.WebContextData;

import static krpc.web.WebConstants.*;

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

		for (FieldDescriptor field : b.getDescriptorForType().getFields()) {
			String name = field.getName();
			if (field.isRepeated()) {
				Object value = getTopValue(ctx,req,name,true);
				if( value == null ) continue;
				objToMessageObjRepeated(b,value,field);
			} else {
				Object value = getTopValue(ctx,req,name,false);
				if( value == null ) continue;
				objToMessageObj(b,value,field);
			}
		}
		
		return b.build();
	}
	
	void objToMessageObjRepeated(Builder b,Object value,FieldDescriptor field) {
		List<Object> list = TypeSafe.anyToList(value);
		if( list == null ) return;

		for(Object o: list) {
			Object newObj = objToMessageObjInner(b,o,field,true);
			if( newObj != null )
				b.addRepeatedField(field, newObj); 
		}
	}
	
	void objToMessageObj(Builder b,Object value,FieldDescriptor field) {
		Object newObj = objToMessageObjInner(b,value,field,false);
		if( newObj != null )
			b.setField(field, newObj );
	}
	
	Object objToMessageObjInner(Builder b,Object value,FieldDescriptor field,boolean isRepeated) {
		
			switch (field.getType()) {
			case INT32:
			case SINT32:
			case SFIXED32:
				return TypeSafe.anyToInt(value);

			case INT64:
			case SINT64:
			case SFIXED64:
				return TypeSafe.anyToLong(value);

			case BOOL:
				return TypeSafe.anyToBool(value);

			case FLOAT:
				return TypeSafe.anyToFloat(value);

			case DOUBLE:
				return TypeSafe.anyToDouble(value);

			case UINT32:
			case FIXED32:
				return  (int)( TypeSafe.anyToLong(value) & 0x00000000FFFFFFFFL );

			case UINT64:
			case FIXED64:
				BigInteger bi = new BigInteger(value.toString());
				return bi.longValue();

			case STRING:
				return TypeSafe.anyToString(value);

			case BYTES:
				return null; // todo

			case ENUM:
				EnumDescriptor ed = (EnumDescriptor)field.getEnumType();
				EnumValueDescriptor evd = ed.findValueByName(value.toString());
				if( evd == null ) {
					evd = ed.findValueByNumber(TypeSafe.anyToInt(value));
				}
				if( evd == null ) return null;
				return evd;

			case MESSAGE:
				
				Map<String,Object> map = TypeSafe.anyToMap(value);
				if( map == null ) return null;
				
				Builder b2 = isRepeated ?
						ReflectionUtils.getRepeatedFieldBuilder(b,field.getName()) :
						ReflectionUtils.getFieldBuilder(b,field.getName()); 
				
				for (FieldDescriptor subfield : b2.getDescriptorForType().getFields()) {
					String subName = subfield.getName();
					if (subfield.isRepeated()) {
						Object subValue = getNonTopValue(map,subName,true);
						if( subValue == null ) continue;
						objToMessageObjRepeated(b2,subValue,subfield);
					} else {
						Object subValue = getNonTopValue(map,subName,false);
						if( subValue == null ) continue;
						objToMessageObj(b2,subValue,subfield);
					}
				}
				
				return isRepeated ? null : b2.build();

			default:
				return  null;
			}
	}
	
	Object getNonTopValue(Map<String,Object> req,String name,boolean isRepeated) {
		return req.get(name);
	}
	
	Object getTopValue(WebContextData ctx,DefaultWebReq req,String name,boolean isRepeated) {
		
		if( isRepeated ) {
			return req.getParameterList(name);
		}
		
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
		MessageToMap.parseMessage(message,results,true,Integer.MAX_VALUE);
		res.setResults(results);
	}

	public ServiceMetas getServiceMetas() {
		return serviceMetas;
	}

	public void setServiceMetas(ServiceMetas serviceMetas) {
		this.serviceMetas = serviceMetas;
	}

}

