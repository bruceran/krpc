package krpc.rpc.monitor;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;

import krpc.rpc.core.ReflectionUtils;
import krpc.rpc.util.MessageToMap;
import krpc.rpc.web.WebMessage;

import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;

public class PbTextLogFormatter extends AbstractLogFormatter {

	static Logger log = LoggerFactory.getLogger(PbTextLogFormatter.class);
	
	Printer printer;
	
	public void config(String paramsStr) {
		configInner(paramsStr);
		printer = new Printer();
		printer.maxRepeatedSizeToLog = this.maxRepeatedSizeToLog;
		printer.maskFieldsSet = this.maskFieldsSet;
		printer.printDefault = this.printDefault;	
	}
	
	public String toLogStr(boolean isServerLog, Message body) {
    	try {
    	      StringBuilder text = new StringBuilder();
    	      TextGenerator tg = new TextGenerator(text);
    	      printer.print(body, tg, !isServerLog);
    	      return text.toString();
    	} catch(Exception e) {
    		log.error("toLogStr exception, e="+e.getMessage());
    		return "";
    	}
	}
	public String toLogStr(boolean isServerLog, WebMessage body) {
		return "";
	}
	
	static class Printer {

	    int maxRepeatedSizeToLog = 1;
	    HashSet<String> maskFieldsSet;
	    boolean printDefault = false;
	    
		void print(final MessageOrBuilder message, TextGenerator generator,boolean removeRetCode) throws IOException {
			for (Map.Entry<FieldDescriptor, Object> field : MessageToMap.getFields(message,printDefault).entrySet() ) {
			
				if( removeRetCode && field.getKey().getName().equals(ReflectionUtils.retCodeFieldInMap) ) continue;
				
				if( maskFieldsSet != null && maskFieldsSet.contains(field.getKey().getName()))
					printMask(field.getKey().getName(), generator);
				else
					printField(field.getKey(), field.getValue(), generator);
			}
		}

		void printField(final FieldDescriptor field, final Object value, final TextGenerator generator)
				throws IOException {
			if (field.isRepeated()) {
				int count = 0;
				for (Object element : (List<?>) value) {
					if( count >= maxRepeatedSizeToLog ) continue;
					printSingleField(field, element, generator);
					count++;
				}
			} else {
				printSingleField(field, value, generator);
			}
		}

		void printMask(String fieldName, TextGenerator generator) throws IOException  {
			generator.print(fieldName);
			generator.print(": ");
			generator.print("\"***\"");
			generator.eol();
		}

		void printSingleField(final FieldDescriptor field, final Object value, final TextGenerator generator)
				throws IOException {
			generator.print(field.getName());

			if (field.getJavaType() == FieldDescriptor.JavaType.MESSAGE) {
				generator.print(" {");
				generator.eol();
			} else {
				generator.print(": ");
			}

			printFieldValue(field, value, generator);

			if (field.getJavaType() == FieldDescriptor.JavaType.MESSAGE) {
				generator.print("}");
			}
			generator.eol();
		}

		void printFieldValue(final FieldDescriptor field, final Object value, final TextGenerator generator)
				throws IOException {
			switch (field.getType()) {
			case INT32:
			case SINT32:
			case SFIXED32:
				generator.print(((Integer) value).toString());
				break;

			case INT64:
			case SINT64:
			case SFIXED64:
				generator.print(((Long) value).toString());
				break;

			case BOOL:
				generator.print(((Boolean) value).toString());
				break;

			case FLOAT:
				generator.print(((Float) value).toString());
				break;

			case DOUBLE:
				generator.print(((Double) value).toString());
				break;

			case UINT32:
			case FIXED32:
				generator.print(MessageToMap.unsignedToString((Integer) value));
				break;

			case UINT64:
			case FIXED64:
				generator.print(MessageToMap.unsignedToString((Long) value));
				break;

			case STRING:
				generator.print("\"");
				generator.print( escapeText((String) value) );
				generator.print("\"");
				break;

			case BYTES:
				generator.print("\"");
				// donot print bytes
				generator.print("\"");
				break;

			case ENUM:
				generator.print(String.valueOf(((EnumValueDescriptor) value).getNumber()));
				break;

			case MESSAGE:
				print((Message) value, generator,false);
				break;
				
			default:
				break;
			}
		}
		
		String escapeText(String input) {
			final StringBuilder builder = new StringBuilder();
			char[] ca = input.toCharArray();
			for (int i = 0; i < ca.length ; i++) {
				char b = ca[i];
				switch (b) {
				// Java does not recognize \a or \v, apparently.
				case 0x07:
					builder.append("\\a");
					break;
				case '\b':
					builder.append("\\b");
					break;
				case '\f':
					builder.append("\\f");
					break;
				case '\n':
					builder.append("\\n");
					break;
				case '\r':
					builder.append("\\r");
					break;
				case '\t':
					builder.append("\\t");
					break;
				case 0x0b:
					builder.append("\\v");
					break;
				case '\\':
					builder.append("\\\\");
					break;
				case '\'':
					builder.append("\\\'");
					break;
				case '"':
					builder.append("\\\"");
					break;
				default:
					builder.append(b);
					break;
				}
			}
			return builder.toString();
		}
	}

	static class TextGenerator {
		private final Appendable output;
		private boolean atStartOfLine = false;

		TextGenerator(final Appendable output) {
			this.output = output;
		}

		public void print(final CharSequence text) throws IOException {
			if (atStartOfLine) {
				atStartOfLine = false;
				output.append(" ");
			}
			output.append(text);
		}

		public void eol() throws IOException {
			atStartOfLine = true;
		}
	}

}

