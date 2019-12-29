package krpc.rpc.impl;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import krpc.common.RetCodes;
import krpc.rpc.core.*;

import java.lang.reflect.Method;
import java.util.*;

public class DefaultServiceMetas implements ServiceMetas {

    HashMap<Integer, Object> services = new HashMap<>();
    HashMap<Integer, Object> referers = new HashMap<>();
    HashMap<String, Method> methods = new HashMap<>();
    HashMap<Integer, Object> asyncReferers = new HashMap<>();
    HashMap<String, Method> asyncMethods = new HashMap<>();
    HashMap<String, Class<?>> reqClsMap = new HashMap<>();
    HashMap<String, Class<?>> resClsMap = new HashMap<>();
    HashMap<String, Method> reqParserMap = new HashMap<>();
    HashMap<String, Method> resParserMap = new HashMap<>();
    HashMap<Integer, String> serviceNames = new HashMap<>();
    HashMap<String, String> msgNames = new HashMap<>();
    HashMap<String, String> originalMsgNames = new HashMap<>();
    HashMap<String, Descriptor> reqDescMap = new HashMap<>();
    HashMap<String, Descriptor> resDescMap = new HashMap<>();
    HashMap<String, RpcCallable> callableMap = new HashMap<>();
    HashMap<Integer, RpcCallable> callableMapForServiceId = new HashMap<>();
    HashMap<Integer, RpcCallable> dynamicCallableMap = new HashMap<>();

    Validator validator;
    Set<Integer> exchangeServiceIds = new HashSet<>();

    public Object findService(int serviceId) {
        return services.get(serviceId);
    }

    public Object findReferer(int serviceId) {
        return referers.get(serviceId);
    }

    public Method findMethod(int serviceId, int msgId) {
        return methods.get(serviceId + "." + msgId);
    }

    public Object findAsyncReferer(int serviceId) {
        return asyncReferers.get(serviceId);
    }

    public Method findAsyncMethod(int serviceId, int msgId) {
        return asyncMethods.get(serviceId + "." + msgId);
    }

    public Class<?> findReqClass(int serviceId, int msgId) {
        return reqClsMap.get(serviceId + "." + msgId);
    }

    public Class<?> findResClass(int serviceId, int msgId) {
        return resClsMap.get(serviceId + "." + msgId);
    }

    public Method findReqParser(int serviceId, int msgId) {
        return reqParserMap.get(serviceId + "." + msgId);
    }

    public Method findResParser(int serviceId, int msgId) {
        return resParserMap.get(serviceId + "." + msgId);
    }

    public String getServiceName(int serviceId) {
        return serviceNames.get(serviceId);
    }

    public String getName(int serviceId, int msgId) {
        return msgNames.get(serviceId + "." + msgId);
    }

    public String getOriginalName(int serviceId, int msgId) {
        return originalMsgNames.get(serviceId + "." + msgId);
    }

    public RpcCallable findCallable(String implClsName) {
        return callableMap.get(implClsName);
    }
    public RpcCallable findCallable(int serviceId) {
        return callableMapForServiceId.get(serviceId);
    }

    public List<Integer> getServiceIds() {
        return new ArrayList<>(services.keySet());
    }

    public Map<Integer, String> getMsgNames(int serviceId) {
        Map<Integer, String> map = new HashMap<>();
        String prefix = serviceId + ".";
        for (Map.Entry<String, Method> entry : methods.entrySet()) {
            String key = entry.getKey();
            String name = entry.getValue().getName();
            if (key.startsWith(prefix)) {
                int p = key.indexOf(".");
                map.put(Integer.parseInt(key.substring(p + 1)), name);
            }
        }
        return map;
    }

    public String getServiceIdMsgId(String serviceName, String msgName) {

        serviceName = serviceName.toLowerCase();
        msgName = msgName.toLowerCase();

        String s = serviceName + "." + msgName;
        for (Map.Entry<String, String> entry : msgNames.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(s)) {
                return entry.getKey();
            }
        }

        return null;

    }

    private void addImpl(Class<?> intf, Object obj, boolean isService) {

        ReflectionUtils.checkInterface(intf, obj);

        int serviceId = ReflectionUtils.getServiceId(intf);
        if (serviceId <= 1) throw new RuntimeException("serviceId must > 1");
        if (isService)
            services.put(serviceId, obj);
        else
            referers.put(serviceId, obj);

        String serviceName = intf.getSimpleName();

        if( isService ) {
            HashMap<String, Integer> msgNameMap = ReflectionUtils.getMsgNames(intf);
            HashSet<Integer> msgIds = new HashSet<>();
            for (Map.Entry<String, Integer> entry : msgNameMap.entrySet()) {
                int msgId = entry.getValue();
                if( msgIds.contains(msgId)) {
                    throw new RuntimeException("msgId duplicated, serviceId="+serviceId+",msgId="+msgId);
                }
                msgIds.add(msgId);
            }
        }
        HashMap<Integer, String> msgIdMap = ReflectionUtils.getMsgIds(intf);
        HashMap<String, Object> msgNameMap = ReflectionUtils.getMethodInfo(intf);
        for (Map.Entry<Integer, String> entry : msgIdMap.entrySet()) {
            int msgId = entry.getKey();
            String msgName = entry.getValue();
            if (msgId < 1) throw new RuntimeException("msgId must > 0");
            Method m = (Method) msgNameMap.get(msgName);
            Class<?> reqCls = (Class<?>) msgNameMap.get(msgName + "-req");
            Class<?> resCls = (Class<?>) msgNameMap.get(msgName + "-res");

            if (isService && validator != null) validator.prepare(reqCls);

            Method reqParser = (Method) msgNameMap.get(msgName + "-reqp");
            Method resParser = (Method) msgNameMap.get(msgName + "-resp");
            if (m != null) {
                methods.put(serviceId + "." + msgId, m);
                reqClsMap.put(serviceId + "." + msgId, reqCls);
                resClsMap.put(serviceId + "." + msgId, resCls);
                reqParserMap.put(serviceId + "." + msgId, reqParser);
                resParserMap.put(serviceId + "." + msgId, resParser);
                serviceNames.put(serviceId, serviceName.toLowerCase());
                msgNames.put(serviceId + "." + msgId, (serviceName + "." + msgName).toLowerCase());
                originalMsgNames.put(serviceId + "." + msgId, serviceName + "." + msgName);
            }
        }
    }

    private void addAsyncImpl(Class<?> intf, Object obj) {
        ReflectionUtils.checkInterface(intf, obj);

        int serviceId = ReflectionUtils.getServiceId(intf);
        if (serviceId <= 1) throw new RuntimeException("serviceId must > 1");
        asyncReferers.put(serviceId, obj);

        String serviceName = intf.getSimpleName();
        serviceName = serviceName.substring(0, serviceName.length() - 5);

        HashMap<Integer, String> msgIdMap = ReflectionUtils.getMsgIds(intf);
        HashMap<String, Object> msgNameMap = ReflectionUtils.getAsyncMethodInfo(intf);
        for (Map.Entry<Integer, String> entry : msgIdMap.entrySet()) {
            int msgId = entry.getKey();
            String msgName = entry.getValue();
            if (msgId < 1) throw new RuntimeException("msgId must > 0");
            Method m = (Method) msgNameMap.get(msgName);
            Class<?> reqCls = (Class<?>) msgNameMap.get(msgName + "-req");
            Class<?> resCls = (Class<?>) msgNameMap.get(msgName + "-res");
            Method reqParser = (Method) msgNameMap.get(msgName + "-reqp");
            Method resParser = (Method) msgNameMap.get(msgName + "-resp");
            if (m != null) {
                asyncMethods.put(serviceId + "." + msgId, m);
                reqClsMap.putIfAbsent(serviceId + "." + msgId, reqCls);
                resClsMap.putIfAbsent(serviceId + "." + msgId, resCls);
                reqParserMap.putIfAbsent(serviceId + "." + msgId, reqParser);
                resParserMap.putIfAbsent(serviceId + "." + msgId, resParser);
                serviceNames.putIfAbsent(serviceId, serviceName.toLowerCase());
                msgNames.putIfAbsent(serviceId + "." + msgId, (serviceName + "." + msgName).toLowerCase());
                originalMsgNames.putIfAbsent(serviceId + "." + msgId, serviceName + "." + msgName);
            }
        }
    }

    public void addService(Class<?> intf, Object impl, RpcCallable callable) {
        addImpl(intf, impl, true);
        if (callable != null) {
            callableMap.put(impl.getClass().getName(), callable);
            int serviceId = ReflectionUtils.getServiceId(intf);
            callableMapForServiceId.put(serviceId,callable);
        }
    }

    public void addReferer(Class<?> intf, Object impl, RpcCallable callable) {
        addImpl(intf, impl, false);
        callableMap.put(impl.getClass().getName(), callable);
        int serviceId = ReflectionUtils.getServiceId(intf);
        callableMapForServiceId.put(serviceId,callable);
    }

    public void addAsyncReferer(Class<?> intf, Object impl, RpcCallable callable) {
        addAsyncImpl(intf, impl);
        callableMap.put(impl.getClass().getName(), callable);
        int serviceId = ReflectionUtils.getServiceId(intf);
        callableMapForServiceId.put(serviceId,callable);
    }

    public void addDirect(int serviceId, int msgId, Class<?> reqCls, Class<?> resCls) {
        reqClsMap.putIfAbsent(serviceId + "." + msgId, reqCls);
        resClsMap.putIfAbsent(serviceId + "." + msgId, resCls);
        HashMap<String, Method> parsers = ReflectionUtils.getParsers(reqCls, resCls);
        reqParserMap.put(serviceId + "." + msgId, parsers.get("reqp"));
        resParserMap.put(serviceId + "." + msgId, parsers.get("resp"));
    }

    public void addDynamic(int serviceId, int msgId, Descriptor reqDesc, Descriptor resDesc, String serviceName, String msgName) {
        reqDescMap.put(serviceId + "." + msgId, reqDesc);
        resDescMap.put(serviceId + "." + msgId, resDesc);
        serviceNames.put(serviceId, serviceName.toLowerCase());
        msgNames.put(serviceId + "." + msgId, (serviceName + "." + msgName).toLowerCase());
        originalMsgNames.put(serviceId + "." + msgId, serviceName + "." + msgName);
    }

    public void addDynamic(int serviceId, RpcCallable callable) {
        dynamicCallableMap.put(serviceId, callable);
    }

    public Descriptor findDynamicReqDescriptor(int serviceId, int msgId) {
        return reqDescMap.get(serviceId + "." + msgId);
    }

    public Descriptor findDynamicResDescriptor(int serviceId, int msgId) {
        return resDescMap.get(serviceId + "." + msgId);
    }

    public RpcCallable findDynamicCallable(int serviceId) {
        return dynamicCallableMap.get(serviceId);
    }

    public Message generateRes(int serviceId, int msgId, int retCode) {
        return generateRes(serviceId, msgId, retCode, null);
    }

    public Message generateRes(int serviceId, int msgId, int retCode, String retMsg) {

        Class<?> cls = findResClass(serviceId, msgId);
        if (cls == null) {
            return generateResDynamic(serviceId, msgId, retCode);
        }

        Message res = null;
        try {
            if (retMsg == null)
                retMsg = RetCodes.retCodeText(retCode);
            res = (Message) ReflectionUtils.generateResponseObject(cls, retCode, retMsg);
        } catch (Exception e) {
            throw new RpcException(RetCodes.ENCODE_RES_ERROR, "generateRes generate object exception");
        }

        return res;
    }

    Message generateResDynamic(int serviceId, int msgId, int retCode) {

        Descriptor desc = findDynamicResDescriptor(serviceId, msgId);
        if (desc == null) {
            throw new RpcException(RetCodes.NOT_FOUND, "generateRes cls not found");
        }

        Message res = null;
        try {
            String retMsg = RetCodes.retCodeText(retCode);
            DynamicMessage.Builder b = ReflectionUtils.generateDynamicBuilder(desc);
            res = (Message) ReflectionUtils.generateResponseObject(b, serviceId + ":" + msgId, retCode, retMsg);
        } catch (Exception e) {
            throw new RpcException(RetCodes.ENCODE_RES_ERROR, "generateRes generate object exception");
        }

        return res;
    }

    public Validator getValidator() {
        return validator;
    }

    public void setValidator(Validator validator) {
        this.validator = validator;
    }

    public void addExchangeServiceId(int serviceId) {
        exchangeServiceIds.add(serviceId);
    }
    public boolean isExchangeServiceId(int serviceId) {
        return exchangeServiceIds.contains(serviceId);
    }

    public Map<String,String> getServiceMetaInfo() {
        Map<String,String> map = new LinkedHashMap<>();
        List<Integer> serviceIds = getServiceIds();
        for(Integer serviceId: serviceIds) {
            map.put(String.valueOf(serviceId),getServiceName(serviceId));
        }
        return map;
    }
    public Map<String,String> getMsgMetaInfo() {
        Map<String,String> map = new LinkedHashMap<>();
        List<Integer> serviceIds = getServiceIds();
        for(Integer serviceId: serviceIds) {
            Map<Integer, String> names = getMsgNames(serviceId);
            String prefix = serviceId + ".";
            for (Map.Entry<Integer, String> entry : names.entrySet()) {
                map.put(prefix+entry.getKey(),entry.getValue());
            }
        }
        return map;
    }
}
