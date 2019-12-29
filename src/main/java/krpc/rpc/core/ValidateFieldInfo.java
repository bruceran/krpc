package krpc.rpc.core;

import com.google.protobuf.Descriptors;
import krpc.KrpcExt;

public class ValidateFieldInfo {
    public Descriptors.FieldDescriptor field;
    public KrpcExt.Validate vld;

    public ValidateFieldInfo(Descriptors.FieldDescriptor field, KrpcExt.Validate vld) {
        this.field = field;
        this.vld = vld;
    }
}
