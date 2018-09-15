package krpc.rpc.bootstrap;

import krpc.rpc.core.*;

// used for "Cyclic Dependency"
public class RpcAppInitBean {

    private RpcApp rpcApp;
    private Bootstrap bootstrap;

    public RpcAppInitBean(Bootstrap bootstrap,RpcApp rpcApp) {
        this.bootstrap = bootstrap;
        this.rpcApp = rpcApp;
    }

    private boolean isImplObj(String interfaceName,Object impl ) {
        try {
            ReflectionUtils.checkInterface(interfaceName, impl);
            return true;
        } catch(Exception e) {
            return false;
        }
    }

    public void addServiceImpl(Object implObj) {
        for(ServiceConfig c: bootstrap.getServiceList()) {
            if( isImplObj(c.interfaceName,implObj) ) {
                c.setImpl(implObj);
                break;
            }
        }
    }

    public void postBuild() {
        bootstrap.postBuild();
    }

    public void init() {
        rpcApp.init();
    }

    public void postBuildAndInit() {
        bootstrap.postBuild();
        init();
    }

    public void close() {
        rpcApp.close();
    }

}
