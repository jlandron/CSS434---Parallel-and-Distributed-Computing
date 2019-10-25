import java.io.*;
import UWAgent.*;

public class TestAgent extends UWAgent implements Serializable {
    private String destination = null;

    public TestAgent(String[] args) {

    }

    public TestAgent() {

    }

    public void init() {
        hop("cssmpi2", "func1", null);
    }

    public void func1() {
        System.out.println("hop");
        hop("cssmpi3", "func2", null);
    }

    public void func2() {
        System.out.println("step");
        hop("cssmpi4", "func3", null);
    }

    public void func3() {
        System.out.println("jump");
    }
}
