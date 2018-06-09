package krpc.test.rpc.xml;
 
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
 
import krpc.rpc.web.PreParsePlugin;
import krpc.rpc.web.WebContextData;
import krpc.rpc.web.WebPlugin;
import krpc.rpc.web.WebReq;

public class TestBeanWebPlugin implements WebPlugin,   PreParsePlugin  {

	static Logger log = LoggerFactory.getLogger(TestBeanWebPlugin.class);

	public TestBeanWebPlugin() {
		log.info("TestBeanWebPlugin created");
	}
	
	@Override
	public int preParse(WebContextData ctx, WebReq req) {
		log.info("preParse called");
		return 0;
	}
 
}
