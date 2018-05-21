package krpc.core;

import com.google.protobuf.Message;

import krpc.core.proto.RpcMeta;

public class RpcData {
	
	RpcMeta meta;
	Message body;

	public RpcData() {
	}

	public RpcData(RpcMeta meta) {
		this.meta = meta;
	}

	public RpcData(RpcMeta meta, Message body) {
		this.meta = meta;
		this.body = body;
	}

	public Message getBody() {
		return body;
	}

	public void setBody(Message body) {
		this.body = body;
	}
	
	public RpcMeta getMeta() {
		return meta;
	}

	public void setMeta(RpcMeta meta) {
		this.meta = meta;
	}	
}
