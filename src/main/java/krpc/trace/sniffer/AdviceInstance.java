package krpc.trace.sniffer;

public class AdviceInstance {

    public static boolean debug = false;

    public static Advice instance = new Advice() {

        public void start(String type, String action) {
            if (debug) {
                System.out.println("AdviceInstance start called, type=" + type + ",action=" + action);
            }
        }

        public long stop(boolean ok) {
            if (debug) {
                System.out.println("AdviceInstance stop called, ok=" + ok);
            }
            return 0;
        }

        public void logException(Throwable e) {
            if (debug) {
                System.out.println("AdviceInstance logException called, e=" + e.getMessage());
            }
        }

    };

}
