package krpc.cluster;

import java.util.List;

import com.google.protobuf.Message;

public interface Router {
	List<AddrInfo> route(List<AddrInfo> addrs,int serviceId,int msgId,Message req);
}
