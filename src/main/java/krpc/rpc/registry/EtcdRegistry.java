package krpc.rpc.registry;

public class EtcdRegistry extends AbstractHttpRegistry {

	
	public void register(int serviceId,String serviceName,String group,String addr) {
		if( !enableRegist ) return;
	}
	public void deregister(int serviceId,String serviceName,String group) {
		if( !enableRegist ) return;
	}	
	public String discover(int serviceId,String serviceName,String group) {	
		if( !enableDiscover ) return null;
		return "127.0.0.1:5600";
	}
}

