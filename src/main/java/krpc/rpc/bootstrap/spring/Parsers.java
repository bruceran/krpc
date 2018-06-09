package krpc.rpc.bootstrap.spring;

import java.util.ArrayList;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.NamespaceHandlerSupport;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import krpc.rpc.bootstrap.MethodConfig;
import krpc.rpc.bootstrap.MonitorConfig;

public class Parsers extends NamespaceHandlerSupport {
    @Override
    public void init() {
        registerBeanDefinitionParser("application", new ApplicationConfigBeanParser());
        registerBeanDefinitionParser("registry", new RegistryConfigBeanParser());
        registerBeanDefinitionParser("monitor", new MonitorConfigBeanParser());
        registerBeanDefinitionParser("server", new ServerConfigBeanParser());
        registerBeanDefinitionParser("webserver", new WebServerConfigBeanParser());
        registerBeanDefinitionParser("client", new ClientConfigBeanParser());
        registerBeanDefinitionParser("service", new ServiceConfigBeanParser());
        registerBeanDefinitionParser("referer", new RefererConfigBeanParser());
    }
}

class BaseParser<T> implements BeanDefinitionParser {

    Class<T> beanClass;
    
    String[] attributes;
    boolean hasMethods = false;
    boolean hasPlugins = false;
    boolean hasId = true;
    
    String beanId;

    public BeanDefinition parse(Element root, ParserContext parserContext) {

        RootBeanDefinition bd = new RootBeanDefinition();
        bd.setBeanClass(beanClass);
        bd.setLazyInit(false);

        if( hasId ) {
            beanId = parseId(root, parserContext, bd);        	
        }

        if( attributes != null ) {
            for(String name:attributes) {
                String value = root.getAttribute(name);
                if( value != null && value.length() > 0 )
                	bd.getPropertyValues().addPropertyValue(name, value);
            }        	
        }

        if( hasMethods ) 
        	parseMethod(root,parserContext,bd);
        
        if( hasPlugins ) 
        	parsePlugin(root,parserContext,bd);
        
        return bd;
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
	void parseMethod(Element root, ParserContext parserContext, RootBeanDefinition bd) {
    	NodeList nodeList = root.getChildNodes();
    	if (nodeList == null || nodeList.getLength() == 0) return;
	 
    	MethodConfigBeanParser methodParser = new MethodConfigBeanParser();
    	
    	ManagedList methods = new ManagedList();
    	for (int i = 0; i < nodeList.getLength(); i++) {
    		Node node = nodeList.item(i);
	         if (node instanceof Element) {
	             Element element = (Element) node;
	             if ("method".equals(node.getNodeName()) || "method".equals(node.getLocalName())) {
					 String pattern = element.getAttribute("pattern");
					 if (pattern == null || pattern.isEmpty() ) {
					     throw new RuntimeException("method pattern must be specified");
					 }
					 BeanDefinition methodBeanDefinition = methodParser.parse(element, parserContext);
					 String name = beanId + "." + pattern;
		             BeanDefinitionHolder methodBeanDefinitionHolder = new BeanDefinitionHolder(methodBeanDefinition, name);
		             methods.add(methodBeanDefinitionHolder);
		         }
	         }
		 }
		 if (methods.size() > 0) {
		     bd.getPropertyValues().addPropertyValue("methods", methods);
		 }	
    }
	void parsePlugin(Element root, ParserContext parserContext, RootBeanDefinition bd) {
    	NodeList nodeList = root.getChildNodes();
    	if (nodeList == null || nodeList.getLength() == 0) return;

    	ArrayList<String> plugins = new ArrayList<>();
    	for (int i = 0; i < nodeList.getLength(); i++) {
    		Node node = nodeList.item(i);
	         if (node instanceof Element) {
	             Element element = (Element) node;
	             if ("plugin".equals(node.getNodeName()) || "plugin".equals(node.getLocalName())) {
					 String params = element.getAttribute("params");
					 if (params == null || params.isEmpty() ) {
					     throw new RuntimeException("plugin params must be specified");
					 }
					 plugins.add(params);
		         }
	         }
		 }
		 if (plugins.size() > 0) {
		     bd.getPropertyValues().addPropertyValue("pluginParams", plugins);
		 }	
    }

	String parseId(Element element, ParserContext parserContext, RootBeanDefinition bd) {
		String id = element.getAttribute("id");
        if ( id == null || id.isEmpty() ) {
            id = beanClass.getName();
            int counter = 2;
            while (parserContext.getRegistry().containsBeanDefinition(id)) {
                id = beanClass.getName() + (counter++);
            }
        }
        
        parserContext.getRegistry().registerBeanDefinition(id, bd);
        return id;
	}

}

class ApplicationConfigBeanParser extends BaseParser<ApplicationConfigBean> {
	ApplicationConfigBeanParser() {
		beanClass = ApplicationConfigBean.class;
		attributes = new String[] {"name","errorMsgConverter","mockFile","traceAdapter","dataDir"};
	}
}

class ClientConfigBeanParser extends BaseParser<ClientConfigBean> {
	ClientConfigBeanParser() {
		beanClass = ClientConfigBean.class;
		attributes = new String[] {"pingSeconds","maxPackageSize","connectTimeout",
				"reconnectSeconds","ioThreads","connections",
				"notifyThreads","notifyMaxThreads","notifyQueueSize","threads","maxThreads","queueSize","loadBalance",
				};
	}
}

class ServerConfigBeanParser extends BaseParser<ServerConfigBean> {
	ServerConfigBeanParser() {
		beanClass = ServerConfigBean.class;
		attributes = new String[] {"port","host","backlog","idleSeconds",
				"maxPackageSize","maxConns","ioThreads",
				"notifyThreads","notifyMaxThreads","notifyQueueSize","threads","maxThreads","queueSize",
				"flowControl",
				};
	}
}

class WebServerConfigBeanParser extends BaseParser<WebServerConfigBean> {
	WebServerConfigBeanParser() {
		beanClass = WebServerConfigBean.class;
		attributes = new String[] {"port","host","backlog","idleSeconds",
				"maxContentLength","maxConns","ioThreads",
				"threads","maxThreads","queueSize",
				"routesFile",
				"sessionIdCookieName","sessionIdCookiePath","protoDir","sampleRate",
				"defaultSessionService","flowControl", 
				};
		hasPlugins = true;
	}
}

class RegistryConfigBeanParser extends BaseParser<RegistryConfigBean> {
	RegistryConfigBeanParser() {
		beanClass = RegistryConfigBean.class;
		attributes = new String[] {"type","addrs","enableRegist","enableDiscover","params"};
	}
}

class ServiceConfigBeanParser extends BaseParser<ServiceConfigBean> {
	ServiceConfigBeanParser() {
		beanClass = ServiceConfigBean.class;
		attributes = new String[] { "interfaceName","impl","transport","reverse",
				"registryNames","group","threads","maxThreads","queueSize","flowControl"};
		hasMethods = true;
	}
	
}

@SuppressWarnings("rawtypes")
class RefererConfigBeanParser extends BaseParser<RefererConfigBean> {
	RefererConfigBeanParser() {
		beanClass = RefererConfigBean.class;
		attributes = new String[] {"interfaceName","serviceId","transport","reverse",
				"direct","registryName","group","timeout","retryLevel","retryCount","loadBalance","zip","minSizeToZip"};
		hasMethods = true;
	}
}

class MethodConfigBeanParser extends BaseParser<MethodConfig> {
	MethodConfigBeanParser() {
		beanClass = MethodConfig.class;
		attributes = new String[] {"pattern","timeout","threads","maxThreads","queueSize","retryLevel","retryCount","loadBalance","flowControl"};
		hasId = false;
	}
}

class MonitorConfigBeanParser extends BaseParser<MonitorConfig> {
	MonitorConfigBeanParser() {
		beanClass = MonitorConfig.class;
		attributes = new String[] {"accessLog","maskFields","maxRepeatedSizeToLog","logThreads","logQueueSize","logFormatter","serverAddr","printDefault"};
		hasId = false;
	}
}

