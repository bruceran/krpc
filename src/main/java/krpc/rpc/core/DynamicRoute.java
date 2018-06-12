package krpc.rpc.core;

import java.util.List;

public interface DynamicRoute extends Plugin {
	
	 List<AddrWeight> getWeights(int serviceId,String serviceName,String group); 
	 List<RouteRule> getRules(int serviceId,String serviceName,String group);
	 
	 public static class AddrWeight {
			
			String addr;
			int weight;
			
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
