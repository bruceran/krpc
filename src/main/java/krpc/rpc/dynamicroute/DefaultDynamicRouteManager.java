package krpc.rpc.dynamicroute;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import krpc.common.InitClose;
import krpc.common.InitCloseUtils;
import krpc.common.Json;
import krpc.common.StartStop;
import krpc.rpc.core.DynamicRoute;
import krpc.rpc.core.DynamicRouteManager;
import krpc.rpc.core.DynamicRouteManagerCallback;
import krpc.rpc.core.Registry;
import krpc.rpc.core.RegistryManager;
import krpc.rpc.core.RegistryManagerCallback;
import krpc.rpc.core.ServiceMetas;

public class DefaultDynamicRouteManager implements DynamicRouteManager,InitClose,StartStop {
	
	static Logger log = LoggerFactory.getLogger(DefaultDynamicRouteManager.class);
	
	private String dataDir;
	private String localFile = "routes.cache";
	Map<String,String> localData = new HashMap<>();
	
	private int startInterval = 1000;
	private int checkInterval = 1000;
	
	DynamicRoute dynamicRoute;
	ServiceMetas serviceMetas;

	Timer timer;

	public DefaultDynamicRouteManager(String dataDir) {
		this.dataDir = dataDir;
	}

    public void addConfig(int serviceId,String group,DynamicRouteManagerCallback callback) {
    	
    }
    
    public void init() {

	 

    }
    
    public void start() {
	 
			
    }
    
    public void stop() {
    	
     
    }
    
    public void close() {
 
    }

	public DynamicRoute getDynamicRoute() {
		return dynamicRoute;
	}

	public void setDynamicRoute(DynamicRoute dynamicRoute) {
		this.dynamicRoute = dynamicRoute;
	}

}

