package krpc.test.utils;

import java.util.List;

public class ItemVO extends ItemVOBase {

    String itemId;
    String name;
    private String price;

    public String toString() {
        String s = "itemId="+itemId+",name="+name+",price="+price+",attrs="+attrs;
        return s;
    }

}

