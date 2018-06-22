package krpc.trace.sniffer;

import java.lang.instrument.Instrumentation;

public class TraceSniffer  {
	
    public static void premain(String agentArgs,Instrumentation inst){
        inst.addTransformer(new TraceTransformer(agentArgs));
    }
    
} 
