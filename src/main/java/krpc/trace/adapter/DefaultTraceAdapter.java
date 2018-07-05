package krpc.trace.adapter;

import krpc.trace.DummyTraceAdapter;
import krpc.trace.Span;
import krpc.trace.TraceContext;

public class DefaultTraceAdapter extends DummyTraceAdapter {

	public void send(TraceContext ctx, Span span) {
		// do nothing
	}
	

} 
