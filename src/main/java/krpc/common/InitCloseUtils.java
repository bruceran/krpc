package krpc.common;

import java.util.List;

import krpc.rpc.core.StartStop;

public class InitCloseUtils {

    public static void init(Object o) {
    	if( o == null ) return;
    	if( o instanceof InitClose ) ((InitClose)o).init();
    }

    public static void close(Object o) {
    	if( o == null ) return;
    	if( o instanceof InitClose ) ((InitClose)o).close();
    }

    public static void start(Object o) {
    	if( o == null ) return;
    	if( o instanceof StartStop ) ((StartStop)o).start();
    }

    public static void stop(Object o) {
    	if( o == null ) return;
    	if( o instanceof StartStop ) ((StartStop)o).stop();
    }

    public static void init(List<Object> os) {
    	if( os == null ) return;
    	for(Object o: os) {
    		if( o == null ) continue;
        	if( o instanceof InitClose ) ((InitClose)o).init();
    	}
    }
    
    public static void close(List<Object> os) {
    	if( os == null ) return;
    	for(int i=os.size()-1;i>=0;--i) {
    		Object o = os.get(i);
    		if( o == null ) continue;
        	if( o instanceof InitClose ) ((InitClose)o).close();
    	}
    }    
    
    public static void start(List<Object> os) {
    	if( os == null ) return;
    	for(Object o: os) {
    		if( o == null ) continue;
        	if( o instanceof StartStop ) ((StartStop)o).start();
    	}
    }

    public static void stop(List<Object> os) {
    	if( os == null ) return;
    	for(int i=os.size()-1;i>=0;--i) {
    		Object o = os.get(i);
    		if( o == null ) continue;
        	if( o instanceof StartStop ) ((StartStop)o).stop();
    	}
    }        
}


