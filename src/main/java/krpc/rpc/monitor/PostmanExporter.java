package krpc.rpc.monitor;

import com.google.protobuf.LazyStringList;
import krpc.common.Json;
import krpc.rpc.core.ServiceMetas;

import java.lang.reflect.*;
import java.util.*;

public class PostmanExporter {

    private ServiceMetas serviceMetas;

    public PostmanExporter() {
    }

    public PostmanExporter(ServiceMetas serviceMetas) {
        this.serviceMetas = serviceMetas;
    }

    /**
     * @description 具体解析path路径
     */
    public Map<String, Object> doGeneratePostmanCollections(int serviceId) {
        String clsName = "";
        int servicePort = 9000;
        String serviceName = this.serviceMetas.getServiceName(serviceId);
        if (serviceId < 1000) servicePort = Integer.valueOf(8 + "" + serviceId);
        else if (serviceId >= 1000) servicePort = Integer.valueOf(3 + "" + serviceId);
        Object obj = serviceMetas.findService(serviceId);
        Class<?>[] classes = obj.getClass().getInterfaces();
        if (null == classes) return null;
        clsName = classes[0].getName();
        return generatePostManCollections(clsName, serviceName, servicePort);

    }


    /**
     * @param clsName
     * @param serviceName
     * @description 获取当前服务所有接口，构造postman服务，这里可以选择当前所有服务的接口所在接口名，或者单独某个接口的类名
     */
    protected Map<String, Object> generatePostManCollections(String clsName, String serviceName, int servicePort) {
        if (null == clsName || clsName.equals("")) return null;
        int a = clsName.lastIndexOf(".");
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", UUID.randomUUID().toString());
        map.put("name", serviceName);
        try {
            Class<?> cls = Class.forName(clsName);
            //获取对象中的所有方法
            Method[] methods = cls.getDeclaredMethods();
            List<Map<String, Object>> params = new ArrayList<>();
            //解析方法参数
            analysisMethodParam(methods, serviceName, params, servicePort);
            map.put("requests", params);
            return map;
        } catch (
                ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    //postman相关方法

    /**
     * @param methods
     * @param serviceName
     * @param params
     * @description 查找方法的参数
     */
    protected void analysisMethodParam(Method[] methods, String serviceName, List<Map<String, Object>> params, int servicePort) {
        for (int i = 0; i < methods.length; i++) {
            Map<String, Object> param = new LinkedHashMap<>();
            //基本参数设值
            createBaseParam(methods[i], i, param, serviceName, servicePort);
            //获取本方法所有参数类型，存入数组
            Class<?>[] parameterTypes = methods[i].getParameterTypes();
            //标签rawModeData代表真正的入参
            Map<String, Object> rawMap = new LinkedHashMap<>();
            getParameters(parameterTypes, rawMap);
            //postman解析的时候，入参先转json字符串
            param.put("rawModeData", Json.toJson(rawMap));
            params.add(param);
        }
    }

    /**
     * @param method
     * @param i
     * @param param
     * @param serviceName
     * @description 设置基本参数
     */
    protected void createBaseParam(Method method, int i, Map<String, Object> param, String serviceName, int servicePort) {
        //获取方法名字
        String methodName = method.getName();
        param.put("id", UUID.randomUUID().toString() + i);
        param.put("name", methodName);
        param.put("url", " http://{{host}}:" + servicePort + "/" + serviceName + "/" + methodName);
        param.put("dataMode", "raw");
        param.put("method", "POST");
        param.put("headers", "Content-Type:application/json");
    }

    /**
     * @param parameterTypes
     * @param rawMap
     * @description 获取接口参数
     */
    protected void getParameters(Class<?>[] parameterTypes, Map<String, Object> rawMap) {
        for (int j = 0; j < parameterTypes.length; j++) {
            String parameterName = parameterTypes[j].getName();
            Class<?> clazz = null;
            try {
                clazz = Class.forName(parameterName);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            Field[] fields = clazz.getDeclaredFields();
            //具体的参数
            createParam(fields, rawMap);
        }
    }


    /**
     * @param fields
     * @param param
     * @description 设置接口的入参
     */
    protected void createParam(Field[] fields, Map<String, Object> param) {
        for (Field f : fields) {
            int k = f.getModifiers();
            String decorate = Modifier.toString(k);
            //找到被private修饰的方法，是我们想要的真正入参
            if (!f.isAccessible() && !decorate.contains("final")) {
                Class<?> T = f.getType();
                //依据pb定义来的Arrays.asList(classes).contains(T)
                if (T.getClass() instanceof Object) {
                    String paramName = f.getName().substring(0, f.getName().length() - 1);
                    if (paramName.contains("bitField0") || paramName.contains("memoizedIsInitialize")) continue;
                    //获取并判断选择参数类型
                    chooseParamType(T, param, paramName, f);
                }
            }
        }
    }


    /**
     * @param T
     * @param param
     * @param f
     * @description 判断并选择参数
     */
    protected void chooseParamType(Class<?> T, Map<String, Object> param, String paramName, Field f) {
        //从具体的类到对象类
        if (T == String.class || T == Object.class) {
            param.put(paramName, paramName);
        } else if (T == int.class || T == Integer.class || T == double.class || T == Double.class || T == Long.class || T == long.class) {
            param.put(paramName, 1);
        } else if (T == List.class) {
            //继续深挖List对象里的类
            // 如果是List类型，得到其Generic的类型
            Type genericType = f.getGenericType();
            createListParam(genericType, paramName, param);
        } else if (T == LazyStringList.class) {
            //List<String>格式
            List<String> listParam = new ArrayList<>();
            listParam.add(paramName);
            param.put(paramName, listParam);
        } else if (T.getClass() instanceof Object) {
            Field[] listFields = T.getDeclaredFields();
            //List的内部对象
            Map<String, Object> innerParam = new LinkedHashMap<>();
            //递归获取参数
            createParam(listFields, innerParam);
            param.put(paramName, innerParam);
        }
    }

    /**
     * @param paramName
     * @param genericType
     * @param param
     * @description 构造List参数
     */
    protected void createListParam(Type genericType, String paramName, Map<String, Object> param) {
        if (genericType == null) return;
        // 如果是泛型参数的类型
        if (genericType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType;
            //得到泛型里的class类型对象
            Class<?> genericClazz = (Class<?>) pt.getActualTypeArguments()[0];
            Field[] listFields = genericClazz.getDeclaredFields();
            //参数格式是List的
            List<Map<String, Object>> listParam = new ArrayList<>();
            //List的内部对象
            Map<String, Object> innerParam = new LinkedHashMap<>();
            //递归获取参数
            createParam(listFields, innerParam);
            listParam.add(innerParam);
            param.put(paramName, listParam);
        }
    }

    public ServiceMetas getServiceMetas() {
        return serviceMetas;
    }

    public void setServiceMetas(ServiceMetas serviceMetas) {
        this.serviceMetas = serviceMetas;
    }
}
