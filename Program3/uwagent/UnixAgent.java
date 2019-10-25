
/**
 *#############################################################################
 *#------------------------------ UnixAgent -----------------------------------
 *#  
 *#  @author 	Joshua Landron
 *#  @date 	    10May2019
 *#  @version	17May2019
 *#
 *#  Built as part of CSS434 with Dr. Munehiro Fukuda, Spring 2019
 *#
 *#############################################################################
 *
 *
 * Implementation and assumptions:
 *  Uses UWAgent to execute remote agents
 *  Assumes that input upon execution will be in order as follows:
 *  
 * java UnixClient P/S port# numberOfServers Ip1 Ip2... numberOfCommands Com1 Com2...
 * 
 * if the order of this input is changed, the client will probably not run
 * There is very little error checking in the methods, so input should be valid and
 * UWPlace is setup on all nodes to be called, including the Computing node that will
 * run this Class initially.
 * UWPlace can be setup with the provided script ./runUWPlace.sh port#
 * and this class should be compiled with the script ./compile.sh UnixAgent.java
 * 
 * To test this class on up to 3 servers, I created and used runUnixAgent.sh which 
 * takes one arg for port# and an optional arg for printing (default is no printing)
 * ------------------------------------------------------------------------------
 **/
import UWAgent.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.lang.Integer;

public class UnixAgent extends UWAgent implements Serializable {
    private static final long serialVersionUID = 1L;
    boolean print = false;
    Vector<String> allValueVector;
    String[] servers;
    String[] commands;
    int nextNode;
    String origin;
    Date startTime;

    /**
     * ------------------------------------Constructor----------------------------------
     * no args constructor that simply prints an error message and exits
     */
    public UnixAgent() {
        System.out.println("No args passed, exiting now");
        System.exit(-1);
    }

    /**
     * ------------------------------------Constructor---------------------------------
     * constructor that takes a copy of args that was passed upon terminal method
     * execution. initializes most of the fields that will be carried as the agent
     * migrates, including print boolean, array of servers to migrate to, array of
     * commands to carry out, int representing the index of the next node to be
     * migrated to, and the string representing the original injection point of the
     * agent.
     * 
     */
    public UnixAgent(String[] argsCpy) {
        int i;
        this.print = argsCpy[0].startsWith("P");
        this.allValueVector = new Vector();
        this.servers = new String[Integer.parseInt(argsCpy[1])];
        for (i = 0; i < this.servers.length; i++) {
            this.servers[i] = argsCpy[i + 2];
        }
        this.commands = new String[Integer.parseInt(argsCpy[this.servers.length + 2])];
        for (i = 0; i < this.commands.length; i++) {
            this.commands[i] = argsCpy[i + this.servers.length + 3];
        }
        this.nextNode = 0;
    }

    /**
     * --------------------------------------init-----------------------------------------
     * public method called by UWAgent to start the migration process and initialize
     * the startTime
     */
    public void init() {
        this.startTime = new Date();
        try {
            InetAddress inetAddress = InetAddress.getLocalHost();
            this.origin = inetAddress.getHostAddress();
            this.hop(this.servers[this.nextNode], "runAgent");
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    /**
     * ----------------------------------------runAgent---------------------------------------
     * public method that is executed on all nodes that this object migrates to,
     * each migration carries out the execution of all commands and then migrates to
     * either the next node or back to the original node.
     * 
     */
    public void runAgent() {
        if (this.print) {
            this.allValueVector.addElement(new String(
                    "\n==================================================================================="));
        }
        // iterate through commands calling execute(command)
        for (int command = 0; command < this.commands.length; command++) {
            Vector<String> vector = this.execute(this.commands[command]);
            if (this.print) {
                this.allValueVector.addElement(new String(this.servers[this.nextNode] + " command("
                        + this.commands[command] + "):..................................."));
            }
            for (int j = 0; j < vector.size(); j++) {
                this.allValueVector.addElement(vector.elementAt(j));
            }
        }
        // add blank space if printing
        if (this.print) {
            this.allValueVector.addElement(new String(""));
        }
        this.nextNode++;// increment nextNode number
        // if there are more nodes/servers to visit, hop to them, otherwise return to
        // the origin
        if (this.nextNode < this.servers.length) {
            this.hop(this.servers[this.nextNode], "runAgent");
        } else {
            // once the end of the servers is reached return to the origin node
            this.hop(this.origin, "result");
        }
    }

    /**
     * ---------------------------------------------------result---------------------------------
     * public method called once all migrations have happened and this object is
     * back to the node that was originally injected, prints the results requested
     * 
     */
    public void result() {
        System.out.println(
                "\nnServers = " + this.servers.length + ", server1 = " + servers[0] + ", command1 = " + commands[0]);
        if (this.print) {
            for (int i = 0; i < this.allValueVector.size(); i++) {
                System.out.println(this.allValueVector.elementAt(i));
            }
        } else {
            System.out.println("total = " + allValueVector.size());
        }
        Date endDate = new Date();
        System.err.println("Execution Time = " + (endDate.getTime() - this.startTime.getTime()));
    }

    /**
     * -----------------------------------execute------------------------------------------------
     * private method used once this object has migrated to a computing node that
     * executes the passed command and returns a vector containing the results.
     * 
     * @param command : linux command to be executed
     * @return Vector<String> : Vector containing all results from command execution
     */
    private Vector<String> execute(String command) {
        Vector<String> output = new Vector<String>();
        try {
            String line;
            Runtime runtime = Runtime.getRuntime();
            Process process = runtime.exec(command);
            InputStream input = process.getInputStream();
            BufferedReader bufferedInput = new BufferedReader(new InputStreamReader(input));
            while ((line = bufferedInput.readLine()) != null) {
                if (this.print) {
                    System.out.println(line);
                }
                output.addElement(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return output;
        }
        return output;
    }

}
