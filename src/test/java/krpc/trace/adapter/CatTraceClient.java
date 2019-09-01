package krpc.trace.adapter;

public class CatTraceClient {

    public static void main(String[] args) throws Exception {

        CatTraceAdapter t = new CatTraceAdapter();
        t.config("server=10.1.20.157:8080");
        t.init();

        sleepSeconds(60);


    }

    static void sleepSeconds(int n) {
        try {
            Thread.sleep(n * 1000);
        } catch(Exception e) {

        }
    }
}

