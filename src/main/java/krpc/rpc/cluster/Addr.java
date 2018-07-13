package krpc.rpc.cluster;

public interface Addr {

    String getAddr(); // return ip:port

    int getPendingCalls(int serviceId);  // used for least active policy

}
