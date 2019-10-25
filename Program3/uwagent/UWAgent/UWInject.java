package UWAgent;

import java.net.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.List;
import java.util.Map;
import java.util.Date;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.HashMap;

/**
 *
 * Class Name: UWInject
 * Purpose: Inject a UWAgent to a specified UWPlace.
 * Used by: UWInject
 *
 * @author   Koichi Kashiwagi, Munehiro Fukuda, and Duncan Smith (CSS)
 * @since    10/1/04
 * @version  7/31/06
 */
public class UWInject extends UWPlace {
    // ********** Public

    /**
     * the classpath to locate an agent to be injected.
     */
    public String agentPath = ".";
    // ********** Private
    // Client name (can be set with -c argument)
    private String clientName = "Default";
    // Max number of children a parent is allowed to spawn (see -m)
    private String maxChildren = "10";	// default to a maximum of 10 children
    // List of class names (see -s argument)
    private List<String> classNames = new ArrayList<String>();
    // List of jar files whose classes are carried/used by the agent (see -j)
    private List<String> jarNames = new ArrayList<String>();

    /**
     * Is the constructor that calls the UWPlace super class.
     */
    public UWInject() {
        super();	// Call UWPlace constructor
    }

    /**
     * Method Name: Main
     */
    public static void main(String[] args) {
        try {
            // Validate arguments/print usage
            if (args.length < 2) {
                displayUsage();
                System.exit(1);
            }

            // Instantiate UWInject Class
            UWInject inject = new UWInject();

            // Set environments with options and extract only arguments to
            // be passed to an agent
            String[] argsWithoutOption = inject.setCommandArgsUWInject(args);

            // Set arguments necessary to launch a new agent
            String destHostname = argsWithoutOption[0];
            String agentName = argsWithoutOption[1];
            String[] agentArgs = null;
            if (argsWithoutOption.length > 2) {
                agentArgs = new String[argsWithoutOption.length - 2];
                for (int i = 0; i < agentArgs.length; i++) {
                    agentArgs[i] = argsWithoutOption[i + 2];
                }
            }

            // Set arguments necessary for engine( )
            String[] injectArgs = new String[4];
            injectArgs[0] = destHostname;
            injectArgs[1] = agentName;
            injectArgs[2] = inject.clientName;
            injectArgs[3] = inject.maxChildren;

            // Now call engine( ) to instantiate a new agent
            inject.classNames.add(agentName);
            inject.engine(injectArgs, agentArgs, inject);

            UWUtility.LogInfo("End of UWInject (main)");
            System.exit(0);
        } catch (Exception e) {
            UWUtility.Log(e.toString());
            UWUtility.Log("Cause: " + e.getCause());
        }
    }

    /**
     * Method Name: engine
     * Purpose:
     * Prepare the UWAgent for injection based on the specified inject args.
     * Instantiate the agent with the specified agent args.
     * This engine method is intended for use in cases where a UWAgent has not
     * yet been constructed, such as from command line UWInject arguments.
     *
     * Used by: UWInject.main( )
     * @param injectArgs   includes destination, agent name, and client name.
     * @param agentArgs    a list of arguments necessary to inject an agent.
     * @param i            an instance of UWInject.
     */
    public void engine(String[] injectArgs, String[] agentArgs, UWInject i) {
        UWUtility.LogInfo("[UWInject.engine] agentPath = " + agentPath);

        String destHostname = injectArgs[0];
        String agentName = injectArgs[1];
        String clientUserName = injectArgs[2];
        Object agentObj = new Object();
        try {
            // Get the agent class from its String name.
            // Locate the base directory that includes the agent class
            File baseDir = (new File(agentPath)).getCanonicalFile();
            UWUtility.LogInfo("File : " + baseDir.getName());
            URL baseURL = baseDir.toURL();
            UWUtility.LogInfo("URL : " + baseURL.toString());

            // The agent class may be included in a jar file.
            URL jarURL = null;
            if (jarNames.size() > 0) {
                File jarFile = (new File(agentPath + "/" +
                        (String) jarNames.get(0))).getCanonicalFile();
                UWUtility.LogInfo("File : " + jarFile);
                jarURL = jarFile.toURL();
            }


            // Create an URL that point to either the base directory or jar.
            UWUtility.LogInfo("URL : " + jarURL);
            URLClassLoader loader = new URLClassLoader((jarURL == null) ? new URL[]{baseURL} : new URL[]{baseURL, jarURL});
            Class agentClass = loader.loadClass(agentName);

            // If there are no arguments provided for the construction
            // of the injected agent, then simply create the new instance.
            if (agentArgs == null) {
                agentObj = agentClass.newInstance();
            } else {
                // Otherwise, pass the additional arguments to the constructor
                // of the injected agent.
                Object[] constructorArgs = new Object[]{agentArgs};

                // Locate this agent's constructor and instantiate the agent
                Constructor agentConst = agentClass.getConstructor(new Class[]{String[].class});
                agentObj = agentConst.newInstance(constructorArgs);
            }
        } catch (ClassNotFoundException e) {
            UWUtility.Log(e.toString());
        } catch (NoSuchMethodException e) {
            UWUtility.Log(e.toString());
        } catch (InstantiationException e) {
            UWUtility.Log(e.toString());
        } catch (IllegalAccessException e) {
            UWUtility.Log(e.toString());
        } catch (InvocationTargetException e) {
            UWUtility.Log(e.toString());
        } catch (Exception e) {
            UWUtility.Log(e.toString());
        }

        // These agent properties must be initialized before the
        // actual UWInject engine method.
        // After being injected, the new UWPlace should invoke
        // the UWAgent's "init" method.
        ((UWAgent) agentObj).setNextFunc("init");
        ((UWAgent) agentObj).setClientName(clientUserName);
        ((UWAgent) agentObj).setMaxChildren(maxChildren);
        ((UWAgent) agentObj).setName(agentName);
        ((UWAgent) agentObj).setAgentId(0);
        ((UWAgent) agentObj).setPackagePath(agentPath);

        engine(((UWAgent) agentObj), destHostname, i);
    }

    /**
     * Method Name: engine
     * Purpose:
     * 		Inject the specified agent to the specified destination host.
     * 		This engine method is intended for use in cases where a UWAgent
     *	 	has already been constructed.
     * Used by: UWInject.main( ).
     * Used by: engine(String [] injectArgs, String [] agentArgs, UWInject i).
     * 
     * @param agentObj     an instance of a new agent to be injected
     * @param destHostname a destination ip name
     * @param i            an instance of UWInject
     * 
     */
    public void engine(UWAgent agentObj, String destHostname, UWInject i) {
        // Finish initialization of UWAgent that reflects the injection.
        // Upon being injected to the UWPlace, the UWAgent should not
        // do anything until being initialized ( init( ) ).
        ((UWAgent) agentObj).setPlace(i);
        ((UWAgent) agentObj).setTimeStamp(new Date().getTime());

        try {
            ((UWAgent) agentObj).setIp(InetAddress.getLocalHost());
        } catch (Exception e) {
            UWUtility.Log(e.toString());
        }

        UWUtility.LogInfo("ip = " + ((UWAgent) agentObj).getIp() +
                ", time = " +
                ((UWAgent) agentObj).getTimeStamp() +
                ", ID = " + ((UWAgent) agentObj).getAgentId());

        // call UWIject's senAgent( ) function (see below).
        i.sendAgent(((UWAgent) agentObj), destHostname);
    }

    /**
     * Method Name: sendAgent
     * Purpose:
     * The UWInject sendAgent method reads the class file and
     * stores its byte representation in byteArrayData, which is later
     * used by remote processes to reconstruct UWAgent objects.
     *
     * This method overrides UWPlace.sendAgent().
     * Used by: UWInject.engine().
     *
     * @param ag a reference to a new agent to be injected
     * @param hostName a destination host ip name
     * @return true if a given agent has been sent in success
     */
    public boolean sendAgent(UWAgent ag, String hostName) {
        try {
            // Instantiate a hashtable to manitain all classes carried with
            // this agent
            HashMap<String, byte[]> classesHash = new HashMap<String, byte[]>();

            // Store all jar classes into a class hashtable
            for (int i = 0; i < jarNames.size(); i++) {
                String jarName = (String) jarNames.get(i);

                UWUtility.LogInfo("UWInject.sendAgent: jarName = " +
                        jarName);

                JarExtraction jarExt = new JarExtraction(jarName);
                while (jarExt.nextClass()) {
                    byte byteArrayClass[] = jarExt.getByteArray();
                    classesHash.put(jarExt.getClassName(), byteArrayClass);

                    UWUtility.LogInfo("UWInject.sendAgent: classname = " +
                            jarExt.getClassName());
                }
            }

            // Check all class names specified with -s. Read the corresponding
            // class file from the local directory if a class name has not been
            // registered in classHash.
            for (int i = 0; i < classNames.size(); i++) {
                // Agents and classes may be packaged as
                // something.something.agent1 or something.something.class1.
                // We need to convert them as
                //     classNameSlash = something/something/agent1
                //     classNameBase  = agent1
                String className = (String) classNames.get(i);
                String classNameSlash = className.replace('.', '/');
                int index = className.lastIndexOf('.');
                // -1 is returned when not found and substring(0) will
                // return the original string.
                String classNameBase = className.substring(index + 1);

                if (classesHash.get(classNameSlash) == null &&
                        classesHash.get(classNameBase) == null) {
                    // read the class file from agentPath (directory or
                    // packaged jar file)
                    byte byteArrayClass[] = UWUtility.makeByteArrayFromFile(agentPath,
                            className);
                    // register it.
                    if (byteArrayClass != null) {
                        classesHash.put(className, byteArrayClass);
                    } else {
                        UWUtility.Log("UWInject.sendAgent: class = " +
                                className + " couldn't be found in " +
                                agentPath);
                        return false;
                    }
                }

                UWUtility.LogInfo("UWInject.sendAgent: class = " +
                        ((classesHash.get(classNameSlash) != null) ? (classNameSlash + ", length = " +
                        classesHash.get(classNameSlash).length) : (classNameBase + ", length = " +
                        classesHash.get(classNameBase).length)));
            }

            if (classesHash.isEmpty()) {
                UWUtility.Log("Error: no classes");
                return false;
            }

            // Call UWPlace sendAgent
            startUWPSocket(null);
            return sendAgent(ag, classesHash, hostName);
        } catch (Exception e) {
            UWUtility.Log(e.toString());
            return false;
        }
    }

    /**
     * set to List className
     *
     * @param classNameList List of class names that are set to
     * UWInject.className
     */
    public void setClassNames(List classNameList) {
        for (int i = 0; i < classNameList.size(); i++) {
            String className = (String) classNameList.get(i);
            if (!classNames.contains(className)) {
                classNames.add(className);
            }
        }
    }

    /**
     * This function analyzes commandline arguments.
     * Each option is indicated with "-". It sets up all environments with 
     * givenoptions. The function then extracts only arguments to be passed to
     * an injected agent.
     *
     * @param commandArgs arguments to be analyzed by
     *                    CommandLineOptionAnalyzer.class
     * @return arguments passed to an injected agent.
     */
    public String[] setCommandArgsUWInject(String[] commandArgs) {
        // Instantiate a command option analyzer.
        CommandLineOptionAnalyzer analyser =
                new CommandLineOptionAnalyzer(commandArgs);

        Map map = analyser.getAnalyzedMap();

        // Set UWPlace name
        if (map.containsKey("-n")) {
            setPlaceName((String) map.get("-n"));
        }

        // Set port number
        if (map.containsKey("-p")) {
            setPortNumber((String) map.get("-p"));
        }

        // Set path of agent that loaded by UWInject
        if (map.containsKey("-u")) {
            agentPath = (String) map.get("-u");
        }

        // Set client name of agent loaded
        if (map.containsKey("-c")) {
            clientName = (String) map.get("-c");
        }

        // Set subclasses that is used by the agent
        if (map.containsKey("-s")) {
            String classNamesString = (String) map.get("-s");
            StringTokenizer token = new StringTokenizer(classNamesString, ",");
            while (token.hasMoreElements()) {
                classNames.add((String) token.nextElement());
            }
        }

        // Set jar file names whose classes are carried/used by the agent
        if (map.containsKey("-j")) {
            String jarNameString = (String) map.get("-j");
            StringTokenizer token = new StringTokenizer(jarNameString, ",");
            while (token.hasMoreElements()) {
                jarNames.add((String) token.nextElement());
            }
        }

        // Set maximum number of children allowed
        if (map.containsKey("-m")) {
            maxChildren = (String) map.get("-m");
        }

        // Set SSL support
        if (map.containsKey("-e")) {
            setIsSSL(true);
        }

        // Show usage of UWInject
        if (map.containsKey("-h")) {
            displayUsage();
            System.exit(0);
        }

        // Extract only arguments to be passed to an agent
        List list = (List) map.get("arguments");
        int size = list.size();
        String[] newArgs = new String[size];
        for (int count = 0; count < size; count++) {
            newArgs[count] = (String) list.get(count);
        }

        return newArgs;
    }

    /**
     * display the usage.
     */
    private static void displayUsage() {
        System.err.println("Usage: $ java UWInject " +
                "[-n placeName] [-p portNumber]" +
                "[-u path] [-s className1,className2,..]" +
                "[-j jarName1,jarName2,..]" +
                "[-c client name] " +
                "[-m maxChildren] " +
                "[-e] " +
                "destHostName " +
                "agentName [agentArgs[0] agentArgs[1] ...]");
    }
}
