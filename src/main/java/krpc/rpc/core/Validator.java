package krpc.rpc.core;

import com.google.protobuf.Message;

public interface Validator {

    boolean prepare(Class<?> cls);

    ValidateResult validate(Message message);

    static public interface FieldValidator {
        boolean validate(Object v);
    }

}
