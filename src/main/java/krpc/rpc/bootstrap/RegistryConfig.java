package krpc.rpc.bootstrap;

public class RegistryConfig {

    String id;

    String type; // typeName(etcd,zookeeper,...), must be registered in Bootstrap.registryTypes
    String addrs;

    boolean enableRegist = true;
    boolean enableDiscover = true;

    String aclToken;

    String params;

    public RegistryConfig() {
    }

    public RegistryConfig(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public RegistryConfig setId(String id) {
        this.id = id;
        return this;
    }

    public String getType() {
        return type;
    }

    public RegistryConfig setType(String type) {
        this.type = type;
        return this;
    }

    public String getAddrs() {
        return addrs;
    }

    public RegistryConfig setAddrs(String addrs) {
        this.addrs = addrs;
        return this;
    }

    public boolean isEnableRegist() {
        return enableRegist;
    }

    public RegistryConfig setEnableRegist(boolean enableRegist) {
        this.enableRegist = enableRegist;
        return this;
    }

    public boolean isEnableDiscover() {
        return enableDiscover;
    }

    public RegistryConfig setEnableDiscover(boolean enableDiscover) {
        this.enableDiscover = enableDiscover;
        return this;
    }

    public String getParams() {
        return params;
    }

    public RegistryConfig setParams(String params) {
        this.params = params;
        return this;
    }

    public String getAclToken() {
        return aclToken;
    }

    public RegistryConfig setAclToken(String aclToken) {
        this.aclToken = aclToken;
        return this;
    }
}
