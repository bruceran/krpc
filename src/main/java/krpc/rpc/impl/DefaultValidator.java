package krpc.rpc.impl;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.Message.Builder;
import krpc.KrpcExt;
import krpc.rpc.core.ReflectionUtils;
import krpc.rpc.core.Validator;
import krpc.rpc.util.TypeSafe;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

public class DefaultValidator implements Validator {

    static final int TYPE_MATCH = 1;
    static final int TYPE_VALUES = 2;
    static final int TYPE_LENGTH = 3;
    static final int TYPE_NRANGE = 4;
    static final int TYPE_SRANGE = 5;
    static final int TYPE_ARRLEN = 6;

    FieldValidator requiredValidator = new RequiredValidator();

    HashMap<String, FieldValidator> validatorCache = new HashMap<>();

    static class FieldInfo {
        Descriptors.FieldDescriptor field;
        KrpcExt.Validate vld;

        FieldInfo(Descriptors.FieldDescriptor field, KrpcExt.Validate vld) {
            this.field = field;
            this.vld = vld;
        }
    }

    HashMap<String, List<FieldInfo>> fieldCache = new HashMap<>();

    public boolean prepare(Class<?> cls) {
        Builder b = ReflectionUtils.generateBuilder(cls);
        Descriptors.Descriptor desc = b.getDescriptorForType();
        return prepare(desc);
    }

    boolean prepare(Descriptors.Descriptor desc) {

        List<FieldInfo> l = new ArrayList<>();
        fieldCache.put(desc.getFullName(), l);

        for (Descriptors.FieldDescriptor field : desc.getFields()) {

            KrpcExt.Validate v = getVldOption(field);
            if (v != null) {
                l.add(new FieldInfo(field, v));
                continue;
            }

            if (field.getJavaType() == FieldDescriptor.JavaType.MESSAGE) {
                Descriptors.Descriptor sub = field.getMessageType();
                boolean has = prepare(sub);
                if (has) {
                    l.add(new FieldInfo(field, null));
                    continue;
                }
            }
        }

        return l.size() > 0;
    }

    KrpcExt.Validate getVldOption(Descriptors.FieldDescriptor field) {
        for (Map.Entry<Descriptors.FieldDescriptor, Object> entry : field.getOptions().getAllFields().entrySet()) {
            if (!entry.getKey().getFullName().equals("krpc.vld"))
                continue;
            KrpcExt.Validate v = (KrpcExt.Validate) entry.getValue();
            return v;
        }
        return null;
    }

    public String validate(Message message) {

        Descriptors.Descriptor desc = message.getDescriptorForType();
        List<FieldInfo> l = fieldCache.get(desc.getFullName());
        if (l == null || l.size() == 0) return null;

        for (FieldInfo fi : l) {

            KrpcExt.Validate v = fi.vld;
            Object fieldValue = message.getField(fi.field);

            if (fieldValue instanceof List) {
                if (v != null) {
                    boolean ok = doValidateArray(fieldValue, v);
                    if (!ok)
                        return fi.field.getFullName();
                }
                for (Object element : (List<?>) fieldValue) {
                    if (element instanceof Message) {
                        String res = validate((Message) element);
                        if (res != null) return res;
                    } else {
                        if (v != null) {
                            boolean ok = doValidateSingle(element, v);
                            if (!ok) return fi.field.getFullName();
                        }
                    }
                }
            } else if (fieldValue instanceof Message) {
                String res = validate((Message) fieldValue);
                if (res != null) return res;
            } else {
                if (v != null) {
                    boolean ok = doValidateSingle(fieldValue, v);
                    if (!ok)
                        return fi.field.getFullName();
                }
            }
        }

        return null;
    }

    boolean doValidateArray(Object fieldValue, KrpcExt.Validate v) {

        if (fieldValue == null)
            return false;

        if (!v.getArrlen().isEmpty()) {
            FieldValidator fv = getValidatorWithCache(TYPE_ARRLEN, v.getArrlen());
            boolean ok = fv.validate(fieldValue);
            if (!ok)
                return false;
        }

        return true;
    }

    boolean doValidateSingle(Object fieldValue, KrpcExt.Validate v) {

        if (fieldValue == null)
            return false;

        if (v.getRequired()) {
            boolean ok = requiredValidator.validate(fieldValue);
            if (!ok)
                return false;
        }
        if (!v.getMatch().isEmpty()) {
            FieldValidator fv = getValidatorWithCache(TYPE_MATCH, v.getMatch());
            boolean ok = fv.validate(fieldValue);
            if (!ok)
                return false;
        }
        if (!v.getValues().isEmpty()) {
            FieldValidator fv = getValidatorWithCache(TYPE_VALUES, v.getValues());
            boolean ok = fv.validate(fieldValue);
            if (!ok)
                return false;
        }
        if (!v.getLength().isEmpty()) {
            FieldValidator fv = getValidatorWithCache(TYPE_LENGTH, v.getLength());
            boolean ok = fv.validate(fieldValue);
            if (!ok)
                return false;
        }
        if (!v.getNrange().isEmpty()) {
            FieldValidator fv = getValidatorWithCache(TYPE_NRANGE, v.getNrange());
            boolean ok = fv.validate(fieldValue);
            if (!ok)
                return false;
        }
        if (!v.getSrange().isEmpty()) {
            FieldValidator fv = getValidatorWithCache(TYPE_SRANGE, v.getSrange());
            boolean ok = fv.validate(fieldValue);
            if (!ok)
                return false;
        }

        return true;
    }

    FieldValidator getValidatorWithCache(int type, String params) {
        String key = type + "###" + params;
        FieldValidator v = validatorCache.get(key);
        if (v != null)
            return v;
        v = getValidator(type, params);
        validatorCache.put(key, v);
        return v;
    }

    FieldValidator getValidator(int type, String params) {
        switch (type) {
            case TYPE_VALUES:
                return new ValuesValidator(params);
            case TYPE_LENGTH:
                return new LengthValidator(params);
            case TYPE_NRANGE:
                return new NrangeValidator(params);
            case TYPE_SRANGE:
                return new SrangeValidator(params);
            case TYPE_ARRLEN:
                return new ArrlenValidator(params);
            case TYPE_MATCH:
                return getMatchValidator(params);
            default:
                return null; // impossible
        }
    }

    FieldValidator getMatchValidator(String params) {
        switch (params) {
            case "int":
                return new IntValidator();
            case "long":
                return new LongValidator();
            case "double":
                return new DoubleValidator();
            case "date":
                return new DateValidator();
            case "timestamp":
                return new TimestampValidator();
            case "email":
                return new PatternValidator("^[A-Za-z0-9_.-]+@[a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+$");
            default:
                return new PatternValidator(params);
        }
    }

    static class RequiredValidator implements FieldValidator {
        @SuppressWarnings("rawtypes")
        public boolean validate(Object v) {
            if (v instanceof String) {
                String s = (String) v;
                return !s.isEmpty();
            }
            if (v instanceof List) {
                List l = (List) v;
                return l.size() > 0;
            }
            return true;
        }
    }

    static class ValuesValidator implements FieldValidator {
        HashSet<String> set = new HashSet<>();

        ValuesValidator(String values) {
            String[] ss = values.split(",");
            for (String s : ss) {
                s = s.trim();
                if (!s.isEmpty())
                    set.add(s);
            }
        }

        public boolean validate(Object v) {
            String s = v.toString();
            if (s.isEmpty())
                return false;
            return set.contains(s);
        }
    }

    static class LengthValidator implements FieldValidator {
        int min = 0;
        int max = Integer.MAX_VALUE;

        LengthValidator(String s) {
            int p = s.indexOf(",");
            if (p >= 0) {
                String s1 = s.substring(0, p);
                String s2 = s.substring(p + 1);
                min = TypeSafe.anyToInt(s1, 0);
                max = TypeSafe.anyToInt(s2, Integer.MAX_VALUE);
            } else {
                max = min = TypeSafe.anyToInt(s, -1);
            }
        }

        public boolean validate(Object v) {
            int len = v.toString().length();
            return len >= min && len <= max;
        }

    }

    static class ArrlenValidator implements FieldValidator {
        int min = 0;
        int max = Integer.MAX_VALUE;

        ArrlenValidator(String s) {
            int p = s.indexOf(",");
            if (p >= 0) {
                String s1 = s.substring(0, p);
                String s2 = s.substring(p + 1);
                min = TypeSafe.anyToInt(s1, 0);
                max = TypeSafe.anyToInt(s2, Integer.MAX_VALUE);
            } else {
                max = min = TypeSafe.anyToInt(s, -1);
            }
        }

        @SuppressWarnings("rawtypes")
        public boolean validate(Object v) {
            if (!(v instanceof List))
                return false;
            List l = (List) v;
            int len = l.size();
            return len >= min && len <= max;
        }

    }

    static class NrangeValidator implements FieldValidator {
        long min = Long.MIN_VALUE;
        long max = Long.MAX_VALUE;

        NrangeValidator(String s) {
            int p = s.indexOf(",");
            if (p >= 0) {
                String s1 = s.substring(0, p);
                String s2 = s.substring(p + 1);
                min = TypeSafe.anyToLong(s1, Long.MIN_VALUE);
                max = TypeSafe.anyToLong(s2, Long.MAX_VALUE);
            } else {
                max = min = TypeSafe.anyToLong(s, 0);
            }
        }

        public boolean validate(Object v) {
            long n = 0;
            if (v instanceof Integer) {
                n = (Integer) v;
            } else if (v instanceof Long) {
                n = (Long) v;
            } else if (v instanceof Number) {
                n = ((Number) v).longValue();
            } else {
                try {
                    n = Long.parseLong(v.toString());
                } catch (Exception e) {
                    return false;
                }
            }

            return n >= min && n <= max;
        }

    }

    static class SrangeValidator implements FieldValidator {
        String min = "";
        String max = "";

        SrangeValidator(String s) {
            int p = s.indexOf(",");
            if (p >= 0) {
                min = s.substring(0, p);
                max = s.substring(p + 1);
            } else {
                max = min = s;
            }
        }

        public boolean validate(Object v) {
            String s = v.toString();
            return s.compareTo(min) >= 0 && s.compareTo(max) <= 0;
        }

    }

    static class IntValidator implements FieldValidator {
        public boolean validate(Object v) {
            if (v instanceof Integer)
                return true;

            String s = v.toString();
            try {
                Integer.parseInt(s);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    static class LongValidator implements FieldValidator {
        public boolean validate(Object v) {
            if (v instanceof Integer || v instanceof Long)
                return true;
            String s = v.toString();
            try {
                Long.parseLong(s);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    static class DoubleValidator implements FieldValidator {
        public boolean validate(Object v) {
            if (v instanceof Integer || v instanceof Long || v instanceof Float || v instanceof Double)
                return true;

            String s = v.toString();
            try {
                Double.parseDouble(s);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    static class DateValidator implements FieldValidator {

        ThreadLocal<SimpleDateFormat> f = new ThreadLocal<SimpleDateFormat>() {
            public SimpleDateFormat initialValue() {
                return new SimpleDateFormat("yyyy-MM-dd");
            }
        };

        public boolean validate(Object v) {
            String s = v.toString();
            try {
                f.get().parse(s);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    static class TimestampValidator implements FieldValidator {

        ThreadLocal<SimpleDateFormat> f = new ThreadLocal<SimpleDateFormat>() {
            public SimpleDateFormat initialValue() {
                return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            }
        };

        public boolean validate(Object v) {
            String s = v.toString();
            try {
                f.get().parse(s);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    static class PatternValidator implements FieldValidator {
        Pattern pattern;

        PatternValidator(String s) {
            pattern = Pattern.compile(s);
        }

        public boolean validate(Object v) {
            String s = v.toString();
            return pattern.matcher(s).matches();
        }
    }

}
