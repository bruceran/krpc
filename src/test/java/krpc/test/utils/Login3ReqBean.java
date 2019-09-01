package krpc.test.utils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class Login3ReqBean {

    String userName;

    LinkedHashMap<String,String> kvs;
    HashMap<String,Integer> kvs2;
    Map<Integer,String> kvs3;
    Map<String,AppleBean> apples;

    public String toString() {
        return "userName="+userName+",kvs="+kvs
                +",kvs2="+kvs2+",kvs3="+kvs3+",apples="+apples;
    }

}

