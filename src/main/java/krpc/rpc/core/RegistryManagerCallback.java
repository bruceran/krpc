package krpc.rpc.core;

import java.util.Map;

public interface RegistryManagerCallback {
	void addrChanged(Map<Integer,String> addrsMap); // addrs for all serviceIds
	void routeChanged(String rules);
}
