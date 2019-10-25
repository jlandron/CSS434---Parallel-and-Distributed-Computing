
/**
 *#############################################################################
 *#-------------------------- UnixClient --------------------------------------
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
 *  Uses the Instructor provided UnixServer, and assumes that input upon execution \
 *  will be in order as follows:
 *  
 *  java UnixClient P/S port# numberOfServers Ip1 Ip2... numberOfCommands Com1 Com2...
 * 
 *  if the order of this input is changed, the client will probably not run
 *  There is very little error checking in the methods, so input should be valid and
 *  rmiregistry port#& should be initialized
 * 
 *  To test this class on up to 3 servers, I created and used runRMITests.sh, 
 *  which takes one arg for port#
 * ------------------------------------------------------------------------------
 **/
import java.util.*;
import java.rmi.*;
import java.rmi.server.UnicastRemoteObject;

public class UnixClient extends UnicastRemoteObject {
    private boolean print = false;
    Vector<String> allValueVector;
    int total = 0;

    /**
     * --------------------------Constructor--------------------------------------
     * Initial UnixClient setup to setup the value of print for this object
     * 
     * @param print
     * @throws RemoteException
     */
    UnixClient(String print) throws RemoteException {
        this.print = print.startsWith("P");
    }

    public static void main(String[] args) throws RemoteException {
        if (args.length < 4) {
            System.err.println(
                    "usage: java UnixClient P/S port# numberOfServers Ip1 Ip2... numberOfCommands Com1 Com2... ");
            System.exit(-1);
        }
        UnixClient myClient = new UnixClient(args[0]);
        myClient.runClient(args);
    }

    /**
     * -----------------------------runClient------------------------------------
     * private method only called from main that initializes all connections and
     * saves them in an array This method also saves all commands to be run on the
     * remote servers and than passes all information to its helper method for RMI
     * on the servers
     * 
     * @param args : args array from main
     */
    private void runClient(String[] args) {
        allValueVector = new Vector<>();
        Date startTime = new Date(); // start total setup and run time
        int port = Integer.parseInt(args[1]);
        // establish how hany servers client will connect to
        int numConnections = Integer.parseInt(args[2]); // always the third item in args
        String[] serverNames = new String[numConnections];
        ServerInterface[] servers = new ServerInterface[numConnections];
        // establish how many commands client will execute
        int numCommands = Integer.parseInt(args[numConnections + 3]); // the number of commands will always be #3
        String[] commands = new String[numCommands];

        for (int i = 0; i < numConnections; i++) {
            try {
                serverNames[i] = args[i + 3];
                servers[i] = (ServerInterface) Naming.lookup("rmi://" + args[i + 3] + ":" + port + "/unixserver");
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
        // store all commands in array
        for (int i = 0; i < numCommands; i++) {
            // calculate offset for commands in args
            commands[i] = args[i + 4 + numConnections];
        }
        System.out.println("port = " + port + ", nServers = " + numConnections + ", server1 = " + serverNames[0]
                + ", command1 = " + commands[0]);
        executeLoop(serverNames, servers, commands);
        if (print) {
            for (int i = 0; i < allValueVector.size(); i++) {
                System.out.println(allValueVector.get(i));
            }
        }
        System.out.println("items = " + total);
        Date endTime = new Date();
        System.err.println("\nElapsed time = " + (endTime.getTime() - startTime.getTime()));
        System.exit(-1);
    }

    /**
     * ---------------------------------------executeLoop----------------------------------------
     * private method to that runs the RMI commands on all connected servers
     * extracted from runClient for readability
     * 
     * @param serverNames : array of strings containing the names of servers
     * @param servers     : array of serverInterfaces containing the actual server
     *                    connections
     * @param commands    : array of strings containing linux terminal commands
     */
    private void executeLoop(String[] serverNames, ServerInterface[] servers, String[] commands) {
        Vector<String> returnValueVector;
        // loop through all servers
        for (int server = 0; server < servers.length; server++) {
            if (print) {
                allValueVector.addElement(new String(
                        "\n==================================================================================="));
            }
            // loop through all commands
            for (int command = 0; command < commands.length; command++) {
                try {
                    // execute remote method on server and take the returned Vector
                    returnValueVector = servers[server].execute(commands[command]);
                    if (print) {
                        allValueVector.addElement(new String(serverNames[server] + " command(" + commands[command]
                                + "):..................................."));
                        for (int i = 0; i < returnValueVector.size(); i++) {
                            allValueVector.addElement(new String(returnValueVector.get(i)));
                        }
                    }
                    total += returnValueVector.size();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }
            }
            allValueVector.addElement(new String(""));
        }
    }
}