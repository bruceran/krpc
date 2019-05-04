package krpc.test.utils;

public class OrderDetailVO2 extends OrderDetailVO2Base {

    public String name;
    private Integer quantity;
    public Double price;

    public String toString() {
        return "name="+name+",quantity="+quantity+",price="+price+",note="+note;
    }

}

