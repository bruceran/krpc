package krpc.rpc.bootstrap.spring;

import krpc.rpc.bootstrap.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.NamespaceHandlerSupport;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

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

    static RootBeanDefinition rpcAppBeanDefinition;
    static List<String> beanIds = new ArrayList<>();

    Class<T> beanClass;
    String[] attributes;
    boolean hasMethods = false;
    boolean hasPlugins = false;

    String idValue;
    String beanId;

    public void init(Class<?> cls) {
        Field[] fields = cls.getDeclaredFields();
        List<String> attrs = new ArrayList<>();
        for (Field f : fields) {
            String name = f.getName();
            if (name.equals("methods")) {
                hasMethods = true;
            } else if (name.equals("pluginParams")) {
                hasPlugins = true;
            } else {
                attrs.add(name);
            }
        }
        attributes = attrs.toArray(new String[0]);
    }

    public BeanDefinition parse(Element root, ParserContext parserContext) {

        RootBeanDefinition bd = new RootBeanDefinition();
        bd.setBeanClass(beanClass);
        bd.setLazyInit(false);
        idValue = root.getAttribute("id");

        if (this instanceof RefererConfigBeanParser)
            beanId = generateBeanId(null, parserContext); // idValue will be used by referer proxy
        else
            beanId = generateBeanId(idValue, parserContext);
        parserContext.getRegistry().registerBeanDefinition(beanId, bd);

        beanIds.add(beanId);

        if (rpcAppBeanDefinition == null) {
            rpcAppBeanDefinition = new RootBeanDefinition();
            rpcAppBeanDefinition.setBeanClass(RpcAppFactory.class);
            rpcAppBeanDefinition.setLazyInit(false);
            rpcAppBeanDefinition.setInitMethodName("init");
            rpcAppBeanDefinition.setDestroyMethodName("close");

            parserContext.getRegistry().registerBeanDefinition("rpcApp", rpcAppBeanDefinition);
        }
        rpcAppBeanDefinition.setDependsOn(beanIds.toArray(new String[0]));

        if (attributes != null) {
            for (String name : attributes) {
                String value = root.getAttribute(name);
                if (value != null && value.length() > 0)
                    bd.getPropertyValues().addPropertyValue(name, value);
            }
        }

        if (hasMethods)
            parseMethod(root, parserContext, bd);

        if (hasPlugins)
            parsePlugin(root, parserContext, bd);


        return bd;
    }

    String generateBeanId(String initValue, ParserContext parserContext) {
        String id = initValue;
        if (id == null || id.isEmpty()) {
            id = beanClass.getName();
            int counter = 2;
            while (parserContext.getRegistry().containsBeanDefinition(id)) {
                id = beanClass.getName() + (counter++);
            }
        }
        return id;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
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
                    if (pattern == null || pattern.isEmpty()) {
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
                    if (params == null || params.isEmpty()) {
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

}

class ApplicationConfigBeanParser extends BaseParser<ApplicationConfigBean> {
    ApplicationConfigBeanParser() {
        beanClass = ApplicationConfigBean.class;
        init(ApplicationConfig.class);
    }
}

class ClientConfigBeanParser extends BaseParser<ClientConfigBean> {
    ClientConfigBeanParser() {
        beanClass = ClientConfigBean.class;
        init(ClientConfig.class);
    }
}

class ServerConfigBeanParser extends BaseParser<ServerConfigBean> {
    ServerConfigBeanParser() {
        beanClass = ServerConfigBean.class;
        init(ServerConfig.class);
    }
}

class WebServerConfigBeanParser extends BaseParser<WebServerConfigBean> {
    WebServerConfigBeanParser() {
        beanClass = WebServerConfigBean.class;
        init(WebServerConfig.class);
    }
}

class RegistryConfigBeanParser extends BaseParser<RegistryConfigBean> {
    RegistryConfigBeanParser() {
        beanClass = RegistryConfigBean.class;
        init(RegistryConfig.class);
    }
}

class ServiceConfigBeanParser extends BaseParser<ServiceConfigBean> {
    ServiceConfigBeanParser() {
        beanClass = ServiceConfigBean.class;
        init(ServiceConfig.class);
    }

}

class RefererConfigBeanParser extends BaseParser<RefererConfigBean> {

    static Logger log = LoggerFactory.getLogger(Parsers.class);

    RefererConfigBeanParser() {
        beanClass = RefererConfigBean.class;
        init(RefererConfig.class);
    }

    public BeanDefinition parse(Element root, ParserContext parserContext) {
        BeanDefinition bd = super.parse(root, parserContext);
        String interfaceName = bd.getPropertyValues().getPropertyValue("interfaceName").getValue().toString();
        registerReferer(idValue, interfaceName, parserContext);
        return bd;
    }

    void registerReferer(String id, String interfaceName, ParserContext parserContext) {
        String beanName = generateBeanName(id, interfaceName);
        //log.info("register referer "+interfaceName+", beanName="+beanName);
        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(RefererFactory.class);
        beanDefinitionBuilder.addConstructorArgValue(beanName);
        beanDefinitionBuilder.addConstructorArgValue(interfaceName);
        beanDefinitionBuilder.addDependsOn("rpcApp");
        beanDefinitionBuilder.setLazyInit(true);
        parserContext.getRegistry().registerBeanDefinition(beanName, beanDefinitionBuilder.getRawBeanDefinition());

        registerAsyncReferer(beanName + "Async", interfaceName + "Async", parserContext);
    }

    void registerAsyncReferer(String beanName, String interfaceName, ParserContext parserContext) {
        //log.info("register referer "+interfaceName+", beanName="+beanName);
        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(RefererFactory.class);
        beanDefinitionBuilder.addConstructorArgValue(beanName);
        beanDefinitionBuilder.addConstructorArgValue(interfaceName);
        beanDefinitionBuilder.addDependsOn("rpcApp");
        beanDefinitionBuilder.setLazyInit(true);
        parserContext.getRegistry().registerBeanDefinition(beanName, beanDefinitionBuilder.getRawBeanDefinition());
    }

    String generateBeanName(String id, String interfaceName) {
        if (id != null && !id.isEmpty()) return id;
        int p = interfaceName.lastIndexOf(".");
        String name = interfaceName.substring(p + 1);
        name = name.substring(0, 1).toLowerCase() + name.substring(1);
        return name;
    }

}

class MonitorConfigBeanParser extends BaseParser<MonitorConfigBean> {
    MonitorConfigBeanParser() {
        beanClass = MonitorConfigBean.class;
        init(MonitorConfig.class);
    }
}

class MethodConfigBeanParser extends BaseParser<MethodConfig> {
    MethodConfigBeanParser() {
        beanClass = MethodConfig.class;
        init(MethodConfig.class);
    }
}
