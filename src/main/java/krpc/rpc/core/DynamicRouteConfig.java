package krpc.rpc.core;

import java.util.List;

public class DynamicRouteConfig {

	int serviceId;
	boolean disabled; // service is disabled or not
	List<AddrWeight> weight; // weight for each service instance, addr=ip:port
	List<RouteRule> rules; // route rules
	
	// todo equals,,hashcode
	
	public int getServiceId() {
		return serviceId;
	}

	public void setServiceId(int serviceId) {
		this.serviceId = serviceId;
	}

	public boolean isDisabled() {
		return disabled;
	}

	public void setDisabled(boolean disabled) {
		this.disabled = disabled;
	}

	public List<AddrWeight> getWeight() {
		return weight;
	}

	public void setWeight(List<AddrWeight> weight) {
		this.weight = weight;
	}

	public List<RouteRule> getRules() {
		return rules;
	}

	public void setRules(List<RouteRule> rules) {
		this.rules = rules;
	}

	public static class AddrWeight {

		String addr;
		int weight; // -1 means disable the addr

		public String getAddr() {
			return addr;
		}

		public void setAddr(String addr) {
			this.addr = addr;
		}

		public int getWeight() {
			return weight;
		}

		public void setWeight(int weight) {
			this.weight = weight;
		}

	}

	public static class RouteRule {

		String from;
		String to;
		int priority;

		public String getFrom() {
			return from;
		}

		public void setFrom(String from) {
			this.from = from;
		}

		public String getTo() {
			return to;
		}

		public void setTo(String to) {
			this.to = to;
		}

		public int getPriority() {
			return priority;
		}

		public void setPriority(int priority) {
			this.priority = priority;
		}

	}


}
