package krpc.cluster;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;

import com.google.protobuf.Message;

public class ServiceInfo {

	private int serviceId;
	private LoadBalance policy;

	private Random retryRand = new Random();
	private HashSet<String> all = new HashSet<String>();
	private ArrayList<AddrInfo> alive = new ArrayList<AddrInfo>();

	ServiceInfo(int serviceId, LoadBalance policy) {
		this.serviceId = serviceId;
		this.policy = policy;
	}

	synchronized AddrInfo nextAddr(int msgId, Message req, String excludeConnIds) {
		if (alive.size() == 0)
			return null;

		// todo router logic

		if (alive.size() == 1)
			return alive.get(0);

		if (excludeConnIds == null || excludeConnIds.isEmpty() ) {
			if ( policy == null ) return alive.get(0);
			Addr[] as = alive.toArray(new Addr[0]);
			int idx = policy.select(as, serviceId, msgId, req);
			return alive.get(idx);
		}

		ArrayList<AddrInfo> list = null;
		String[] ss = excludeConnIds.split(",");
		for (int i = 0; i < ss.length; ++i) {
			ss[i] = getAddr(ss[i]);
		}

		for (AddrInfo ci : alive) {
			if (found(ss, ci.getAddr()))
				continue;
			if (list == null)
				list = new ArrayList<AddrInfo>();
			list.add(ci);
		}
		if (list.size() == 0)
			return null;
		if (list.size() == 1)
			return list.get(0);

		int k = retryRand.nextInt(list.size()); // always use random for retry
		return list.get(k);
	}

	String getAddr(String connId) {
		int p = connId.lastIndexOf(":");
		return connId.substring(0, p);
	}

	synchronized void copyTo(HashSet<String> newSet) {
		newSet.addAll(all);
	}

	synchronized HashSet<String> mergeFrom(HashSet<String> newSet) {
		HashSet<String> toBeAdded = new HashSet<String>();
		toBeAdded.addAll(newSet);
		toBeAdded.removeAll(all);
		all.addAll(toBeAdded);
		return toBeAdded;
	}

	synchronized int foundAlive(String addr) {
		int size = alive.size();
		for (int i = 0; i < size; ++i) {
			if (alive.get(i).getAddr().equals(addr)) {
				return i;
			}
		}
		return -1;
	}

	synchronized void remove(AddrInfo ai) {
		int idx = foundAlive(ai.getAddr());
		if (idx >= 0)
			alive.remove(idx);
		all.remove(ai.getAddr());
	}

	synchronized void updateAliveConn(AddrInfo ai, boolean connected) {
		if (!all.contains(ai.getAddr()))
			return;
		int idx = foundAlive(ai.getAddr());

		if (connected) {
			if (idx < 0) {
				alive.add(ai);
			}
		} else {
			if (idx >= 0) {
				alive.remove(idx);
			}
		}
	}

	boolean found(String[] ss, String s) {
		for (int i = 0; i < ss.length; ++i) {
			if (s.equals(ss[i])) {
				return true;
			}
		}
		return false;
	}

}
