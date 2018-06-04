package krpc.rpc.web.impl;

import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import krpc.common.InitClose;
import krpc.common.Json;
import krpc.rpc.core.Continue;
import krpc.rpc.web.AsyncPostParsePlugin;
import krpc.rpc.web.AsyncPostSessionPlugin;
import krpc.rpc.web.ParserPlugin;
import krpc.rpc.web.PostParsePlugin;
import krpc.rpc.web.PostRenderPlugin;
import krpc.rpc.web.PostSessionPlugin;
import krpc.rpc.web.PreParsePlugin;
import krpc.rpc.web.PreRenderPlugin;
import krpc.rpc.web.RenderPlugin;
import krpc.rpc.web.WebContextData;
import krpc.rpc.web.WebPlugin;
import krpc.rpc.web.WebReq;
import krpc.rpc.web.WebRes;

public class LogOnlyWebPlugin implements WebPlugin, RenderPlugin, PreRenderPlugin, PreParsePlugin, PostSessionPlugin,
		PostRenderPlugin, PostParsePlugin, ParserPlugin, AsyncPostSessionPlugin, AsyncPostParsePlugin,InitClose {

	static Logger log = LoggerFactory.getLogger(LogOnlyWebPlugin.class);

	Timer t;
	
	public void init() {
		 t = new Timer();
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
		res.setContent(Json.toJson(res.getResults()));
		log.info("render called");
	}
}
