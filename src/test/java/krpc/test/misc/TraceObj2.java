package krpc.test.misc;

import krpc.trace.Span;
import krpc.trace.Trace;
import krpc.trace.sniffer.Advice;
import krpc.trace.sniffer.AdviceInstance;

public class TraceObj2 {

    public int notsayHello123(){
        System.out.println("hello !!!!");
        int i =220;
        if( i < 10)
        	throw new RuntimeException("hello exception");
        return -1;
    }
    
    public int sayHello123(){
        System.out.println("hello !!!!");
        int i =220;
        if( i < 10)
        	throw new RuntimeException("hello exception");
        return -1;
    }
    
    public int sayHello(){
        System.out.println("hello !!!!");
        int i =220;
        if( i < 10)
        	throw new RuntimeException("hello exception");
        return -1;
    }
    
    public void sayHello2(int a,int b) throws Exception {
        System.out.println("hello 2 !!!!");
        throw new RuntimeException("hello 2 exception");
    }
    

	public static void initSniffer() {
		AdviceInstance.instance  = new Advice() {
	
			public void start(String type, String action) {
	System.out.println("TraceSniffer start called in app");			
				Trace.start(type, action);
			}
	
			public long stop(boolean ok) {
	System.out.println("TraceSniffer stop called in app");			
				return Trace.stop(ok);
			}
	
			public void logException(Throwable e) {
	System.out.println("TraceSniffer logException called in app");		
				Trace.logException(e);
			}
			
		};
	}
	    
    public static void main(String[]args){
    	
    	initSniffer();
    	
    	Trace.start("Test","Test");
    	
    	TraceObj2 t=new TraceObj2();
    	
    	try {
    		t.sayHello();
    	} catch(Exception e) {
    		}
    	
    	try {
    		t.sayHello2(111,222);
    	} catch(Exception e) {
		}
    	
        Span s = Trace.currentSpan();
        System.out.println("children="+s.getChildren().size());
        System.out.println("span 1 ts="+s.getChildren().get(0).getTimeUsedMicros());
        System.out.println("span 1 status="+s.getChildren().get(0).getStatus());
        System.out.println("span 2 ts="+s.getChildren().get(1).getTimeUsedMicros());
        System.out.println("span 2 status="+s.getChildren().get(1).getStatus());
        Trace.stop();
    }
 
}

