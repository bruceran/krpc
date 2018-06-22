package krpc.test.misc;

import krpc.trace.Span;
import krpc.trace.Trace;

public class TraceObj {


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
    
    public static void main(String[]args){
    	Trace.start("Test","Test");
    	
    	TraceObj t=new TraceObj();
    	
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

