package krpc.rpc.core;

import java.util.List;
import java.util.Objects;

public class DynamicRouteConfig {

	int serviceId;
	boolean disabled; // service is disabled or not
	List<AddrWeight> weights; // weight for each service instance, addr=ip:port
	List<RouteRule> rules; // route rules

	public boolean equals(final java.lang.Object obj) {
		if (obj == this) {
			return true;
		}
		if (!(obj instanceof DynamicRouteConfig)) {
			return false;
		}
		DynamicRouteConfig other = (DynamicRouteConfig) obj;

		boolean result = true;
		result = result && serviceId == other.serviceId;
		result = result && disabled == other.disabled;
		result = result && Objects.equals(weights,other.weights);
		result = result && Objects.equals(rules,other.rules);

		return result;
	}

	public int hashCode() {
		return Objects.hash(serviceId, disabled,weights,rules);
	}

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

	public List<AddrWeight> getWeights() {
		return weights;
	}

	public void setWeights(List<AddrWeight> weights) {
		this.weights = weights;
	}

	public List<RouteRule> getRules() {
		return rules;
	}

	public void setRules(List<RouteRule> rules) {
		this.rules = rules;
	}

	public static class AddrWeight {

		String addr = "";
		int weight;

		public boolean equals(final java.lang.Object obj) {
			if (obj == this) {
				return true;
			}
			if (!(obj instanceof AddrWeight)) {
				return false;
			}
			AddrWeight other = (AddrWeight) obj;

			boolean result = true;
			result = result && Objects.equals(addr, other.addr);
			result = result && weight == other.weight;
			return result;
		}

		public int hashCode() {
			return Objects.hash(addr, weight);
		}

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

	public static class RouteRule implements Comparable<RouteRule> {

		String from = "";
		String to = "";
		int priority;

		public int compareTo(RouteRule rr) {
			if( priority < rr.priority ) return -1;
			if( priority > rr.priority ) return 1;
			return from.compareTo(rr.from);
		}

		public boolean equals(final java.lang.Object obj) {
			if (obj == this) {
				return true;
			}
			if (!(obj instanceof RouteRule)) {
				return false;
			}
			RouteRule other = (RouteRule) obj;

			boolean result = true;
			result = result && Objects.equals(from, other.from);
			result = result && Objects.equals(to, other.to);
			result = result && priority == other.priority;
			return result;
		}

		public int hashCode() {
			return Objects.hash(from, to, priority);
		}

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
