import java.io.*;
import UWAgent.*;

public class AnAgent extends UWAgent implements Serializable {
    private String destination = null;

    public AnAgent(String[] args) {
        System.out.println("Injected");
        destination = args[0];
    }

    public AnAgent() {
        System.out.println("Injected");
        destination = "localhost";
    }

    public void init() {
        System.out.println("I'll hop to " + destination);
        String[] args = new String[1];
        args[0] = "hello";
        hop(destination, "func", args);
    }

    public void func(String[] args) {
        System.out.println(args[0]);
    }
}
