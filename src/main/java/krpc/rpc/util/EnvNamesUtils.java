package krpc.rpc.util;

import java.util.ArrayList;
import java.util.List;

public class EnvNamesUtils {

    static public List<String> parseEnvNames(String content) {

        List<String> names = new ArrayList<>();

        char[] chs = content.toCharArray();
        for(int i=0;i<chs.length;++i) {
            char ch = chs[i];

            if( ch == '%' ) {
                int end = findEnd(chs,i);
                if( end == -1 ) {
                    continue;
                }

                String name = content.substring(i+1,end);
                if( !name.isEmpty() ) {
                    names.add(name);
                }

                i = end;
            }
        }

        return names;
    }

    static int findEnd(char[] chs,int start) {
        start++;
        if( start >= chs.length ) return -1;
        for(int k=start;k<chs.length;++k) {
            if( chs[k] == '%' ) return k;
            if( !isValidChar(chs[k]) ) return -1;
        }
        return -1;
    }

    static boolean isValidChar(char ch) {
        return ch >= '0' && ch <= '9' ||
                ch >= 'a' && ch <= 'z' ||
                ch >= 'A' && ch <= 'Z' ||
                ch == '_' || ch == '-' ||
                ch == '.' ;
    }

}
