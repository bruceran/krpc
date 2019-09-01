package krpc.rpc.util;

import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class TypeSafe {

    public static String anyToString(Object v) {
        if (v == null) return null;
        if (v instanceof String) {
            return (String) v;
        }
        return v.toString();
    }

    public static String anyToString(Object v,String defaultValue) {
        if (v == null) return defaultValue;
        if (v instanceof String) {
            return (String) v;
        }
        return v.toString();
    }

    public static int anyToInt(Object v) {
        return anyToInt(v, 0);
    }

    public static int anyToInt(Object v, int defaultValue) {
        if (v == null) return defaultValue;
        if (v instanceof Integer) {
            return (Integer) v;
        }
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        try {
            return Integer.parseInt(v.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static long anyToLong(Object v) {
        return anyToLong(v, 0);
    }

    public static long anyToLong(Object v, long defaultValue) {
        if (v == null) return defaultValue;
        if (v instanceof Integer) {
            return (Integer) v;
        }
        if (v instanceof Long) {
            return (Long) v;
        }
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        try {
            return Long.parseLong(v.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static float anyToFloat(Object v) {
        return anyToFloat(v,0);
    }

    public static float anyToFloat(Object v,float defaultValue) {
        if (v == null) return defaultValue;
        if (v instanceof Float) {
            return (Float) v;
        }
        if (v instanceof Number) {
            return ((Number) v).floatValue();
        }
        try {
            return Float.parseFloat(v.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static boolean anyToBool(Object v) {
        return anyToBool(v,false);
    }

    public static boolean anyToBool(Object v,boolean defaultValue) {
        if (v == null || v.equals("") ) return defaultValue;
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        if (v instanceof Number) {
            return ((Number) v).intValue() == 1;
        }
        try {
            String s = v.toString().toLowerCase();
            if( s.equals("true") || s.equals("yes") || s.equals("t") || s.equals("y") || s.equals("1") )
                return true;
            if( s.equals("false") || s.equals("no") || s.equals("f") || s.equals("n") || s.equals("0") )
                return false;
            return defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static double anyToDouble(Object v) {
        return anyToDouble(v, 0.0);
    }

    public static double anyToDouble(Object v,double defaultValue) {
        if (v == null) return defaultValue;
        if (v instanceof Double) {
            return (Double) v;
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        try {
            return Double.parseDouble(v.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    public static BigInteger anyToBigInteger(Object v) {
        if (v == null) return BigInteger.ZERO;

        try {
            return new BigInteger(v.toString());
        } catch (Exception e) {
            return BigInteger.ZERO;
        }
    }

    public static Map<String, Object> anyToMap(Object v) {
        if (v == null) return null;
        if (v instanceof Map) {
            return (Map<String, Object>) v;
        }
        return null;
    }

    public static List<Object> anyToList(Object v) {
        if (v == null) return null;
        if (v instanceof List) {
            return (List<Object>) v;
        }
        return null;
    }


    public static Date anyToDate(Object v) {
        if (v == null) return null;
        if (v instanceof Date) {
            return (Date)v;
        }
        if (v instanceof Long) {
            return new Date((Long)v);
        }
        if (v instanceof String) {
            String s = (String)v;
            if( s.isEmpty() ) return null;
            if( s.contains("-")) {
                return parseDate(s);
            } else {
                long l = anyToLong(s);
                if( l == 0 ) return null;
                return new Date(l);
            }
        }
        return null;
    }

    static Date parseDate(String s) {
        try {
            SimpleDateFormat f ;
            if (s.length() == 19) {
                f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            } else if (s.length() == 10) {
                f = new SimpleDateFormat("yyyy-MM-dd");
            } else {
                return null;
            }
            return f.parse(s);
        } catch(Exception e) {
            return null;
        }
    }
}

