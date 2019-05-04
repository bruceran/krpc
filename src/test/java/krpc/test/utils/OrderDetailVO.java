package krpc.test.utils;

public class OrderDetailVO {

    public String name;
    private int quantity;
    public double price;
    public String note;

    public String toString() {
        return "name="+name+",quantity="+quantity+",price="+price+",note="+note;
    }

}

