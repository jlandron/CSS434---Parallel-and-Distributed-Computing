import java.io.*;
import UWAgent.*;

//inject into Computer you launch at by default.
//input args for Nodes to jump to
public class MyAgent extends UWAgent implements Serializable {
    private String destination1 = null;
    private String destination2 = null;

    public MyAgent(String[] args) {
        System.out.println("Injected");
        destination1 = args[0];
        destination2 = args[1];
    }

    public void init() {
        System.out.println("hop");
        hop(destination1, "func1");
    }

    public void func1() {
        System.out.println("skip");
        hop(destination2, "func2");
    }

    public void func2() {
        System.out.println("jump");
    }
}
