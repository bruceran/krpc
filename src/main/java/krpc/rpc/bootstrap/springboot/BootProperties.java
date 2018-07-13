package krpc.rpc.bootstrap.springboot;

import krpc.rpc.bootstrap.*;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "krpc", ignoreUnknownFields = false)
public class BootProperties {

    boolean enabled = false;

    ApplicationConfig application;
    MonitorConfig monitor;

    RegistryConfig registry;
    List<RegistryConfig> registries;
    ServerConfig server;
    List<ServerConfig> servers;
    ClientConfig client;
    List<ClientConfig> clients;
    ServiceConfig service;
    List<ServiceConfig> services;
    RefererConfig referer;
    List<RefererConfig> referers;
    WebServerConfig webserver;
    List<WebServerConfig> webservers;

    public ApplicationConfig getApplication() {
        return application;
    }

    public void setApplication(ApplicationConfig application) {
        this.application = application;
    }

    public MonitorConfig getMonitor() {
        return monitor;
    }

    public void setMonitor(MonitorConfig monitor) {
        this.monitor = monitor;
    }

    public List<RegistryConfig> getRegistries() {
        return registries;
    }

    public void setRegistries(List<RegistryConfig> registries) {
        this.registries = registries;
    }

    public List<ServerConfig> getServers() {
        return servers;
    }

    public void setServers(List<ServerConfig> servers) {
        this.servers = servers;
    }

    public List<ClientConfig> getClients() {
        return clients;
    }

    public void setClients(List<ClientConfig> clients) {
        this.clients = clients;
    }

    public List<ServiceConfig> getServices() {
        return services;
    }

    public void setServices(List<ServiceConfig> services) {
        this.services = services;
    }

    public List<RefererConfig> getReferers() {
        return referers;
    }

    public void setReferers(List<RefererConfig> referers) {
        this.referers = referers;
    }

    public RegistryConfig getRegistry() {
        return registry;
    }

    public void setRegistry(RegistryConfig registry) {
        this.registry = registry;
    }

    public ServerConfig getServer() {
        return server;
    }

    public void setServer(ServerConfig server) {
        this.server = server;
    }

    public ClientConfig getClient() {
        return client;
    }

    public void setClient(ClientConfig client) {
        this.client = client;
    }

    public ServiceConfig getService() {
        return service;
    }

    public void setService(ServiceConfig service) {
        this.service = service;
    }

    public RefererConfig getReferer() {
        return referer;
    }

    public void setReferer(RefererConfig referer) {
        this.referer = referer;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public WebServerConfig getWebserver() {
        return webserver;
    }

    public void setWebserver(WebServerConfig webserver) {
        this.webserver = webserver;
    }

    public List<WebServerConfig> getWebservers() {
        return webservers;
    }

    public void setWebservers(List<WebServerConfig> webservers) {
        this.webservers = webservers;
    }


}
