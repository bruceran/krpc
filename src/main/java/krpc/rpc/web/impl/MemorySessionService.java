package krpc.rpc.web.impl;

import krpc.common.InitClose;
import krpc.common.Plugin;
import krpc.rpc.core.Continue;
import krpc.rpc.web.SessionService;
import krpc.rpc.web.WebPlugin;

import java.util.ArrayList;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

public class MemorySessionService implements WebPlugin, SessionService, InitClose {

    static class SessionInfo {
        String sessionId;
        ConcurrentHashMap<String, String> session;
        long expiredTime;

        SessionInfo(String sessionId) {
            this.sessionId = sessionId;
            session = new ConcurrentHashMap<>();
        }
    }

    private int expireSeconds = 600;

    ConcurrentHashMap<String, SessionInfo> allSessions = new ConcurrentHashMap<>();
    Timer t;

    public void config(String paramsStr) {
        Map<String, String> params = Plugin.defaultSplitParams(paramsStr);
        String s = params.get("expireSeconds");
        if (s != null && !s.isEmpty())
            expireSeconds = Integer.parseInt(s);
    }

    public void init() {
        t = new Timer();
        t.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                MemorySessionService.this.clearExpired();
            }
        }, 60000, 60000);
    }

    public void close() {
        t.cancel();
    }

    void clearExpired() {
        long now = System.currentTimeMillis();
        ArrayList<SessionInfo> items = new ArrayList<SessionInfo>();
        items.addAll(allSessions.values());
        for (SessionInfo si : items) {
            if (si.expiredTime <= now) {
                allSessions.remove(si.sessionId);
            }
        }
    }

    public void load(String sessionId, Map<String, String> values, Continue<Integer> cont) {
        long now = System.currentTimeMillis();
        SessionInfo info = allSessions.get(sessionId);
        if (info != null && info.expiredTime > now) {
            values.putAll(info.session);
        }
        if (cont != null) {
            cont.readyToContinue(0);
        }
    }

    public void update(String sessionId, Map<String, String> values, Continue<Integer> cont) {

        SessionInfo info = allSessions.get(sessionId);
        if (info == null) {
            info = new SessionInfo(sessionId);
        }
        info.session.putAll(values);

        long now = System.currentTimeMillis();
        info.expiredTime = now + expireSeconds * 1000;
        allSessions.put(sessionId, info);

        if (cont != null) {
            cont.readyToContinue(0);
        }
    }

    public void remove(String sessionId, Continue<Integer> cont) {

        allSessions.remove(sessionId);

        if (cont != null) {
            cont.readyToContinue(0);
        }

    }
}
