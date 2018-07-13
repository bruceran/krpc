package krpc.rpc.web;

import krpc.rpc.core.Continue;

public interface AsyncPreParsePlugin {
    void asyncPreParse(WebContextData ctx, WebReq req, Continue<Integer> cont);
}
