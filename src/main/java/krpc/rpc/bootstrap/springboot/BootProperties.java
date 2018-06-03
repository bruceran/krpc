package krpc.rpc.bootstrap.springboot;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import krpc.rpc.bootstrap.ApplicationConfig;
import krpc.rpc.bootstrap.ClientConfig;
import krpc.rpc.bootstrap.MonitorConfig;
import krpc.rpc.bootstrap.RefererConfig;
import krpc.rpc.bootstrap.RegistryConfig;
import krpc.rpc.bootstrap.ServerConfig;
import krpc.rpc.bootstrap.ServiceConfig;
import krpc.rpc.bootstrap.WebServerConfig;

@ConfigurationProperties(prefix = "krpc")
public class BootProperties {

	boolean autoStart = true;
	
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
	WebServerConfig webServer;
	List<WebServerConfig> webServers;
	
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
	public List<WebServerConfig> getWebServers() {
		return webServers;
	}
	public void setWebServers(List<WebServerConfig> webServers) {
		this.webServers = webServers;
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
	public WebServerConfig getWebServer() {
		return webServer;
	}
	public void setWebServer(WebServerConfig webServer) {
		this.webServer = webServer;
	}
	public boolean isAutoStart() {
		return autoStart;
	}
	public void setAutoStart(boolean autoStart) {
		this.autoStart = autoStart;
	}


}
