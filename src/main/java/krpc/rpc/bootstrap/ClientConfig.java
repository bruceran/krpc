package krpc.rpc.bootstrap;

import java.util.ArrayList;
import java.util.List;

public class ClientConfig {

    String id;

    int pingSeconds = 60;
    int maxPackageSize = 1000000;
    int connectTimeout = 15000;
    int reconnectSeconds = 1;
    int ioThreads = 0;  // auto
    boolean nativeNetty = false;
    boolean enableEncrypt = false;

    int connections = 1;

    int notifyThreads = 0; // for future listener, 0=auto -1=no threads
    int notifyMaxThreads = 0;
    int notifyQueueSize = 10000;

    int threads = 0; // for reverse call, worker threads, 0=auto -1=no workthreads,use iothreads n=workthreads
    int maxThreads = 0;
    int queueSize = 10000;

    String plugins = ""; // comma seperated RpcPlugin names
    List<String> pluginParams = new ArrayList<>(); // config RpcPlugins if needed

    String connectionPlugin = "";

    public ClientConfig() {
    }

    public ClientConfig(String id) {
        this.id = id;
    }

    public ClientConfig addPluginParams(String params) {
        pluginParams.add(params);
        return this;
    }

    public String getId() {
        return id;
    }

    public ClientConfig setId(String id) {
        this.id = id;
        return this;
    }

    public int getPingSeconds() {
        return pingSeconds;
    }

    public ClientConfig setPingSeconds(int pingSeconds) {
        this.pingSeconds = pingSeconds;
        return this;
    }

    public int getMaxPackageSize() {
        return maxPackageSize;
    }

    public ClientConfig setMaxPackageSize(int maxPackageSize) {
        this.maxPackageSize = maxPackageSize;
        return this;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public ClientConfig setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    public int getReconnectSeconds() {
        return reconnectSeconds;
    }

    public ClientConfig setReconnectSeconds(int reconnectSeconds) {
        this.reconnectSeconds = reconnectSeconds;
        return this;
    }

    public int getIoThreads() {
        return ioThreads;
    }

    public ClientConfig setIoThreads(int ioThreads) {
        this.ioThreads = ioThreads;
        return this;
    }

    public int getNotifyThreads() {
        return notifyThreads;
    }

    public ClientConfig setNotifyThreads(int notifyThreads) {
        this.notifyThreads = notifyThreads;
        return this;
    }

    public int getNotifyQueueSize() {
        return notifyQueueSize;
    }

    public ClientConfig setNotifyQueueSize(int notifyQueueSize) {
        this.notifyQueueSize = notifyQueueSize;
        return this;
    }

    public int getThreads() {
        return threads;
    }

    public ClientConfig setThreads(int threads) {
        this.threads = threads;
        return this;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public ClientConfig setQueueSize(int queueSize) {
        this.queueSize = queueSize;
        return this;
    }

    public int getConnections() {
        return connections;
    }

    public ClientConfig setConnections(int connections) {
        this.connections = connections;
        return this;
    }

    public int getMaxThreads() {
        return maxThreads;
    }

    public ClientConfig setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
        return this;
    }

    public int getNotifyMaxThreads() {
        return notifyMaxThreads;
    }

    public ClientConfig setNotifyMaxThreads(int notifyMaxThreads) {
        this.notifyMaxThreads = notifyMaxThreads;
        return this;
    }

    public String getPlugins() {
        return plugins;
    }

    public ClientConfig setPlugins(String plugins) {
        this.plugins = plugins;
        return this;
    }

    public List<String> getPluginParams() {
        return pluginParams;
    }

    public ClientConfig setPluginParams(List<String> pluginParams) {
        this.pluginParams = pluginParams;
        return this;
    }

    public String getConnectionPlugin() {
        return connectionPlugin;
    }

    public ClientConfig setConnectionPlugin(String connectionPlugin) {
        this.connectionPlugin = connectionPlugin;
        return this;
    }

    public boolean isNativeNetty() {
        return nativeNetty;
    }

    public ClientConfig setNativeNetty(boolean nativeNetty) {
        this.nativeNetty = nativeNetty;
        return this;
    }

    public boolean isEnableEncrypt() {
        return enableEncrypt;
    }

    public ClientConfig setEnableEncrypt(boolean enableEncrypt) {
        this.enableEncrypt = enableEncrypt;
        return this;
    }
}
