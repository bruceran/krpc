package krpc.rpc.core;

import io.netty.buffer.ByteBuf;
import krpc.rpc.core.proto.RpcMeta;

public interface RpcCodec {

    void configZip(int serviceId, int zip, int minSizeToZip);

    RpcMeta decodeMeta(ByteBuf bb); // exception will close connection

    RpcData decodeBody(RpcMeta meta, ByteBuf leftBuff,String key); // exception will return response

    void encode(RpcData data, ByteBuf bb,String key); // bb paramter is to use netty4's pooled buffer

    void getReqHeartBeat(ByteBuf bb); // bb parameter is to use netty4's pooled buffer

    void getResHeartBeat(ByteBuf bb); // bb parameter is to use netty4's pooled buffer

    int getSize(RpcData data);
}
