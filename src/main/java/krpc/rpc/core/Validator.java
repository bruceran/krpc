package krpc.rpc.core;

import com.google.protobuf.Message;

public interface Validator {

    boolean prepare(Class<?> cls);

    String validate(Message message); // return null means success, otherwise prompt string

    static public interface FieldValidator {
        boolean validate(Object v);
    }

}
