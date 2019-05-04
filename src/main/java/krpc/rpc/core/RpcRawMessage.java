package krpc.rpc.core;

import io.netty.buffer.ByteBuf;
public class RpcRawMessage {

    int retCode;
    ByteBuf res;

    public RpcRawMessage(int retCode, ByteBuf res) {
        this.retCode = retCode;
        this.res = res;
    }

    public int getRetCode() {
        return retCode;
    }

    public void setRetCode(int retCode) {
        this.retCode = retCode;
    }

    public ByteBuf getRes() {
        return res;
    }

    public void setRes(ByteBuf res) {
        this.res = res;
    }
}
