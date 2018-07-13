package krpc.rpc.web;

import com.google.protobuf.Message;

public interface RpcDataConverter {
    Message generateData(WebContextData ctx, DefaultWebReq req, boolean dynamic);

    void parseData(WebContextData ctx, Message data, DefaultWebRes res);
}
