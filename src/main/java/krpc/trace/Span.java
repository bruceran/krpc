package krpc.trace;

import java.util.List;
import java.util.Map;

public interface Span {

	public Span newChild(String type,String action);
    public long stop();
    public long stop(boolean ok);
	public long stop(String result);
	public void logEvent(String type,String action,String status,String data);
	public void logException(Throwable c);
	public void logException(String message,Throwable c);
	public void tag(String key,String value);
	public void setRemoteAddr(String addr);

	public String getRpcId();
	public String getType();
	public String getAction();
	public long getStartMicros();
	public long getTimeUsedMicros();
	public String getStatus();
	public String getRemoteAddr();
	public Map<String,String> getTags();
	public List<Event> getEvents();
	public List<Span> getChildren(); 
	
}
