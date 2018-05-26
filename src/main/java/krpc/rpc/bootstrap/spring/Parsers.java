package krpc.rpc.bootstrap.spring;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.RuntimeBeanReference;
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
        registerBeanDefinitionParser("client", new ClientConfigBeanParser());
        registerBeanDefinitionParser("server", new ServerConfigBeanParser());
        registerBeanDefinitionParser("webserver", new WebServerConfigBeanParser());
        registerBeanDefinitionParser("registry", new RegistryConfigBeanParser());
        registerBeanDefinitionParser("service", new ServiceConfigBeanParser());
        registerBeanDefinitionParser("referer", new RefererConfigBeanParser());
        registerBeanDefinitionParser("monitor", new MonitorConfigBeanParser());
    }
}

class BaseParser<T> implements BeanDefinitionParser {

    Class<T> beanClass;
    
    String[] attributes;
    String[] refs;    
    boolean hasMethods = false;
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
        
        if( refs != null ) {
            for(String name:refs) {
            	parseReference(name,root,parserContext,bd);
            }        	
        }

        if( hasMethods ) 
        	parseMethod(root,parserContext,bd);
        
        return bd;
    }

    /*
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void parseParams(Element root, ParserContext parserContext, RootBeanDefinition bd) {
    	NodeList nodeList = root.getChildNodes();
    	if (nodeList == null || nodeList.getLength() == 0) return;
	 
    	ManagedMap params = new ManagedMap();
    	for (int i = 0; i < nodeList.getLength(); i++) {
    		Node node = nodeList.item(i);
	         if (node instanceof Element) {
	             Element element = (Element) node;
	             if ("property".equals(node.getNodeName()) || "property".equals(node.getLocalName())) {
					 String name = element.getAttribute("name");
					 String value = element.getAttribute("value");
					 if (name == null || name.isEmpty()) {
					     throw new RuntimeException("property name must be specified");
					 }
					 params.put(name, value);
		         }
	         }
		 }
		 if (params.size() > 0) {
		     bd.getPropertyValues().addPropertyValue("params", params);
		 }	    	
    }
	*/
    
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
    
    void parseReference(String key, Element element, ParserContext parserContext, RootBeanDefinition bd) {
    	try {
	    	String refName = element.getAttribute(key);
	    	
	    	if (parserContext.getRegistry().containsBeanDefinition(refName)) {
                BeanDefinition refBean = parserContext.getRegistry().getBeanDefinition(refName);
                if (!refBean.isSingleton()) {
                    throw new RuntimeException("not a valid impl for service, "+key+"="+refName);
                }
            }
	    	RuntimeBeanReference reference = new RuntimeBeanReference(refName);
	    	bd.getPropertyValues().addPropertyValue(key, reference);

    	} catch(Exception e) {
    		throw new RuntimeException(e);
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
		attributes = new String[] {"name","errorMsgConverter","mockFile","flowControl","traceAdapter"};
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
		attributes = new String[] {"port","host","idleSeconds",
				"maxPackageSize","maxConns","ioThreads",
				"notifyThreads","notifyMaxThreads","notifyQueueSize","threads","maxThreads","queueSize",
				};
	}
}

class WebServerConfigBeanParser extends BaseParser<WebServerConfigBean> {
	WebServerConfigBeanParser() {
		beanClass = WebServerConfigBean.class;
		attributes = new String[] {"port","host","idleSeconds",
				"maxContentLength","maxConns","ioThreads",
				"threads","maxThreads","queueSize",
				"jsonConverter","sessionService","routesFile",
				"sessionIdCookieName","sessionIdCookiePath","protoDir","sampleRate"};
	}
}

class RegistryConfigBeanParser extends BaseParser<RegistryConfigBean> {
	RegistryConfigBeanParser() {
		beanClass = RegistryConfigBean.class;
		attributes = new String[] {"type","addrs"};
	}
}

class ServiceConfigBeanParser extends BaseParser<ServiceConfigBean> {
	ServiceConfigBeanParser() {
		beanClass = ServiceConfigBean.class;
		attributes = new String[] { "interfaceName","transport","reverse",
				"registryNames","group","threads","maxThreads","queueSize","flowControl"};
		refs = new String[] { "impl" };
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
		attributes = new String[] {"maskFields","maxRepeatedSizeToLog","logThreads","logQueueSize","logFormatter","serverAddr","printDefault"};
		hasId = false;
	}
}

