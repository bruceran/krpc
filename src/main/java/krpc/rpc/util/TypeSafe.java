package krpc.rpc.util;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TypeSafe {

	public static String anyToString(Object v) {
		if(v == null) return null;
		if( v instanceof String) {
			return (String)v;
		}
		return v.toString(); 	
	}

	public static int anyToInt(Object v) {
		return anyToInt(v,0);
	}
	
	public static int anyToInt(Object v,int defaultValue) {
		if(v == null) return 0;
		if( v instanceof Integer) {
			return (Integer)v;
		}
		if( v instanceof Number) {
			return ((Number)v).intValue();
		}    	
		try {
			return Integer.parseInt(v.toString());
		} catch(Exception e) {
			return defaultValue;
		}    	
	}  
	
	public static long anyToLong(Object v) {
		return anyToLong(v,0);
	}
	
	public static long anyToLong(Object v,long defaultValue) {
		if(v == null) return 0;
		if( v instanceof Integer) {
			return (Integer)v;
		}
		if( v instanceof Long) {
			return (Long)v;
		}
		if( v instanceof Number) {
			return ((Number)v).longValue();
		}    	
		try {
			return Long.parseLong(v.toString());
		} catch(Exception e) {
			return defaultValue;
		}    	
	}  
	
	public static float anyToFloat(Object v) {
		if(v == null) return 0;
		if( v instanceof Float) {
			return (Float)v;
		}
		if( v instanceof Number) {
			return ((Number)v).floatValue();
		}    	
		try {
			return Float.parseFloat(v.toString());
		} catch(Exception e) {
			return 0;
		}    	
	}  	
		
	public static boolean anyToBool(Object v) {
		if(v == null) return false;
		if( v instanceof Boolean) {
			return (Boolean)v;
		}
		if( v instanceof Number) {
			return ((Number)v).intValue() == 1;
		}    	
		try {
			String s = v.toString().toLowerCase();
			return s.equals("true") || s.equals("yes")  || s.equals("t")   || s.equals("y")  || s.equals("1");
		} catch(Exception e) {
			return false;
		}    	
	}  	
		
	
	public static double anyToDouble(Object v) {
		if(v == null) return 0;
		if( v instanceof Double) {
			return (Double)v;
		}
		if( v instanceof Number) {
			return ((Number)v).doubleValue();
		}    	
		try {
			return Double.parseDouble(v.toString());
		} catch(Exception e) {
			return 0;
		}    	
	}  	

	public static BigInteger anyToBigInteger(Object v) {
		if(v == null) return BigInteger.ZERO;

		try {
			return new BigInteger(v.toString());
		} catch(Exception e) {
			return BigInteger.ZERO;
		}    	
	}  
	
	public static Map<String,Object> anyToMap(Object v) {
		if(v == null) return null;
		if( v instanceof HashMap) {
			return (Map<String,Object>)v;
		}
		return null; 	
	}	
	
	public static List<Object> anyToList(Object v) {
		if(v == null) return null;
		if( v instanceof List) {
			return (List<Object>)v;
		}
		return null; 	
	}	
}

