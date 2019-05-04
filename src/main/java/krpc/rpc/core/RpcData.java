package krpc.rpc.core;

import com.google.protobuf.Message;
import io.netty.buffer.ByteBuf;
import krpc.rpc.core.proto.RpcMeta;

public class RpcData {

    RpcMeta meta;
    Object body; // Message or ByteBuf
    boolean isRaw;

    private RpcData() {
    }

    public RpcData(RpcMeta meta) {
        this.meta = meta;
        isRaw = true;
    }

    public RpcData(RpcMeta meta, Message body) {
        this.meta = meta;
        this.body = body;
        this.isRaw = false;
    }

    public RpcData(RpcMeta meta, ByteBuf body) {
        this.meta = meta;
        this.body = body;
        this.isRaw = true;
    }

    public boolean isRaw() { return isRaw; }

    public Message asMessage() {
        if( isRaw ) return null;
        return (Message)body;
    }

    public ByteBuf asByteBuf() {
        if( !isRaw ) return null;
        return (ByteBuf)body;
    }

    public Object getBody() {
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
    }

    public RpcMeta getMeta() {
        return meta;
    }

    public void setMeta(RpcMeta meta) {
        this.meta = meta;
    }
}
