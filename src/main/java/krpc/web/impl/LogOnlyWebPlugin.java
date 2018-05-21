package krpc.web.impl;

import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import krpc.core.Continue;
import krpc.core.InitClose;
import krpc.web.WebContextData;
import krpc.web.WebReq;
import krpc.web.WebRes;
import krpc.web.plugin.*;

public class LogOnlyWebPlugin implements WebPlugin, RenderPlugin, PreRenderPlugin, PreParsePlugin, PostSessionPlugin,
		PostRenderPlugin, PostParsePlugin, ParserPlugin, AsyncPostSessionPlugin, AsyncPostParsePlugin,InitClose {

	static Logger log = LoggerFactory.getLogger(LogOnlyWebPlugin.class);
	
	JacksonJsonConverter c = new JacksonJsonConverter();
	Timer t = new Timer();
	
	public void init() {
		log.info("init called");
	}
	public void close() {
		t.cancel();
		log.info("close called");
	}
	
	@Override
	public void asyncPostParse(int serviceId, int msgId, WebReq req, Continue<Integer> cont) {
		t.schedule( new TimerTask() {
			public void run() {
				log.info("asyncPostParse called");
				cont.readyToContinue(0);
			}
		}, 0);
		
	}

	@Override
	public void asyncPostSession(WebContextData ctx, WebReq req, Continue<Integer> cont) {
		t.schedule( new TimerTask() {
			public void run() {
				log.info("asyncPostSession called");
				cont.readyToContinue(0);
			}
		}, 0);
	}

	@Override
	public int parse(int serviceId, int msgId, WebReq req) {
		log.info("parse called");
		return 0;
	}

	@Override
	public int postParse(int serviceId, int msgId, WebReq req) {
		log.info("postParse called");
		return 0;
	}

	@Override
	public void postRender(WebContextData ctx, WebReq req, WebRes res) {
		log.info("postRender called");
	}

	@Override
	public int postSession(WebContextData ctx, WebReq req) {
		log.info("postSession called");
		return 0;
	}

	@Override
	public int preParse(int serviceId, int msgId, WebReq req) {
		log.info("preParse called");
		return 0;
	}

	@Override
	public void preRender(WebContextData ctx, WebReq req, WebRes res) {
		log.info("preRender called");
	}

	@Override
	public void render(WebContextData ctx, WebReq req, WebRes res) {
		res.setContentType("application/json");
		res.setContent(c.fromMap(res.getResults()));
		log.info("render called");
	}
}
