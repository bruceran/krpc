package krpc.rpc.web;

import krpc.common.Plugin;

import java.util.List;

public interface AutoRoutePlugin extends Plugin {
    List<WebUrl> generateWebUrls();
    List<WebDir> generateWebDirs();
}
