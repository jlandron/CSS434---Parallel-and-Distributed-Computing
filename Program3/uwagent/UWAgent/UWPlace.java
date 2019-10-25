package UWAgent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.Scanner;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

/**
 * Is a UWAgent execution daemon that should run at every host to exchange 
 * agents. It is used by UWAgent.java and UWInject.
 *
 * @author Koichi Kashiwagi, Duncan Smith, and Munehiro Fukuda (CSS, UWBothell)
 * @since  10/1/04
 * @version 8/8/06
 */
public class UWPlace implements Runnable {
    // Variables //////////////////////////////////////////////////////////////
    // UWPlace name

    private static String placeName = "UWPlace"; // user changeable

    // Maintaining incoming agents until they are activated as threads
    private Vector<UWAgent> incomingAgents = new Vector<UWAgent>();

    // Maintaining each agent's (ip/timestamp/id) and classes
    private HashMap<String, HashMap> classesHashMap = new HashMap<String, HashMap>();

    // Maintaining each agent's (ip/timestamp/id) and class loader
    private Hashtable<String, UWClassLoader> loaderHashtable = new Hashtable<String, UWClassLoader>();

    // Maintaining tunnelling ip name and local port
    private Hashtable<String, Integer> tunnelIpPort = new Hashtable<String, Integer>();

    // Keeping track of agents residing at this place as threads
    /**
     * Keeping track of agents residing at this place as threads
     */
    public List<AgentThread> agentsList = new ArrayList<AgentThread>();

    // Inter-agent communication
    private UWPSocket uwpsocket = null;                      // socket
    /**
     * IP port used for inter-place communication
     */
    public static String portnumber = UWUtility.DEFAULT_PORT;// port
    private boolean isSSL = false;                           // SSL or not
    private static int log = 100;                            // listen log size
    // network gateway, for over-gateway navigation/communication
    private static String gateway = "";

    // Time from thread interrupt to agent termination.
    // All threads in an agent to be terminated will receive an interrupt
    // and must complete their necessary work within this timeToKill msec.
    private static final int timeToKill = 2000; // 2sec
    public static AcceptThread acceptT; // accept thread

    // Socket handling functions //////////////////////////////////////////////
    /**
     * Creates a UWPSocket object in this place.
     * @param srvr a server socket used to create a UWPSocket object.
     */
    protected void startUWPSocket(ServerSocket srvr) {
        uwpsocket = new UWPSocket(srvr, getIsSSL(), tunnelIpPort);
    }

    /**
     * retrieves the UWPSocket object maintained in this UWPlace.
     * @return the UWPSocket object maintained in this UWPlace.
     */
    public UWPSocket getUWPSocket() {
        return uwpsocket;
    }

    // Agent information printing utilities ///////////////////////////////////
    /**
     * Prints out the names of all agents residing in this place.
     */
    public void printAgentInfo() {
        UWUtility.LogEnter();
        synchronized (agentsList) {
            UWUtility.LogInfo("agentsList.size = " + agentsList.size());
            for (int i = 0; i < agentsList.size(); i++) {
                UWAgent ag = ((AgentThread) agentsList.get(i)).uwA;
                UWUtility.LogInfo("ag = " + ag + ", ag.Name = " +
                        ag.getName());
            }
        }

        UWUtility.LogExit();
    }

    /**
     * Prints out the status of all agents residing in this palce.
     */
    public void agentStatus() {
        System.err.println("");
        System.err.println("-- Agent status --");
        synchronized (agentsList) {
            System.err.println("Number of agents: " + agentsList.size());
            System.err.println("ID\tName\t\t\tStatus");
            System.err.println("--\t----\t\t\t------");
            for (int i = 0; i < agentsList.size(); i++) {
                Thread th = ((AgentThread) agentsList.get(i)).thread;
                UWAgent ag = ((AgentThread) agentsList.get(i)).uwA;

                System.err.print(ag.getAgentId() + "\t");
                System.err.print(ag.getName() + "\t");

                if (((AgentThread) agentsList.get(i)).isSuspended()) {
                    System.err.print("Suspended");
                } else if (((AgentThread) agentsList.get(i)).isRunning()) {
                    System.err.print("Running");
                } else {
                    System.err.print("Ready");
                }
                System.err.println("");
            }
        }
        System.err.println("");
    }

    // Put and get utilities //////////////////////////////////////////////////
    /**
     * Gives this UWPlace a new name
     * @param name a name given to the place
     */
    public void setPlaceName(String name) {
        placeName = name;
    }

    /**
     * Retrieves this UWPlace's name
     * @return this place's name
     */
    public String getPlaceName() {
        return placeName;
    }

    /**
     * Sets this UWPlace's IP port
     * @param port an IP port given to this place
     */
    public void setPortNumber(String port) {
        portnumber = port;
    }

    /**
     * Retrieves this UWPlace's port number
     * @return this UWPlace's port number
     */
    public String getPortNumber() {
        return portnumber;
    }

    /**
     * Specifies a gateway to another domain.
     * @param gateway a gateway to another domain
     */
    public void setGateway(String gateway) {
        gateway = gateway;
    }

    /**
     * Retrieves the gateway to another domain.
     * @return the gateway to another domain.
     */
    public String getGateway() {
        return gateway;
    }

    /**
     * Activates SSL communication.
     * @param ssl true to activate SSL communication, otherwise false.
     */
    protected void setIsSSL(boolean ssl) {
        isSSL = ssl;
    }

    /**
     * Checks if SSL communication is activated.
     * @return true if SSL communication is activated, otherwise false.
     */
    public boolean getIsSSL() {
        return isSSL;
    }

    // Constructors and Main //////////////////////////////////////////////////
    /**
     * Is the constructor called from main( ).
     */
    public UWPlace() {
        super();
        UWUtility.LogEnter();
	// start the command thread
	Thread command = new Thread( this );
	command.start( );
        UWUtility.LogExit();
    }

    /**
     * Is the UWPlace command-interpretor thread.
     */
    public void run( ) {
	// create a keyboard input to read commands
	Scanner keyboard = new Scanner( System.in );	

	boolean background = false;
	
	// keep reading a next command
	while ( keyboard.hasNextLine( ) ) { 
	    String command = keyboard.nextLine( );
	    System.out.println( "command = " + command );

	    // interpret a command
	    if ( command.equals( "quit" ) || command.equals( "QUIT" ) ) {
		quit( );
	    }
	    if ( command.equals( "background" ) || command.equals( "BACKGROUND" ) ) {
		background = true;
	    }
	}
	if ( background == false )
	    quit( );
	// otherwise keep running even after stdin is closed!
    }

    /**
    * Is called from run( ) to quit UWPlace.
    */
    public static void quit( ) {
	System.err.println( "UWPlace: terminated" );
	System.exit( 0 );
    }

    /**
     * Is the main program to start a UWAgent daemon at the local host.
     * @param args options 
     *             <ol>
     *             <li> -n name:    set the UWPlace name
     *             <li> -p port:    set the IP port number
     *             <li> -g gatway:  set the gateway name to another domain
     *             <li> -e:         activate SSL (encryption).
     *             </ol>
     */
    public static void main(String[] args) {
        UWUtility.LogEnter();

        try {
            // Instantiate UWPlace
            UWPlace uwplace = new UWPlace();

            // Expects 5 options
            // -n : UWPlace name
            // -p : port number
            // -g : the gateway name to another domain
            // -e : activate SSL (encryption).
            // -digits : SSH tunnelling that binds local port to remote ip
            if (args.length >= 1) {
                uwplace.setCommandArgs(args);
            }

            // Listen on the UWPlace port number using a server socket
            int portNum = Integer.parseInt(uwplace.getPortNumber());
            ServerSocket srvr = null;
            if (uwplace.getIsSSL()) {
                SSLServerSocketFactory sslserversocketfactory =
                        (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
                srvr =
                        sslserversocketfactory.createServerSocket(portNum, log);
            } else {
                srvr = new ServerSocket(portNum, log);
            }
            UWUtility.LogInfo("Server listening on port " + portNum);

            // create a UWPSocket
            uwplace.startUWPSocket(srvr);

            // instantiate an accept thread.
            acceptT = new AcceptThread(uwplace, srvr);



            // Busy waiting for UWAgent to come
            while (true) {
                UWUtility.LogInfo("start of main while loop");
                uwplace.engine(uwplace);
                UWUtility.LogInfo("end of main while loop");
            }
        } catch (IOException ioe) {
            UWUtility.Log(ioe.toString());
            UWUtility.Log("Cause: " + ioe.getCause());
        }
    }

    /**
     * Analyzes command arguments.
     * @param commandArgs argments passed to main( ).
     */
    public void setCommandArgs(String[] commandArgs) {
        UWUtility.LogEnter();

        CommandLineOptionAnalyzer analyser =
                new CommandLineOptionAnalyzer(commandArgs);

        Map<String, Object> map = analyser.getAnalyzedMap();

        // Set UWPlace name
        if (map.containsKey("-n")) {
            setPlaceName((String) map.get("-n"));
        }

        // Set port number
        if (map.containsKey("-p")) {
            setPortNumber((String) map.get("-p"));
        }

        // Set network gateway
        if (map.containsKey("-g")) {
            setGateway((String) map.get("-g"));
        }

        // Set whether to use SSL
        if (map.containsKey("-e")) {
            setIsSSL(true);
        } else {
            setIsSSL(false);
        }

        // Show usage
        if (map.containsKey("-h")) {
            System.err.println("Usage: java UWPlace " +
                    "[-n placeName] " + // UWPlace name
                    "[-p portNumber] " +// IP port number
                    "[-g gateway] " + // gateway to another domain
                    "[-e]" + // encryption
                    "[-localPort# remoteIpName]" // ssh tunnelling
                    );
            System.exit(0);
        }

        // Set ssh tunnelling ip address and port
        Set<String> keyset = map.keySet();
        Iterator<String> keys = keyset.iterator();
        while (keys.hasNext()) {
            String curKey = (String) keys.next();
            String portString = curKey.substring(1);
            try {
                int localPort = Integer.parseInt(portString);
                String remoteIp = (String) map.get(curKey);
                if (remoteIp != null) {
                    tunnelIpPort.put(remoteIp, new Integer(localPort));
                }
            } catch (NumberFormatException nfe) {
            }
        }

        // for debug
        Enumeration<String> tunnelIps = tunnelIpPort.keys();
        while (tunnelIps.hasMoreElements()) {
            String localPort = tunnelIps.nextElement();
            System.err.println("tunnelling " +
                    tunnelIpPort.get(localPort) +
                    " to " + localPort);
        }

        UWUtility.LogExit();
    }

    /**
     * Picks up a new agent from the incoming queue and starts it as a thread.
     * @param uwplace a place where a new agent can work.
     */
    private void engine(UWPlace uwplace) {
        UWUtility.LogEnter();

        // Get UWAgent from the vector list "incomingAgents"
        UWAgent ag = popAgent();

        if (ag != null) {
            UWUtility.LogInfo("ag != null");

            // Create a new AgentThread
            String groupName = ag.getName() + ag.getIp() + ag.getTimeStamp() + ag.getAgentId();
            ThreadGroup threadGroup = new ThreadGroup(groupName);
            AgentThread agentT = new AgentThread(ag, uwplace, threadGroup);

            // for management, insert this new thread in agentsList
            synchronized (agentsList) {
                agentsList.add(agentT); // in chronological order
            }
            printAgentInfo();
        }

        UWUtility.LogExit();
    }

    /**
     * Picks up a new agent from incomingAgnet. If there are no agents, wait
     * for a new agent's arrival.
     * @return a new agent
     */
    private UWAgent popAgent() {
        UWUtility.LogEnter();

        if (incomingAgents.isEmpty()) {
            // If "incomingAgents" is empty, wait for a new agent's arrival
            UWUtility.LogInfo("agents vector is empty, so waiting");
            synchronized (incomingAgents) {
                try {
                    incomingAgents.wait();
                } catch (Exception e) {
                    UWUtility.Log("UWPlace.popAgent: " + e.toString());
                    e.printStackTrace();
                }
            }
            UWUtility.LogExit();

            // there may be a new agent but return null anyway as expecting
            // that popAgent( ) will be called again soon.
            return null;
        }
        UWUtility.LogExit();
        // Remove and return a new agent from the vector list
        return ((UWAgent) incomingAgents.remove(0));
    }

    /**
     * REVISIT REVISIT !!!!
     */
    /*
    public void notifyAgents( ) {
    UWUtility.LogEnter( );
    synchronized ( incomingAgents ) {
    // wakes up the main thread (in charge of engine( )).
    incomingAgents.notify( );
    }
    UWUtility.LogExit( );
    }
     */

    // Receiving agents ///////////////////////////////////////////////////////
    /**
     * Receives an incoming UWAgent from network and put it in the 
     * incomingAgents queue.
     *
     * @param byteArrayAgent serialized state of agent
     * @param classesHash HashMap including pairs of class name and class file
     *                    (byte array)
     */
    public void receiveAgent(byte[] byteArrayAgent, HashMap classesHash) {

        UWUtility.LogEnter();

        // Extract class names from classesHash
        List<String> classNames = new ArrayList<String>();
        for (Iterator i = classesHash.keySet().iterator(); i.hasNext();) {
            String className = (String) i.next();
            classNames.add(className);
            UWUtility.LogInfo("[UWPlace(receiveAgent)]: Added class name: " + className);
        }

        try {
            // Convert the byte array to an instance of UWAgent.
            ByteArrayInputStream bais = new ByteArrayInputStream(byteArrayAgent);
            UWClassLoader ucl = new UWClassLoader(classesHash, getClass().getClassLoader());
            UWObjectInputStream ois = new UWObjectInputStream(bais, ucl, classNames);
            UWAgent ag = (UWAgent) ois.readObject();

            ois.close();
            bais.close();

            // Put the HashMap(classesHash) including byte array
            // representations into the HashMap(classesHashMap).
            pushClassHashMap(classesHash, ag.getIp(), ag.getTimeStamp(), ag.getAgentId());

            // Register this agent's classloader in loaderHashtable,
            pushLoaderHashtable(ucl, ag.getIp(), ag.getTimeStamp(), ag.getAgentId());

            // Allow messages to be received
            ag.activateMailbox();

            // Put the UWAgent object into the incomingAgent Vector.
            UWUtility.LogInfo("pushing agent id = " + ag.getAgentId());
            pushAgent(ag);

        } catch (Exception e) {
            UWUtility.Log(e.toString());
            UWUtility.Log("Cause: " + e.getCause());
        }
        UWUtility.LogExit();
    }

    /**
     * Pushes an incoming agent into the incomingAgent queue from which
     * the UWPlace engine picks up for its execution.
     *
     * @param ag an incoming agent to be pushed into the queue
     */
    private void pushAgent(UWAgent ag) {
        if (ag != null) {
            UWUtility.LogEnter("agentId = " + ag.getAgentId());
        }

        // Put UWAgent into vector list "incomingAgents"
        incomingAgents.addElement(ag);
        synchronized (incomingAgents) {
            // Notify the main thread (in charge of engine( ) )
            incomingAgents.notify(); // was originally notifyAll( )
        }

        UWUtility.LogExit();
    }

    /**
     * Registers a given agent's class hash table with its ip, time, and id.
     *
     * @param classesHash a given agent's class hash table.
     * @param ip the agent's ip where it was created
     * @param time the agent's timestamp when it was created
     * @param aId the agent's id that is unique in its agent domain.
     */
    public void pushClassHashMap(HashMap classesHash, InetAddress ip,
            long time, int aId) {

        // Create the key for a given agent's hash table from ip, time, and
        // anget id
        String key = ip.toString() + String.valueOf(time) + String.valueOf(aId);
        UWUtility.LogEnter("key = " + key);

        // debugging code
        UWUtility.LogInfo("[UWPlace ( pushClassHashMap)] put: " +
                " key = " + key);
        Set tmp = classesHash.keySet();
        Iterator it = tmp.iterator();
        while (it.hasNext()) {
            UWUtility.LogInfo("[UWPlace (puchClassHashMap)] contents: " +
                    it.next());
        }

        // Register the given agent's key and hash table in this place's
        // classes hash map
        synchronized (classesHashMap) { // hashmap is not thread-safe.
            classesHashMap.put(key, classesHash);
        }

        UWUtility.LogExit();
    }

    // Sending Agents /////////////////////////////////////////////////////////
    /**
     * Sends a specified UWAgent to the UWPlace running  on a specified host.
     * @param ag        an agent to send
     * @param hostName  the destination host name
     * @return true if a given agent has been sent in success
     */
    public boolean sendAgent(UWAgent ag, String hostName) {
        boolean success = false;
        if (ag == null) {
            return success;
        }

        UWUtility.LogEnter("agentId = " + ag.getAgentId());
        try {
            // retrieve this agent's class hash table from its ip, timestamp,
            // and id.
            HashMap classesHash =
                    getClassHashMap(ag.getIp(), ag.getTimeStamp(),
                    ag.getAgentId());
            // call the actual sendAgent( )
            success = sendAgent(ag, classesHash, hostName);
        } catch (Exception e) {
            UWUtility.Log(e.toString());
            UWUtility.Log("Cause: " + e.getCause());
        }
        UWUtility.LogExit();
        return success;
    }

    /**
     * Sends a specified UWAgent with its class name list to the UWPlace 
     * running  on a specified host.
     * @param ag         an agent to send
     * @param classNames a list of its class names
     * @param hostName   the destination host name
     * @return true if a given agent has been sent in success
     */
    public boolean sendAgent(UWAgent ag, List classNames, String hostName) {
        boolean success = false;

        if (ag == null) {
            return success;
        }

        UWUtility.LogEnter("agentId = " + ag.getAgentId());
        // retrieve this aent's class hash table from its ip, timestamp,
        // and id
        HashMap classesHash =
                getClassHashMap(ag.getIp(), ag.getTimeStamp(),
                ag.getParentId(ag.getAgentId()));

        UWUtility.LogExit();
        // call the actual sendAgent )
        success = sendAgent(ag, classesHash, hostName);
        return success;
    }

    /**
     * Send a specified UWAgent to the UWPlace that runs on the specified
     * host.
     *
     * @param ag an instance of agent
     * @param classesHash HashMap including pairs of class name and class file
     *                    (byte array)
     * @param hostName host name that ag is sent
     * @return true if a given agent has been sent in success
     */
    public boolean sendAgent(UWAgent ag, HashMap classesHash, String hostName) {
        boolean success = false;
        if (ag == null) {
            return success;
        }
        UWUtility.LogEnter("agentId = " + ag.getAgentId());

        try {
            // Write the byte representation of the agent to the output
            // stream and send to the new place.
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(ag);
            oos.close();
            byte[] byteArrayObj = baos.toByteArray();
            baos.close();

            // Call receiveAgent at remote resource
            UWUtility.LogInfo("Calling receiveAgent at " + hostName +
                    " with isSSL = " + getIsSSL());
            OutputStream out = uwpsocket.initUWPSocket(UWUtility.MSG_TYPE_FUNC, // type
                    hostName, // dest
                    getPortNumber(), // ip port
                    "receiveAgent", // func
                    byteArrayObj.length, // param 1
                    classesHash.size(), // param 2
                    getIsSSL());
            // Header parameters are:
            //		bytes in byte representation of the agent
            //		number of classes
            //
            // classesHash stores one or more of the following pair of objects:
            // 		(class name, class file)
            // Class name is a string; class file is a byte array.
            // We will serialize classesHash(String, byte[]) as follows
            // for each pair:
            //  	string length|byte array length|string|byte array

            ByteBuffer byteBuff;	// one serialized pair
            ArrayList<ByteBuffer> classNameBytesPairs = new ArrayList<ByteBuffer>();  // Array to store all pairs

            // Iterate through set of object pairs, serialize each one,
            // and add each one to array
            for (Iterator iter = classesHash.keySet().iterator();
                    iter.hasNext();) {

                String className = (String) iter.next();
                byte[] bytClassName = className.getBytes("UTF8");
                byte[] bytClass = (byte[]) classesHash.get(className);

                // Allocate space for
                //               2 integers + class name + class byte array
                byteBuff = ByteBuffer.allocate(UWUtility.INT_SIZE * 2 +
                        bytClassName.length +
                        bytClass.length);
                // 1st integer
                byteBuff.putInt(bytClassName.length); // class name length
                // 2nd integer
                byteBuff.putInt(bytClass.length);     // byte array length
                // Insert class name (as byte array)
                byteBuff.put(bytClassName);
                // Insert class byte (as byte array)
                byteBuff.put(bytClass);
                // Add to array
                classNameBytesPairs.add(byteBuff);

                UWUtility.LogInfo("UWPlace(sendAgent)] send..." +
                        "className = " + className);
            }

            // Send classes to destination for function call
            // Send byte representation of the agent first
            out.write(byteArrayObj);

            // size of each (class name and byte) pair
            ByteBuffer sizeOfClassNameBytesPair;
            for (Iterator iter = classNameBytesPairs.iterator();
                    iter.hasNext();) {
                ByteBuffer bb = (ByteBuffer) iter.next();
                sizeOfClassNameBytesPair = ByteBuffer.allocate(UWUtility.INT_SIZE);
                sizeOfClassNameBytesPair.putInt(bb.array().length);
                // send size of class name and bytes pair
                out.write(sizeOfClassNameBytesPair.array());
                // send serialized class name and bytes pair
                out.write(bb.array());
            }

            // Close the socket
            // out.close( );
            uwpsocket.returnOutputStream(out);
            success = true;
        } catch (Exception e) {
            UWUtility.Log(e.toString());
            UWUtility.Log("Cause: " + e.getCause());
        }
        UWUtility.LogExit();
        return success;
    }

    // Agent Management ///////////////////////////////////////////////////////
    /**
     * Suspends the agent specified with a given identifier.
     * @param agentId the identifier of a thread to suspend.
     */
    public void suspendAgent(int agentId) {
        // scan all agent threads till the thread corresponding to a given id
        // is found.
        AgentThread aTh = null;
        synchronized (agentsList) {
            for (int i = 0; i < agentsList.size(); i++) {
                aTh = (AgentThread) agentsList.get(i);
                if (aTh.uwA.getAgentId() == agentId) {
                    // found it!
                    break;
                }
            }
        }
        if (aTh == null || aTh.uwA.getAgentId() != agentId) {
            UWUtility.Log("Agent " + agentId + " not found");
        } else {
            // now suspend it.
            aTh.suspend();
            System.err.println("Agent " + agentId +
                    " (" + aTh.uwA.getName() + ") suspended");
        }
    }

    /**
     * Resumes the agent specified with a given identifier.
     * @param agentId the identifier of a thread to resume.
     */
    public void resumeAgent(int agentId) {
        // scan all agent threads till the thread corresponding to a given id
        // is found.
        AgentThread aTh = null;
        synchronized (agentsList) {
            for (int i = 0; i < agentsList.size(); i++) {
                aTh = (AgentThread) agentsList.get(i);
                if (aTh.uwA.getAgentId() == agentId) {
                    // found it!
                    break;
                }
            }
        }
        if (aTh == null || aTh.uwA.getAgentId() != agentId) {
            UWUtility.Log("Agent " + agentId + " not found");
        } else {
            // now resume it.
            aTh.resume();
            System.err.println("Agent " + agentId +
                    " (" + aTh.uwA.getName() + ") resumed");
        }
    }

    /**
     * Kills the agent with a given identifier working in the same agent
     * domain. All threads working within this agent are also terminated
     * together.
     * @param agentId the identifier of an agent to kill.
     */
    public void killAgent(int agentId) {
        // ipName = null, timestamp = 0, agentId >= 0
        killAgent(null, 0, agentId);
    }

    /**
     * Kills the agent specified with a given triplet of ip, timestamp, and
     * identifier. All threads working within this agent are also terminated
     * together.
     * @param ipName the ip name where this agent was originally created.
     * @param timeStamp the time stampe when this agent was created.
     * @param agentId the idenfitifer of this agent.
     */
    public void killAgent(String ipName, long timeStamp, int agentId) {
        boolean hit = false;

        synchronized (agentsList) {
            // for all agents in the list
            int size = agentsList.size();
            for (int i = 0; i < size; i++) {
                // examine the agent i
                AgentThread ag = (AgentThread) agentsList.get(i);


                // examine if this is the one to kill
                // check ip name
                hit = (ipName == null) ? true : ipName.equals(ag.uwA.getIp().toString());
                if (hit) {
                    // check time stamp
                    hit = (timeStamp == 0) ? true : (timeStamp == ag.uwA.getTimeStamp());
                    if (hit) {
                        // check identifier
                        hit = (agentId == -1) ? true : (agentId == ag.uwA.getAgentId());
                    }
                }
                if (hit) {
                    // all hit!
                    // interrupt all threads running within this agent
                    System.out.println("UWPlace.KillAgent: will interrupt");
                    ag.group.interrupt();
                    System.out.println("UWPlace.KillAgent: interrupted");
                    try {
                        // give some milli seconds to allow all the threads
                        // to complete their necessary work before a
                        // termination
                        Thread.currentThread().sleep(timeToKill);
                    } catch (Exception e) {
                    }

                    // remove this agent from the list
                    // agentsList.remove( i );
                    // The interrupted agent itself remove itself from the list

                    // this agent and all its threads are terminated.

                    System.out.println("UWPlace.KillAgent: will stop");
                    agentsList.remove(ag); // ag may be stuck, so remove it!
                    UWUtility.Log("agentsList.size = " + agentsList.size());
                    ag.group.stop();
                    System.out.println("UWPlace.KillAgent: stopped");
                    break;
                }
            }
        }
    }

    /**
     * Retrieves the agent specified with a given identifier.
     * @param agentId the identifier of an agent to retrieve
     * @return the specified agent or nulll if it is not found at this place.
     */
    public UWAgent getAgentById(int agentId) {
        synchronized (agentsList) {
            UWUtility.LogEnter();
            UWUtility.LogInfo("Number of agents here: " +
                    agentsList.size());

            // examine all agents at this place in a reverse chronological
            // order. there may be two or more agents with same id (active
            // and zombie agents). pick up the latest, (i.e., active) agent.
            for (int i = agentsList.size() - 1; i >= 0; i--) {
                AgentThread at = (AgentThread) agentsList.get(i);
                UWAgent ag = at.uwA;
                UWUtility.LogInfo("The agent at position " + i +
                        " in the agent list is agent " +
                        ag.getAgentId() + " of " + ag);
                if (ag.getAgentId() == agentId) {
                    // if this agent's id is the same as a given id.
                    UWUtility.LogInfo("Found agent " + agentId);
                    return ag; // return it!
                }
            }
        }
        UWUtility.LogExit();

        return null; // otherwise null.
    }

    // Class-making functions /////////////////////////////////////////////////
    /**
     * Makes the class of a given name from a byte array found in the class
     * hash table that belongs to an agent specified with a triplet of ip, 
     * timestamp, and agent id.
     *
     * @param ip   the ip address where a given agent was originally 
     *             created.
     * @param time the timestamp when a given agent was originally created.
     * @param aId  the identifier of a given agent.
     * @param className the name of a class to be made
     * @return the class of the given name if the corresponding byte array
     *         was found, otherwise null.
     */
    public Class makeClass(InetAddress ip, long time, int aId,
            String className) {
        UWUtility.LogEnter("agentId = " + aId);

        // retrieve the classloader associated with this agent
        UWClassLoader ucl = getLoaderHashtable(ip, time, aId);

        UWUtility.LogExit();
        try {
            // find and make the class of a given class name.
            return ucl.findClass(className);
        } catch (Exception e) {
            UWUtility.Log(e.toString());
            UWUtility.Log("Cause: " + e.getCause());
        }
        return null;
    }

    /**
     * Retrieves a class hash table corresponding to a given
     * triplet of ip, time, and agent id from this UWPlace's hash table.
     * It is used when making a class belonging to the agent specified with
     * ip, tim, and agent id.
     * (Note: it must be public, because it may be called at a user level for
     *  agent retrieval.)
     *
     * @param ip   the ip address where a given agent was originally 
     *             created.
     * @param time the timestamp when a given agent was originally created.
     * @param aId  the identifier of a given agent.
     * @return the class hash table corresponding to this agent.
     */
    public HashMap getClassHashMap(InetAddress ip, long time, int aId) {
        // create a key from ip, timpestamp, and identifier
        String key = ip.toString() + String.valueOf(time) + String.valueOf(aId);
        UWUtility.LogEnter("key = " + key);

        // debugging code
        UWUtility.LogInfo("[UWPlace (getClassHashMap)] key: " + key);
        synchronized (classesHashMap) { // hashmap is not thread-safe.
            UWUtility.LogExit();
            // retrieve the corresponding hash table.
            HashMap m = (HashMap) classesHashMap.get(key);
            Iterator it = m.keySet().iterator();
            while (it.hasNext()) {
                UWUtility.LogInfo("[UWPlace (getClassHashMap)] contents = " +
                        (String) it.next());
            }

            return (HashMap) classesHashMap.get(key);
        }
    }

    /**
     * Retrieves and removes a class hash table corresponding to a given
     * triplet of ip, time, and agent id from this UWPlace's hash table.
     * It is used when removing the given agent from the UWPlace.
     * (Note: it is called from an AgentThread that actuall runs an agent, and
     *  thus must be public.)
     *
     * @param ip   the ip address where an outgoing agent was originally 
     *             created.
     * @param time the timestamp when an outgoing agent was originally created.
     * @param aId  the identifier of an outgoing agent.
     * @return the class hash table corresponding to this agent.
     */
    public HashMap popClassHashMap(InetAddress ip, long time, int aId) {
        // create a key from ip, timestamp, and id.
        String key = ip.toString() + String.valueOf(time) + String.valueOf(aId);
        UWUtility.LogEnter("key = " + key);

        synchronized (classesHashMap) { // hashmap is not thread-safe.
            // pop the corresponding hash table.
            HashMap classesHash = (HashMap) classesHashMap.remove(key);

            UWUtility.LogExit();
            return classesHash;
        }
    }

    /**
     * Registers a given agent's class loader into the loader hash table.
     * @param ucl  The UWClassLoader object associated with a given agent.
     * @param ip   the ip address where a given agent was originally created.
     * @param time the timestamp when a given agent was originally created.
     * @param aId  the identifier of a given agent.
     */
    private void pushLoaderHashtable(UWClassLoader ucl,
            InetAddress ip, long time, int aId) {
        // create a key from ip, timestamp, and id.
        String key = ip.toString() + String.valueOf(time) + String.valueOf(aId);
        loaderHashtable.put(key, ucl);
    }

    /**
     * Retrieves a given agent's class loader from the loader hash table.
     * @param ip   the ip address where a given agent was originally created.
     * @param time the timestamp when a given agent was originally created.
     * @param aId  the identifier of a given agent.
     * @return ucl  The UWClassLoader object associated with a given agent.
     */
    public UWClassLoader getLoaderHashtable(InetAddress ip,
            long time, int aId) {
        // create a key from ip, timestamp, and id.
        String key = ip.toString() + String.valueOf(time) + String.valueOf(aId);

        return (UWClassLoader) loaderHashtable.get(key);
    }

    /**
     * Removes a given agent's class loader from the loader hash table.
     * (Note: it is called from an AgentThread that actuall runs an agent.)
     * @param ip   the ip address where a given agent was originally created.
     * @param time the timestamp when a given agent was originally created.
     * @param aId  the identifier of a given agent.
     * @return ucl  The UWClassLoader object associated with a given agent.
     */
    public UWClassLoader popLoaderHashtable(InetAddress ip,
            long time, int aId) {
        // create a key from ip, timestamp, and id.
        String key = ip.toString() + String.valueOf(time) + String.valueOf(aId);

        return (UWClassLoader) loaderHashtable.remove(key);
    }
}

// AcceptThread ///////////////////////////////////////////////////////////////
/**
 * Is a thread in charge of accepting a new connection. It is instantiated
 * by the main thread bofer going into engine( ).
 */
class AcceptThread implements Runnable {
    // Variables

    Thread thread;
    UWPlace uwP;
    ServerSocket srvr;

    /**
     * Is the constructor that waits for a new connection through a given
     * server socket.
     * @param uwplace the local place
     * @param srvrSock a server socket through which this thread will accept
     *                  new connections.
     */
    AcceptThread(UWPlace uwplace, ServerSocket srvrSock) {
        UWUtility.LogEnter();

        // Instantiate thread
        thread = new Thread(this, "acceptThread");

        // Initialize
        uwP = uwplace;
        srvr = srvrSock;

        // Start the thread
        UWUtility.LogExit();
        thread.start();
    }

    /**
     * Is the main body of the accept thread. 
     */
    public void run() {
        UWUtility.LogEnter();

        Vector<SocketThread> socketThrs = new Vector<SocketThread>();

        // repeat accepting a new connection, allocating a thread to it, and
        // garbage-collecting zombie threads.
        while (true) {
            Socket skt = null;
            try {
                UWUtility.LogInfo("[debug: UWPlace(acceptThread-run)] " +
                        "waiting for agent");

                // srvr.accept() blocks until client connects
                if (uwP.getIsSSL()) {
                    skt = (SSLSocket) srvr.accept();
                } else {
                    skt = srvr.accept();
                }
                UWUtility.LogInfo("AcceptThread accepted a connection");
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (skt == null) {
                System.err.println("[UWPlace (socketThread-run)] accept " +
                        "but socket is null. continue.");
                // acception failed. repeat accepting a new connection
                continue;
            }

            // Allocate a new thread to this connection..
            // System.err.println("skt accepted from "+ skt.getInetAddress( ));
            SocketThread socketT = new SocketThread(uwP, skt);
            socketThrs.add(socketT);

            // Garbage-collect all zombie threads from the list.
            int size = socketThrs.size();
            for (int i = size - 1; i >= 0; i--) {
                SocketThread thr = (SocketThread) socketThrs.elementAt(i);
                if (!thr.isAlive()) {
                    socketThrs.remove(thr);
                }
            }
        }
    }
}

// SocketThread ///////////////////////////////////////////////////////////////
/**
 * Is used to retrieve function call information from a socket.
 */
class SocketThread implements Runnable {
    // Variables 

    Thread thread;                       // This thread
    UWPlace uwP;                         // The local engine
    Socket skt;                          // An established connection
    boolean isAlive[] = new boolean[1];  // True till end of run( )

    // Performance
    Date startTime = new Date();
    String fName = null;
    String mName = null;
    UWMessage msg = null;
    String dest = null;

    /**
     * Is the constructor that activates a new thread to work on a given 
     * socket.
     * @param uwplace the local place.
     * @param socket the socket through which this thread receives new data.
     */
    SocketThread(UWPlace uwplace, Socket socket) {
        UWUtility.LogEnter();

        // Instantiate thread
        Date time = new Date();
        thread = new Thread(this, "socketThread_" + time.getTime());

        // Initialize internal variables.
        uwP = uwplace;
        skt = socket;
        synchronized (isAlive) {
            isAlive[0] = true; // true till end of run( ).
        }

        UWUtility.LogExit();

        // Activate a thread
        try {
            thread.start();
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    /**
     * Gets this thread name.
     * 
     * @return this thread name
     */
    public String getName() {
        return thread.getName();
    }

    /**
     * Is called from AcceptThread to check if this thread is dead and thus
     * garbage-collectible. We would like to know the end of run( ) earlier
     * than Thread.currentThread( ).isAlive( ).
     * @return true if this thread is still working in run( ).
     */
    public boolean isAlive() {
        synchronized (isAlive) {
            return isAlive[0];
        }
    }

    /**
     * Read <i>len</i> bytes from InputStream <i>in</i> into byte array buffer;
     * blocks until at least <i>len bytes</i> are available.
     * @param in the input stream to read
     * @param len the number of bytes to read
     * @return a byte array that includes new data
     */
    private byte[] readBytes(InputStream in, int len) {
        //UWUtility.LogEnter("len = " + len);

        // prepare the buffer to temporarily store new data.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            int bytesRead = 0, bytesLeft = len;
            do {
                byte[] buf = new byte[bytesLeft]; // allocate buffer
                bytesRead = in.read(buf);       // read through input stream
                bytesLeft -= bytesRead;
                baos.write(buf, 0, bytesRead);  // copy it to baos
                UWUtility.LogInfo("Read " + bytesRead + " bytes");
            } while (bytesLeft > 0);  // repeat reading till completion
        } catch (IOException ioe) {
            UWUtility.Log(ioe.toString());
            UWUtility.Log("Cause: " + ioe.getCause());
        } catch (Exception e) {
            UWUtility.Log(e.toString());
            UWUtility.Log("Cause: " + e.getCause());
        }

        //UWUtility.LogExit( );
        return baos.toByteArray(); // return new data in byte[]
    }

    /**
     * Returns the String representation (using UTF8 encoding) of the
     * first <i>len</i> bytes of ByteBuffer <i>bb</i>. String is trimmed of 
     * leading and trailing whitespace.
     *
     * @param bb  a byte buffer whose contents will be converted into a string.
     * @param len the length of bytes in the buffer that will be converted.
     * @return a string converted from the given byte buffer.
     */
    private String getString(ByteBuffer bb, int len) {
        // prepare a temporary buffer.
        byte[] buf = new byte[len];
        bb.get(buf);	// in byte array format
        String s = null;
        try {
            s = new String(buf, "UTF8");  // Decode using UTF8
            s = s.trim();                  // Triming or space padding
        } catch (UnsupportedEncodingException uee) {
            UWUtility.Log(uee.toString());
            UWUtility.Log("Cause: " + uee.getCause());
        }

        return s;
    }

    /**
     * Is the main body of SocketThread.
     */
    public void run() {
        UWUtility.LogEnter();
        UWUtility.LogInfo("UWPlace(socketThread).run( ) starts.");

        try {
            InputStream in = null;
            in = skt.getInputStream();

	    // check if this is a termination signal:
	    // if I received only a "q\n\r" within the next 5 seconds
	    // I'll quit!
	    for ( int i = 0; in.available( ) <= 3; i++ ) {
		if ( i >= 5 && in.available( ) > 0 ) {
		    UWPlace.quit( );
		}
		try {
		    Thread.currentThread( ).sleep( 1000 );
		} catch ( InterruptedException e ) {
		    e.printStackTrace( );
		}
	    }

            // Indicates this UWPlace is ready to accept UWAgent
            UWUtility.LogInfo("UWPlace is ready at localhost:" + uwP.getPortNumber() + "/" + uwP.getPlaceName() + ".");

            // Read message header
            byte[] header = readBytes(in, UWUtility.HEADER_SIZE);

            // Parse header
            // First, put header in a ByteBuffer
            ByteBuffer bb = ByteBuffer.allocate(header.length);
            bb.put(header);
            bb.rewind();
            // Now, decode each field
            int type = bb.getInt(); // Message type
            String strFuncName // Name of function to call
                    = getString(bb, UWUtility.FUNC_NAME_SIZE);

            // performance
            fName = strFuncName;

            // These two parameters are used for different purposes depending
            // on the function being called.
            int headerParam1 = bb.getInt();
            int headerParam2 = bb.getInt();
            // Done parsing header

            UWUtility.LogInfo("Received a header.");
            UWUtility.LogInfo("Type field is: " + type);
	    try {
		InetAddress src = skt.getInetAddress( );
		UWUtility.LogInfo( " Source: " + src.getCanonicalHostName( ) );
	    } catch ( Exception e ) {
	    }
            UWUtility.LogInfo(" Function name is: " + strFuncName);
            UWUtility.LogInfo(" Header parameter 1: " + headerParam1);
            UWUtility.LogInfo(" Header parameter 2: " + headerParam2);

            // This is the complete list of remote function calls supported
            // by SocketThread.
            // To provide support for other functions, add them here.
            if (strFuncName.equals("receiveAgent")) {
                int byteArraySize // Size of byte array representation of obj.
                        = headerParam1;
                int numClasses // Number of classes in ClassesHash
                        = headerParam2;

                // Read byte array representation of object
                byte[] byteArrayObj = readBytes(in, byteArraySize);

                // Read class hash map
                int hashSize = 0;
                byte[] bytHashSize;
                byte[] bytClassHash;
                HashMap<String, byte[]> classesHash = new HashMap<String, byte[]>();
                for (int i = 0; i < numClasses; i++) {
                    // Get size of next classesHash
                    bytHashSize = readBytes(in, UWUtility.INT_SIZE);

                    ByteBuffer bbh = ByteBuffer.allocate(bytHashSize.length);
                    bbh.put(bytHashSize);
                    bbh.rewind();
                    int intHashSize = bbh.getInt();

                    // Get serialized classesHash
                    bytClassHash = readBytes(in, intHashSize);

                    ByteBuffer bbch = ByteBuffer.allocate(bytClassHash.length);
                    bbch.put(bytClassHash);
                    bbch.rewind();

                    // Get string and byte array and insert into classesHash
                    int stringLength = bbch.getInt();
                    int byteArrayLength = bbch.getInt();
                    String className = getString(bbch, stringLength);

                    UWUtility.LogInfo("[UWPlace(socketThread-run)] " +
                            "deserialized class = " + className);

                    byte[] bytClassFile = new byte[byteArrayLength];
                    bbch.get(bytClassFile);
                    classesHash.put(className, bytClassFile);
                }

                // Close InputStream and client socket
                in.close();
                skt.close();

                // this will push the agent onto the stack
                uwP.receiveAgent(byteArrayObj, classesHash);

            } else if (strFuncName.equals("registerAgentIp")) {
                int senderId = headerParam1;	          // sending agent ID
                int bytGatewayNamesLength = headerParam2; // gateway names size

                // Read sending address
                byte[] rawSenderAddress = readBytes(in, UWUtility.INT_SIZE);

                // Read recipient agent ID
                byte[] bytRecvId = readBytes(in, UWUtility.INT_SIZE);
                ByteBuffer recvIdBuff = ByteBuffer.allocate(UWUtility.INT_SIZE);
                recvIdBuff.put(bytRecvId);
                recvIdBuff.rewind();
                int recvId = recvIdBuff.getInt();        // recipient agent id

                // Read byte[] representation of gateway array
                byte[] bytGateways = readBytes(in, bytGatewayNamesLength);
                String strGateways = null;
                try {
                    strGateways // Decode using UTF8
                            = new String(bytGateways, "UTF8");
                    strGateways = strGateways.trim();
                } catch (UnsupportedEncodingException uee) {
                    UWUtility.Log(uee.toString());
                    UWUtility.Log("Cause: " + uee.getCause());
                }
                UWUtility.LogInfo("strGateways = " + strGateways);

                // Convert a string presentation of gateways to a string array
                String[] gateways = null;
                try {
                    gateways = strGateways.split(" ");
                    if (gateways.length == 1 && gateways[0].length() == 0) {
                        gateways = null;
                    }
                } catch (java.util.regex.PatternSyntaxException pse) {
                    UWUtility.Log(pse.toString());
                    UWUtility.Log("Cause: " + pse.getCause());
                }
                UWUtility.LogInfo("gateways[0] = " + ((gateways != null) ? gateways[0] : null));

                UWAgent ag = null;
                do {
                    // retrieve recipient agent
                    ag = uwP.getAgentById(recvId);
                    if (ag == null) {
                        UWUtility.Log("Agent " + recvId +
                                " not found. Retrying.");
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                        }
                    }
                } while (ag == null);
                // Sending address
                InetAddress ti = InetAddress.getByAddress(rawSenderAddress);
                UWUtility.LogInfo("Calling ag.registerAgentIp; agentId = " +
                        senderId +
                        ", location to register = " + ti +
                        ", recipient Id = " + recvId);

                // close the socket connection.
                in.close();
                skt.close();

                // register sending agent id and address with recipient agent
                ag.registerAgentIp(senderId, ti, gateways, 0); // gwPos==0

            } else if (strFuncName.equals("registerAgentIpGateway")) {
                // Gateway version of registerAgentIp

                int senderId = headerParam1;	// sender agent id
                int gatewaysLength = headerParam2; // length of gateways string
                UWUtility.LogInfo("registerAgentIpGateway");
                UWUtility.LogInfo("senderId = " + senderId);
                UWUtility.LogInfo("gatewaysLength = " + gatewaysLength);

                // Read byte[] representation of sender address
                byte[] bytRawSenderAddress = readBytes(in,
                        UWUtility.INT_SIZE);

                // Read byte[] representation of gateway array
                byte[] bytGateways = readBytes(in, gatewaysLength);
                String strGateways = null;
                try {
                    strGateways // Decode using UTF8
                            = new String(bytGateways, "UTF8");
                    strGateways = strGateways.trim();
                } catch (UnsupportedEncodingException uee) {
                    UWUtility.Log(uee.toString());
                    UWUtility.Log("Cause: " + uee.getCause());
                }
                UWUtility.LogInfo("strGateways = " + strGateways);

                // Convert a str representation fo gateways to a string array
                String[] gateways = null;
                try {
                    gateways = strGateways.split(" ");
                } catch (java.util.regex.PatternSyntaxException pse) {
                    UWUtility.Log(pse.toString());
                    UWUtility.Log("Cause: " + pse.getCause());
                }
                UWUtility.LogInfo("gateways[0] = " + gateways[0]);

                // Read destination host name
                byte[] bytDestHostName = readBytes(in, UWUtility.HOSTNAME_SIZE);
                String strDestHostName = null;
                try {
                    strDestHostName // Decode using UTF8
                            = new String(bytDestHostName, "UTF8");
                    strDestHostName = strDestHostName.trim();
                } catch (UnsupportedEncodingException uee) {
                    UWUtility.Log(uee.toString());
                    UWUtility.Log("Cause: " + uee.getCause());
                }
                UWUtility.LogInfo("strDestHostName = " + strDestHostName);

                // Read position in list of gateways
                byte[] bytGwpos = readBytes(in, UWUtility.INT_SIZE);
                int gwPos = UWUtility.BytesToInt(bytGwpos);
                UWUtility.LogInfo("gwPos = " + gwPos);

                // Read the receiver agent id
                byte[] bytRecvId = readBytes(in, UWUtility.INT_SIZE);
                int recvId = UWUtility.BytesToInt(bytRecvId);
                UWUtility.LogInfo("recvId = " + recvId);

                // close the socket connection.
                in.close();
                skt.close();

                // forwarding the message to the next gateway or the final
                // destination
                gwPos--;
                if (gwPos <= 0) {
                    // We're done traversing gateways, so call registerAgentIp
                    // on the destination node

                    // open a socket
                    UWPSocket socket = uwP.getUWPSocket();
                    OutputStream out =
                            socket.initUWPSocket(UWUtility.MSG_TYPE_FUNC, // type
                            strDestHostName, // dest
                            uwP.getPortNumber(), // port
                            "registerAgentIp", // func
                            senderId, // arg1
                            bytGateways.length, // arg2
                            uwP.getIsSSL());

                    // performance
                    dest = strDestHostName;

                    // write the byte[] representation of the InetAddress
                    out.write(bytRawSenderAddress);
                    out.write(bytRecvId);  // from agent Id
                    out.write(bytGateways);// send the gateways list to dest.

                    // close the socket
                    socket.returnOutputStream(out);

                } else {
                    // We still have gateways to traverse, so recursively call
                    // registerAgentIpGateway on the next gateway

                    // open a socket
                    UWPSocket socket = uwP.getUWPSocket();
                    OutputStream out =
                            socket.initUWPSocket(UWUtility.MSG_TYPE_FUNC, // type
                            gateways[gwPos], // next hop
                            uwP.getPortNumber(), // port
                            "registerAgentIpGateway", // func
                            senderId, // arg1
                            bytGateways.length, // arg2
                            uwP.getIsSSL());

                    // performance
                    dest = gateways[gwPos];

                    out.write(bytRawSenderAddress); // write sender address
                    out.write(bytGateways);     // write gateway array
                    out.write(bytDestHostName); // write dest host name

                    ByteBuffer gwPosBuffNew // convert gateway pos to byte
                            = ByteBuffer.allocate(UWUtility.INT_SIZE);
                    gwPosBuffNew.putInt(gwPos);
                    out.write(gwPosBuffNew.array()); //send new gateway pos
                    out.write(bytRecvId);             // write recv agent id

                    // close the socket
                    socket.returnOutputStream(out);
                }

            } else if (strFuncName.equals("enqueueMessage")) {
                // receive and enqueue a user-level message in the local
                // mailbox
                int recvId = headerParam1;             // receiver agent id
                int messageLength = headerParam2;      // message size in bytes

                // retrieve the recipient agent
                UWUtility.LogInfo("UWPlace.socketThread: " +
                        "Attempting to retrieve agent ID " +
                        recvId);

                UWAgent ag = null;
                ag = uwP.getAgentById(recvId);

                // Read byte[] representation of UWMessage
                byte[] bytMessage = readBytes(in, messageLength);

                // prepare an ack that indicates a result of message
                // acceptance.
                byte ack[] = new byte[1];
                ack[0] = -1; // fault


                UWMessage message = null;

                if (ag != null) {
                    // Convert the byte array to an instance of UWMessage.
                    UWClassLoader ucl = uwP.getLoaderHashtable(ag.getIp(),
                            ag.getTimeStamp(),
                            ag.getAgentId());
                    message = convertBytesToMessage(bytMessage, ucl);

                    if (ag.enqueueMessage(message) == true) // accepted in success
                    {
                        ack[0] = 1;
                    }

                } else {
                    // Convert the byte array to an instance of UWMessage.
                    // this may cause a class-not-found exception, because
                    // the message may include an agent-specific class.
                    message = convertBytesToMessage(bytMessage);

                    // no corresponding receiver agent.
                    if (uwP.agentsList.size() > 0) {
                        // some other agents running at this place
                        UWUtility.Log("UWPlace.socketThread: trying to " +
                                "deliver a message from Agent " +
                                message.getSendingAgentId() +
                                " to Agent " + recvId +
                                " that was not found here!");
                        ack[0] = -2;
                    } else {
                        // no agents running at this place
                        UWUtility.Log("UWPlace.socketThread: " +
                                "enqueueMessage failed." +
                                " No agents running at this UWPlace.");
                        message.showRouteInfo();
                        ack[0] = -3;
                    }
                }

                // return an ack if it is not a system message
                returnAck(message, ack);

                // close the socket connection.
                in.close();
                skt.close();

                // performance
                if (message != null) {
                    mName = message.getMessageHeader()[0];
                    msg = message;
                }

            } else if (strFuncName.equals("enqueueMessageGateway")) {
                // Gateway version of enqueueMessage:
                // receive and enqueue a user-level message in the local
                // mailbox

                int messageLength = headerParam1; // length of serialized UWMessage
                int gatewaysLength = headerParam2; // length of gateways string
                UWUtility.LogInfo("enqueueMessageGateway");
                UWUtility.LogInfo("messageLength = " + messageLength);
                UWUtility.LogInfo("gatewaysLength = " + gatewaysLength);

                // Read byte[] representation of gateway array
                byte[] bytGateways = readBytes(in, gatewaysLength);
                String strGateways = null;
                try {
                    strGateways =
                            new String(bytGateways, "UTF8"); // Decode using UTF8
                    strGateways = strGateways.trim();
                } catch (UnsupportedEncodingException uee) {
                    UWUtility.Log(uee.toString());
                    UWUtility.Log("Cause: " + uee.getCause());
                }
                UWUtility.LogInfo("strGateways = " + strGateways);

                // Convert a string presentation of gateways to a string array
                String[] gateways = null;
                try {
                    gateways = strGateways.split(" ");
                } catch (java.util.regex.PatternSyntaxException pse) {
                    UWUtility.Log(pse.toString());
                    UWUtility.Log("Cause: " + pse.getCause());
                }
                UWUtility.Log("gateways[0] = " + gateways[0]);

                // Read destination host name
                byte[] bytHostName = readBytes(in, UWUtility.HOSTNAME_SIZE);

                String strHostName = null;
                try {
                    strHostName =
                            new String(bytHostName, "UTF8"); // Decode using UTF8
                    strHostName = strHostName.trim();
                } catch (UnsupportedEncodingException uee) {
                    UWUtility.Log(uee.toString());
                    UWUtility.Log("Cause: " + uee.getCause());
                }
                UWUtility.LogInfo("strHostName = " + strHostName);

                // Read position in list of gateways
                byte[] bytGwpos = readBytes(in, UWUtility.INT_SIZE);

                int gwPos = UWUtility.BytesToInt(bytGwpos);
                UWUtility.LogInfo("gwPos = " + gwPos);

                // Read receiving agent id
                byte[] bytReceivingAgent = readBytes(in, UWUtility.INT_SIZE);

                int receivingAgentId = UWUtility.BytesToInt(bytReceivingAgent);
                UWUtility.LogInfo("receivingAgentId = " + receivingAgentId);

                // Read byte[] representation of UWMessage
                byte[] bytMessage = readBytes(in, messageLength);
                // Convert the byte array to an instance of UWMessage.
                UWMessage message = convertBytesToMessage(bytMessage);

                // prepare an ack that indicates a result of message
                // acceptance.
                byte ack[] = new byte[1];
                ack[0] = -1; // fault

                // forwarding the message to the next gateway or final host.
                UWUtility.LogInfo("gwPos = " + gwPos);
                gwPos--;
                if (gwPos <= 0) {
                    // We're done traversing gateways, so call enqueueMessage
                    // on the destination node.
                    // open a socket.
                    UWPSocket socket = uwP.getUWPSocket();
                    OutputStream out =
                            socket.initUWPSocket(UWUtility.MSG_TYPE_FUNC, // type
                            strHostName, // dest
                            uwP.getPortNumber(), // port
                            "enqueueMessage", // func
                            receivingAgentId, // arg1
                            bytMessage.length, // arg2
                            uwP.getIsSSL());
                    // performance
                    dest = strHostName;

                    out.write(bytMessage);

                    // receive an ack if it is not a system message.
                    if (!message.getIsSystemMessage()) {
                        ack[0] = (byte) socket.getAckFromServer(out);
                        UWUtility.LogInfo("sendMessage: org = " +
                                message.getSendingAgentId() +
                                " via = " + this +
                                " dst = " +
                                message.getReceivingAgentId() +
                                " ack = " + ack[0] +
                                " header = " +
                                (message.getMessageHeader())[0]);
                    }

                    // close the socket.
                    socket.returnOutputStream(out);

                } else {
                    // We still have gateways to traverse, so recursively call
                    // enqueueMessageGateway on the next gateway
                    UWPSocket socket = uwP.getUWPSocket();
                    OutputStream out =
                            socket.initUWPSocket(UWUtility.MSG_TYPE_FUNC, // type
                            gateways[gwPos], // next hop
                            uwP.getPortNumber(), // port
                            "enqueueMessageGateway", // func
                            bytMessage.length, // arg1
                            bytGateways.length, // arg2
                            uwP.getIsSSL());

                    // performance
                    dest = gateways[gwPos];

                    out.write(bytGateways); // write gateway array
                    out.write(bytHostName); // write destination host name

                    ByteBuffer gwPosBuffNew = ByteBuffer.allocate(UWUtility.INT_SIZE);
                    gwPosBuffNew.putInt(gwPos);

                    out.write(gwPosBuffNew.array()); //send new gateway pos
                    out.write(bytReceivingAgent); // write receiving agent id
                    out.write(bytMessage);        // write the UWMessage

                    // receive an ack if it is not a system message.
                    if (!message.getIsSystemMessage()) {
                        ack[0] = (byte) socket.getAckFromServer(out);
                        UWUtility.LogInfo("sendMessage: org = " +
                                message.getSendingAgentId() +
                                " via = " + this +
                                " dst = " +
                                message.getReceivingAgentId() +
                                " ack = " + ack[0] +
                                " header = " +
                                (message.getMessageHeader())[0]);
                    }

                    // close the socket.
                    socket.returnOutputStream(out);
                }

                // return an ack if it is not a system message
                returnAck(message, ack);

                // close the socket connection.
                in.close();
                skt.close();

            } else if (strFuncName.equals("cacheAgentIp")) {
                // sending agent ID
                int senderId = headerParam1;

                // cache/flush flag... 0 = cache, -1 = flush
                boolean cache = (headerParam2 == 0) ? true : false;

                // Read sending address
                byte[] rawSenderAddress = readBytes(in, UWUtility.INT_SIZE);

                // Read recipient agent ID
                byte[] bytRecvId = readBytes(in, UWUtility.INT_SIZE);
                ByteBuffer recvIdBuff = ByteBuffer.allocate(UWUtility.INT_SIZE);
                recvIdBuff.put(bytRecvId);
                recvIdBuff.rewind();
                int recvId = recvIdBuff.getInt();

                // close the socket connection.
                in.close();
                skt.close();

		// check if the source is really reachable.
		boolean reachable = false;
		try {
                    InetAddress senderIp = InetAddress.getByAddress(rawSenderAddress);
		    UWPSocket socket = uwP.getUWPSocket( );
		    OutputStream out =
			socket.initUWPSocket( UWUtility.MSG_TYPE_FUNC,
					      senderIp.getHostName( ),
					      uwP.getPortNumber( ), "detectHost",
					      0, 0, uwP.getIsSSL( ) );
		    socket.returnOutputStream( out );
		    reachable = true;
		} catch ( Exception e ) {
		    // UWUtility.Log( e.toString( ) );
		    // e.printStackTrace( );
		}

		if ( reachable ) {
		    UWAgent ag = null;
		    for (int i = 0; i < 3; i++) {
			// retrieve recipient agent
			ag = uwP.getAgentById(recvId);
			if (ag != null) {
			    break;
			}
			
			// agent not found. retrying.
			try {
			    Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
		    }
		    
		    if (ag != null) {
			// Sending address
			InetAddress senderIp = (cache) ? 
			    InetAddress.getByAddress(rawSenderAddress) : null;
			UWUtility.LogInfo("Calling ag.registerAgentIp; agentId = " + senderId +
					  ", location to register = " +
					  senderIp +
					  ", recipient Id = " + recvId);
			// Register sending agent id and address with recipient
			// agent if senderIp is null, this entry will be removed.
			ag.cacheAgentIp(senderId, senderIp);
		    }
		}

            } else if (strFuncName.equals("detectHost")) {
                // This function is called by an agent attempting to determing
                // whether a host is reachable, or behind a gateway. If the
                // host is unreachable, the socket connection times out in
                // UWUtility.InitUWPSocket.
                // Otherwise, we end up here. No further processing is
                // required; we can just clean up and return.

                // close the socket connection.
                in.close();
                skt.close();

            } else {
                UWUtility.Log("SocketThread: Unknown method name");
            }
        // uwP.notifyAgents( );

        } catch (java.net.BindException be) {
            UWUtility.Log("Bind exception in SocketThread");
        } catch (Exception e) {
            UWUtility.Log(e.toString());
            e.printStackTrace();
        }

        // This is the end of run( ), so that isAlive must be deactivated in
        // order to remove this thread from AcceptThread's SocketThr vector.
        synchronized (isAlive) {
            isAlive[0] = false;
        }

        // performance
        UWUtility.LogInfo("Elapsed = " +
                ((new Date()).getTime() - startTime.getTime()) +
                " fName = " + fName + " mName = " + mName +
                " dest = " + dest);
        // if ( msg != null ) msg.showRouteInfo( );

        UWUtility.LogExit();
    }

    /*
     * Converts a given byte array to an instance of UWMessage. It is called
     * from run( ) upon receiving coenqueueMessage and enqueueMessageGateway.
     *
     * @param bytMessage a message in byte[]
     * @return an instance of UWMessage
     */
    private UWMessage convertBytesToMessage(byte[] bytMessage)
            throws IOException, ClassNotFoundException {
        return convertBytesToMessage(bytMessage, null);
    }
    /*
     * Converts a given byte array to an instance of UWMessage. It is called
     * from run( ) upon receiving coenqueueMessage and enqueueMessageGateway.
     *
     * @param bytMessage a message in byte[]
     * @param ucl an agent-specific class loader
     * @return an instance of UWMessage
     */

    private UWMessage convertBytesToMessage(byte[] bytMessage,
            UWClassLoader ucl)
            throws IOException, ClassNotFoundException {

        ByteArrayInputStream bais = new ByteArrayInputStream(bytMessage);

        UWObjectInputStream uois = null;
        ObjectInputStream ois = null;

        if (ucl != null) {
            uois = new UWObjectInputStream(bais, ucl, null);
        } else {
            ois = new ObjectInputStream(bais);
        }

        Object uwmessObj = (uois != null) ? uois.readObject() : ois.readObject();

        // Cast it to the UWMessage
        UWMessage message = (UWMessage) uwmessObj;
        if (uois != null) {
            uois.close();
        } else {
            ois.close();
        }
        bais.close();

        return message;
    }

    /*
     * Returns an ack to the sender agent. It is called
     * from run( ) upon receiving coenqueueMessage and enqueueMessageGateway.
     *
     * @param message a message whose sender is now acknowledged.
     * @param ack     an acknowledgment in byte[] to send: 1 = true.
     */
    private void returnAck(UWMessage message, byte[] ack)
            throws IOException {
        // return an ack if it is not a system message
        if (!message.getIsSystemMessage()) {
            OutputStream out = skt.getOutputStream();
            out.write(ack);
            out.close();
        }
    }
}

/**
 * Is a thread assigned to each agent.
 */
class AgentThread implements Runnable {
    // Variables //////////////////////////////////////////////////////////////

    ThreadGroup group;             // the agent domain
    Thread thread;                 // the thread assigned to this agent
    private boolean m_isSuspended; // thread is suspended
    private boolean m_isRunning;   // thread is currently running (or is the
    // most recently started.)
    UWPlace uwP;                   // this agent's current working place
    UWAgent uwA;                   // this agent's data members
    private static int TerminationCheckInterval = 1000; // 1sec.

    // Constructor
    /**
     * Is the constructor that creates a new agent from a given data members,
     * assgins the current working place to it, and places it to the agent
     * domain specified with an argument "group".
     *
     * @param uwagent a class that includes this agent's live data
     * @param uwplace the current working place
     * @param group   this agent's domain
     */
    AgentThread(UWAgent uwagent, UWPlace uwplace, ThreadGroup group) {
        UWUtility.LogEnter();

        // associate this agent with the given agent domain "group".
        this.group = group;
        // Instantiate a thread.
        thread = new Thread(this.group, this, "agentThread");
        // Put it into the running state.
        m_isSuspended = false;
        m_isRunning = true;

        // Initialize
        uwP = uwplace;
        uwA = uwagent;
        uwA.setPlace(uwP);

        // At this point, uwA.activateMailbox( ); already called in
        // receiveAgent( )

        UWUtility.LogExit();

        // Start the thread
        thread.start();
    }

    // Put/get utilities //////////////////////////////////////////////////////
    /**
     * Checks if this agent is suspended.
     * @return true if it is suspended.
     */
    public boolean isSuspended() {
        return m_isSuspended;
    }

    /**
     * Checks if this agent is running.
     * @return true if it is running.
     */
    public boolean isRunning() {
        return m_isRunning;
    }

    /**
     * Indicates that this thread is the most recent one activated.
     * @param ir a thread that has been activated most recently.
     */
    public void setRunning(boolean ir) {
        m_isRunning = ir;
    }

    /**
     * Suspends this agent.
     */
    public void suspend() {
        thread.suspend();
        m_isSuspended = true;
    }

    /**
     * Resume this agent.
     */
    public void resume() {
        thread.resume();
        m_isSuspended = false;
    }

    // the life of each agent /////////////////////////////////////////////////
    /**
     * Is the life of each agent.
     */
    public void run() {
        UWUtility.LogEnter();

        try {
            int agentId = this.uwA.getAgentId();

            // sending childStarting message to the parent
            if (agentId > 0) { // root has no parent
                int parentId = this.uwA.getParentId(agentId);
                UWUtility.LogInfo(this.uwA.getName() + " " + agentId +
                        " starting; sending start message to " +
                        "parent (id " + parentId + ")");
                UWMessage message = new UWMessage(this.uwA, "childStarting");
                message.setIsSystemMessage(true);
                this.uwA.talk(parentId, message);
            }

            // set running state to true
            if (this.uwA.getName().equals("UWMonitorAgent")) {
                // Exclude monitor agent
                this.setRunning(false);
            } else {
                // Set running/ready status
                synchronized (uwP.agentsList) {
                    for (int i = 0; i < uwP.agentsList.size(); i++) {
                        AgentThread at = ((AgentThread) uwP.agentsList.get(i));
                        if (at == this) {
                            // I'm starting to run or I'm the most recently
                            // thread to run.
                            at.setRunning(true);
                        } else {
                            // This has been already started before.
                            at.setRunning(false);
                        }
                    }
                }
            }

            // now invoke a function specified in hop( ) or init( )
            String[] agFuncArgs = uwA.getFuncArgs();
            if (agFuncArgs == null) {
                // function call without arguments
                Method agMethod = uwA.getClass().getMethod(uwA.getNextFunc(),
                        (Class[]) null);
                agMethod.invoke(uwA, (Object[]) null);
            } else {
                // function call with arguments
                Class[] argClass = new Class[]{agFuncArgs.getClass()};
                Method agMethod = uwA.getClass().getMethod(uwA.getNextFunc(),
                        argClass);
                Object[] initArgs = {(Object) agFuncArgs};
                agMethod.invoke(uwA, initArgs);
            }

            // Postpone termination until children have terminated
            do {
                try {
                    Thread.sleep(TerminationCheckInterval);
                } catch (InterruptedException e) {
                }
            } while (this.uwA.getNumAliveChildren() > 0);

        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause().toString().equals("java.lang.ThreadDeath")) {
                System.err.println("Agent " + uwA.getAgentId() + " (" +
                        uwA.getName() + ") killed");
            } else {
                UWUtility.Log(e.toString());
                e.printStackTrace();
            }
        } catch (Exception e) {
            UWUtility.Log(e.toString());
            UWUtility.Log("agent: name=" + uwA.getName() + ", " +
                    "ip=" + uwA.getIp() + ", " +
                    "timestamp=" + uwA.getTimeStamp() + ", " +
                    "id=" + uwA.getAgentId());
            e.printStackTrace();
        }

        // send an exit message to the parent
        int agentId = this.uwA.getAgentId();
        int parentId = this.uwA.getParentId(agentId);
        UWUtility.Log(this.uwA.getName() + " " + agentId +
                " exiting; sending exit message to parent (id " +
                parentId + ")");
        UWMessage message = new UWMessage(this.uwA, "childExiting");
        message.setIsSystemMessage(true);
        this.uwA.talk(parentId, message);

        // Remove myself, (i.e., hash table and thread).
        uwP.popClassHashMap(uwA.getIp(), uwA.getTimeStamp(),
                uwA.getAgentId());
        uwP.popLoaderHashtable(uwA.getIp(), uwA.getTimeStamp(),
                uwA.getAgentId());
        synchronized (uwP.agentsList) {
            uwP.agentsList.remove(this);
        }

        UWUtility.Log("agentsList.size = " + uwP.agentsList.size());
        UWUtility.LogExit();

    }
}
