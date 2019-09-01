package krpc.test.utils;

public class AppleBean {

    String color;
    double weight;

    public String toString() {
        return "color="+color+",weight="+weight;
    }

    public AppleBean() {

    }

    AppleBean(String color) {
        this.color = color;
    }
}

