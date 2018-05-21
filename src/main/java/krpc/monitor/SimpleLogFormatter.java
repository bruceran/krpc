package krpc.monitor;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;

import krpc.core.ReflectionUtils;
import krpc.util.MessageToMap;
import krpc.web.WebMessage;

public class SimpleLogFormatter extends BaseFormatter  {

	static Logger log = LoggerFactory.getLogger(SimpleLogFormatter.class);

	static final int maxLevels = 2;
	
	Printer printer;
	WebPrinter webPrinter;
	
	public void config(String paramsStr) {
		configInner(paramsStr);
		
		printer = new Printer();
		printer.maxRepeatedSizeToLog = this.maxRepeatedSizeToLog;
		printer.maskFieldsSet = this.maskFieldsSet;	
		printer.printDefault = this.printDefault;	
		
		webPrinter = new WebPrinter();
		webPrinter.maskFieldsSet = this.maskFieldsSet;	
	}

	public String toLogStr(boolean isReqLog, Message body) {
    	try {
    		  Appender tg = new Appender();
    	      Level level = new Level();
    	      printer.print(body, tg, level);
    	      return tg.toString();
    	} catch(Exception e) {
    		log.error("toLogStr exception, e="+e.getMessage());
    		return "";
    	}
	}
	
	public String toLogStr(boolean isReqLog, WebMessage body) {
		Map<String,Object> allLog = getLogData(isReqLog,body,maxRepeatedSizeToLog);
		try {
			  Appender tg = new Appender();
	  	      Level level = new Level();
	  	      webPrinter.print(allLog, tg, level);
	  	      return tg.toString();
	  	} catch(Exception e) {
	  		log.error("toLogStr exception, e="+e.getMessage());
	  		return "";
	  	}
	}

	static class Level {
		int level = 0;
		boolean[] firstField = new boolean[maxLevels];
		
		Level() { clear(); }
		
		boolean isFirst() {
			return firstField[level-1];
		}
		void setFirst(boolean b) {
			firstField[level-1] = b;
		}
		void clear() {
			level = 0;
			for(int i=0;i<maxLevels;++i) {
				firstField[i] = true;
			}
		}
		void resetFirst() {
			for(int i=level;i<maxLevels;++i) {
				firstField[i] = true;
			}
		}	    	
		
		void incLevel() { level++; }
		void decLevel() { level--; resetFirst(); }
	}

	static class Printer {
	
	    int maxRepeatedSizeToLog = 1;
	    HashSet<String> maskFieldsSet;
	    boolean printDefault = false;

		void print(MessageOrBuilder message, Appender appender, Level level) throws IOException {
			
			level.incLevel();		
			
			for (Map.Entry<FieldDescriptor, Object> field : MessageToMap.getFields(message,printDefault).entrySet() ) {
			
				if( field.getKey().getName().equals(ReflectionUtils.retCodeFieldInMap) ) continue;
	
				if( maskFieldsSet != null && maskFieldsSet.contains(field.getKey().getName()))
					printMask(field.getKey().getName(), appender,level);
				else
					printField(field.getKey(), field.getValue(), appender,level);
			}
			
			level.decLevel();
		}
	
		void printField(FieldDescriptor field, Object value, Appender appender, Level level)
				throws IOException {

			if (field.isRepeated()) {
				int count = 0;
				for (Object element : (List<?>) value) {
					if( count >= maxRepeatedSizeToLog ) continue;
					printSingleField(field, element, appender,level);
					count++;
				}
			} else {
				printSingleField(field, value, appender,level);
			}
		}
	
		void printMask(String fieldName, Appender appender, Level level) throws IOException  {

			if( !level.isFirst() ) {
				appender.appendSep( level.level );
			}
			level.setFirst(false);	
			
			appender.appendKey(fieldName);
			appender.appendSep9();
			appender.appendString("***");
		}
	
		void printSingleField(FieldDescriptor field, Object value, Appender appender, Level level)
				throws IOException {
			if (field.getJavaType() == FieldDescriptor.JavaType.MESSAGE) {
				if( level.level+1 > maxLevels ) {
					return;
				}				
			}
			
			if( !level.isFirst() ) {
				appender.appendSep( level.level );
			}
			level.setFirst(false);	
			
			if (field.getJavaType() == FieldDescriptor.JavaType.MESSAGE) {			
				appender.appendKey(field.getName());
				appender.appendSep9();
				appender.appendMapStart();
				printFieldValue(field, value, appender,level);
				appender.appendMapEnd();
			} else {
				appender.appendKey(field.getName());
				appender.appendSep9();
				printFieldValue(field, value, appender,level);
			}
		}
	
		void printFieldValue(FieldDescriptor field, Object value, Appender appender, Level level)
				throws IOException {
			switch (field.getType()) {
			case INT32:
			case SINT32:
			case SFIXED32:
				appender.appendNumber(((Integer) value).toString());
				break;
	
			case INT64:
			case SINT64:
			case SFIXED64:
				appender.appendNumber(((Long) value).toString());
				break;
	
			case BOOL:
				appender.appendNumber(((Boolean) value).toString());
				break;
	
			case FLOAT:
				appender.appendNumber(((Float) value).toString());
				break;
	
			case DOUBLE:
				appender.appendNumber(((Double) value).toString());
				break;
	
			case UINT32:
			case FIXED32:
				appender.appendNumber(MessageToMap.unsignedToString((Integer) value));
				break;
	
			case UINT64:
			case FIXED64:
				appender.appendNumber(MessageToMap.unsignedToString((Long) value));
				break;
				
			case ENUM:
				appender.appendNumber(String.valueOf(((EnumValueDescriptor) value).getNumber()));
				break;
					
			case STRING:
				appender.appendString( escapeText( (String) value) );
				break;
	
			case BYTES:
				// donot print bytes
				break;

			case MESSAGE:
				print((Message) value, appender,level);
				break;
				
			default:
				break;
			}
		}
	}

	static class WebPrinter {

	    HashSet<String> maskFieldsSet;

		void print(Map<String,Object> message, Appender appender, Level level) {
			
			level.incLevel();		
			
			for (Map.Entry<String, Object> entry : message.entrySet() ) {

				if( entry.getValue() == null ) continue;
				
				if( maskFieldsSet != null && maskFieldsSet.contains(entry.getKey()))
					printMask(entry.getKey(), appender,level);
				else
					printField(entry.getKey(), entry.getValue(), appender,level);
			}
			
			level.decLevel();
		}

		void printField(String key, Object value, Appender appender, Level level)  {
			if (value instanceof List) {
				for (Object element : (List<?>) value) {
					printSingleField(key, element, appender,level);
				}
			} else {
				printSingleField(key, value, appender,level);
			}
		}

		void printMask(String key, Appender appender, Level level)  {
			if( !level.isFirst() ) {
				appender.appendSep( level.level );
			}
			level.setFirst(false);		
			appender.appendKey(key);
			appender.appendSep9();
			appender.appendString("***");
		}

		void printSingleField(String key, Object value, Appender appender, Level level) {

			if (value instanceof Map) {
				if( level.level+1 > maxLevels ) {
					return;
				}
			}
			
			if( !level.isFirst() ) {
				appender.appendSep( level.level);
			}
			level.setFirst(false);	
			
			if (value instanceof Map) {
				appender.appendKey(key);
				appender.appendSep9();				
				appender.appendMapStart();
				printFieldValue(value, appender,level);
				appender.appendMapEnd();
			} else {
				appender.appendKey(key);
				appender.appendSep9();				
				printFieldValue(value, appender,level);
			}
		}

		@SuppressWarnings("unchecked")
		void printFieldValue(Object value, Appender appender, Level level) {
			if( value instanceof Map ) {
				print((Map<String,Object>)value, appender,level);
			} else if( value instanceof Number ) {
				appender.appendNumber(value.toString());
			} else {
				appender.appendString(escapeText(value.toString()));	
			}
		}

	}

	public static String escapeText(String input) {
		StringBuilder builder = new StringBuilder();
		char[] ca = input.toCharArray();
		for (int i = 0; i < ca.length ; i++) {
			char b = ca[i];
			if( b >= 0 && b < 32 || b == '^' || b == ',' || b == ':' ) {
				builder.append(" ");
			} else {
				builder.append(b);
			}
		}
		return builder.toString();
	}
	
	public static class Appender {
		private StringBuilder b = new StringBuilder();
		public void appendSep(int level) {
			if( level == 1 )
				b.append("^");
			else
				b.append(",");
		}	
		public void appendSep9() {
			b.append(":");
		}	
		public void appendMapStart() {
			b.append("{");
		}
		public void appendMapEnd() {
			b.append("}");
		}
		public void appendKey(String s) {
			b.append(s);
		}		
		public void appendNumber(String s) {
			b.append(s);
		}		
		public void appendString(String s) {
			b.append(s);
		}
		public String toString() {
			return b.toString();
		}
	}	
	
}
