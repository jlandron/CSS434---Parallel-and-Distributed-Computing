package UWAgent;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.ByteBuffer;

/**
 * This is a utility class for the UWAgents engine. It includes:
 * <ol>
 * <li> byte to int conversion
 * <li> bytes retrieval from class files
 * <li> logging utilities
 * </ol>
 *
 * @author     Duncan Smith (CSS University of Washington, Bothell)
 * @author     Munehiro Fukuda
 * @since      6/20/05
 * @version    5/17/07
 */

// Utility class for the UWAgents engine
public class UWUtility {
    // Constants //////////////////////////////////////////////////////////////
    // Sizes (in bytes); used for declaring byte arrays for agent navigation
    // and communication

    /**
     * Total header size
     */
    public static final int HEADER_SIZE = 40;	 // total header size
    /**
     * One integer size
     */
    public static final int INT_SIZE = 4;	 // one integer
    /**
     * Maximum length of a function name
     */
    public static final int FUNC_NAME_SIZE = HEADER_SIZE - INT_SIZE * 3;            // max function name length
    /**
     * Message type 
     */
    public static final int MSG_TYPE_FUNC = 1;	 // message type = function
    /**
     * Host IP name (255bytes)
     */
    public static final int HOSTNAME_SIZE = 255; // host ip name (255bytes)

    /*
     * Used if not specified
     */
    public static final String DEFAULT_PORT = "65432"; // used if not specified

    // Utilities: byte conversion, class file retrieval, and ip retrieval /////
    /**
     * Returns the integer represented by a 4-byte array 
     * (using the default byte order, BIG_ENDIAN).
     *
     * @param bytes an array of four bytes
     * @return an integer represented by given bytes
     */
    public static int BytesToInt(byte[] bytes) {
        ByteBuffer byteBuff = ByteBuffer.allocate(INT_SIZE);
        byteBuff.put(bytes);
        byteBuff.rewind();
        return byteBuff.getInt();
    }

    /**
     * Creates byte representation of a given class (agent) name from class 
     * file.
     *
     * @param path a path where a file is located. (absolute or relative)
     * @param className class (or agent) name that is transformed into byte 
     *                  representation
     * @return a byte array that includes a given (agent) class.
     */
    public static byte[] makeByteArrayFromFile(String path,
            String className) {
        UWUtility.LogEnter();
        byte byteArrayClass[] = null;
        String fileName = null;
        try {
            // Compose a file name of agentPath and className
            if (path.equals("")) {             // no path
                fileName = className + ".class";
            } else if (path.startsWith("/")) { // absolute path
                fileName = path +
                        ((path.endsWith("/")) ? "" : "/") +
                        className + ".class";
            } else {                               // relative path
                fileName = "./" + path +
                        ((path.endsWith("/")) ? "" : "/") +
                        className + ".class";
            }

            // Open a file to read its contents
            File file = new File(fileName);
            byteArrayClass = new byte[(int) file.length()];
            BufferedInputStream bis =
                    new BufferedInputStream(new FileInputStream(fileName));
            bis.read(byteArrayClass, 0, byteArrayClass.length);
            bis.close();

        } catch (FileNotFoundException fnfe) {
            Log("UWUtility.makeByteArrayFromFile: " + fnfe + " " +
                    fileName + " not found. return NULL");
            fnfe.printStackTrace();
            byteArrayClass = null;

        } catch (Exception e) {
            Log(e.toString());
            e.printStackTrace();
        }
        UWUtility.LogEnter();

        return byteArrayClass; // return the class contents in bytes
    }

    // Debugging utilities ///////////////////////////////////////////////////
    // Set current debug level (least to most verbose)
    // 0: No debug messages
    // 1: Exception messages (default)
    // 2: + informational messages
    // 3: + method entry/exit messages
    /**
     * 1: Exception messages (default)
     */
    public static final int DEBUG_EXCEPTION = 1;
    /**
     * 2: Exception messages (default) + informational messages
     */
    public static final int DEBUG_INFO = 2;
    /**
     * 3:  Exception messages (default) + informational messages 
     *     + method entry/exit messages
     */
    public static final int DEBUG_METHOD = 3;

    // *** Change this value to set the current debug level ****
    /**
     * Change this value to set the current debug level
     */
    private static final int CURRENT_DEBUG_LEVEL = DEBUG_EXCEPTION;
    // Enter/exit a method
    /**
     * No method to print out for debugging
     */
    public static final int DEBUG_NOMETHOD = 0;
    /**
     * Enter a method
     */
    public static final int DEBUG_ENTER = 1;
    /**
     * Exit from a method
     */
    public static final int DEBUG_EXIT = 2;
    // indentation of log messages
    /**
     * indentation of log messages
     */
    private static int indentLevel = 0;

    /**
     * Log entry to a method (with a message to print out)
     * @param message a message to be printed out together
     */
    public static void LogEnter(String message) {
        String[] parsedResults = new String[3];

        getCallingMethodName("UWUtility.LogEnter", parsedResults);
        Log(parsedResults[1], parsedResults[2], message, DEBUG_ENTER,
                DEBUG_METHOD);
    }

    /**
     * Log entry to a method (without a message)
     */
    public static void LogEnter() {
        String[] parsedResults = new String[3];

        getCallingMethodName("UWUtility.LogEnter", parsedResults);
        Log(parsedResults[1], parsedResults[2], "", DEBUG_ENTER,
                DEBUG_METHOD);
    }

    /**
     * Log exit from a method (with a message to print out)
     * @param message a message to be printed out together
     */
    public static void LogExit(String message) {
        String[] parsedResults = new String[3];

        getCallingMethodName("UWUtility.LogExit", parsedResults);
        Log(parsedResults[1], parsedResults[2], message, DEBUG_EXIT,
                DEBUG_METHOD);
    }

    /**
     * Log exit from a method (without a message)
     */
    public static void LogExit() {
        String[] parsedResults = new String[3];

        getCallingMethodName("UWUtility.LogExit", parsedResults);
        Log(parsedResults[1], parsedResults[2], "", DEBUG_EXIT,
                DEBUG_METHOD);
    }

    /**
     * Log info from a method (with a message to print out)
     * @param message a message to be printed out together
     */
    public static void LogInfo(String message) {
        String[] parsedResults = new String[3];

        getCallingMethodName("UWUtility.LogInfo", parsedResults);
        Log(parsedResults[1], parsedResults[2], message, DEBUG_NOMETHOD,
                DEBUG_INFO);
    }

    /**
     * Prints out log information regarding the current function with a given
     * message. The defult logging is for exceptions.
     *
     * @param message a message printed out together
     */
    public static void Log(String message) {
        String[] parsedResults = new String[3];

        getCallingMethodName("UWUtility.Log", parsedResults);
        Log(parsedResults[1], parsedResults[2], message,
                DEBUG_NOMETHOD, DEBUG_EXCEPTION);
    }

    /**
     * Prints out log information regarding the current function as well as a 
     * given message with respect to function call, exit, and other events.
     * The details of the log information is switched by a given debug level.
     *
     * @param message a message printed out together
     * @param methodCall flag indicating function call, exit, and the others.
     * @param debugLevel flag idicating how precisely logs should be printed. 
     */
    public static void Log(String message, int methodCall, int debugLevel) {
        String[] parsedResults = new String[3];

        getCallingMethodName("UWUtility.Log", parsedResults);
        Log(parsedResults[1], parsedResults[2], message, methodCall,
                debugLevel);
    }

    /**
     * Prints out log information regarding the current function specified
     * with a given class name and method name. An additional message is also
     * printed out.
     *
     * @param className class name that includes this method
     * @param methodName the name of the method to be logged
     * @param message a message printed out together
     */
    public static void Log(String className, String methodName,
            String message) {
        Log(className, methodName, message, DEBUG_NOMETHOD, DEBUG_EXCEPTION);
    }

    /**
     * Prints out log information including class, method, and messages.
     *
     * @param className class name that includes this method
     * @param methodName the name of the method to be logged
     * @param message the message printed out together
     * @param methodCall flag indicating function call, exit, and the others.
     */
    public static void Log(String className, String methodName,
            String message, int methodCall) {
        if (methodCall > 0) {
            // Default to debug level 3 for method call messages
            Log(className, methodName, message, methodCall, DEBUG_METHOD);
        } else {
            // Otherwise, level 2
            Log(className, methodName, message, methodCall, DEBUG_INFO);
        }
    }

    /**
     * Prints out log information including class, method, and messages.
     *
     * @param className class name that includes this method
     * @param methodName the name of the method to be logged
     * @param message the message printed out together
     * @param methodCall flag indicating function call, exit, and the others.
     * @param debugLevel flag idicating how precisely logs should be printed. 
     */
    public static void Log(String className, String methodName,
            String message, int methodCall, int debugLevel) {
        try {
            if (methodCall == DEBUG_EXIT) {
                indentLevel--;
            }
            StringBuffer tabs2 = new StringBuffer();
            for (int i = 0; i < indentLevel; i++) {
                tabs2.append(" ");
            }

            if (debugLevel <= CURRENT_DEBUG_LEVEL) {
                if (methodCall > 0) {
                    String message2 = (methodCall == DEBUG_ENTER ? "(enter)" : "(exit)");
                    System.err.println(tabs2 + className + "#" +
                            methodName + ": " + message2);
                }

                if (methodCall == DEBUG_ENTER) {
                    indentLevel++;
                }

                StringBuffer tabs = new StringBuffer();
                for (int i = 0; i < indentLevel; i++) {
                    tabs.append(" ");
                }

                if (!message.equals("")) {
                    System.err.println(tabs + className + "#" +
                            methodName + ": " + message);
                }
            }
        } catch (Exception e) {
            System.err.println("Exception inside Log method");
            System.err.println(e.toString());
        }
    }

    /**
     * Returns the name of the method that called this method
     * Adapted from code at http://www.buzzsurf.com/java/util.html
     *
     * @param calledMethod a string of the form ClassName.MethodName
     * @param parsedResults an array of 3 Strings: [0] = class.method name,
    [1] = class name, and [2] = metod name
     * @return String the name of the method that called calledMethod
     */
    public static String getCallingMethodName(String calledMethod,
            String parsedResults[]) {
        // calledMethod must be a string of the form ClassName.MethodName.
        // We will return the name of the method that called it.
        // For example, if calledMethod = "Logger.Log", we
        // return the name of the method that called Logger.Log.

        // Exceptions are an easy way to get call stack information.
        // This method takes advantage of that.
        Throwable t = new Throwable();

        // Print the stack trace to a byte array
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        t.printStackTrace(new PrintWriter(baos, true));
        byte[] stackTraceBytes = baos.toByteArray();

        final int ATPOS = 4; // position of string "at " in stack trace line
        String line = null;

        // Now read from that byte array, line by line
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(stackTraceBytes);
            BufferedReader in = new BufferedReader(new InputStreamReader(bais));

            // Skip lines until we get to the current method
            int parenPos = 0;
            do {
                // Start with the whole line
                line = in.readLine();
                // className.methodName is located between "at " and the
                // first left paren
                parenPos = line.indexOf('(');
                if (parenPos > 0) {
                    line = line.substring(ATPOS, parenPos);
                }
            } while (!line.equals(calledMethod));
            // The next line will contain the method that called calledMethod
            line = in.readLine();
            line = line.substring(ATPOS, line.indexOf('('));
            in.close();

            parsedResults[0] = line;
            // class name
            parsedResults[1] = line.substring(0, line.indexOf('.'));
            // method name
            parsedResults[2] = line.substring(parsedResults[1].length() + 1);
        } catch (NullPointerException e) {
            // will occur if calledMethod is not found
            parsedResults[0] = parsedResults[1] = parsedResults[2] = "";
            return "";
        } catch (StringIndexOutOfBoundsException e) {
            // parse problem
            parsedResults[0] = parsedResults[1] = parsedResults[2] = "";
            return "";
        } catch (IOException e) {
            parsedResults[0] = parsedResults[1] = parsedResults[2] = "";
            return "";
        }

        return line;
    }

    /**
     * Returns the name of the method that called this method.
     *
     * @param calledMethod a string of the form ClassName.MethodName
     * @return String the name of the method that called calledMethod
     */
    public static String getCallingMethodName(String calledMethod) {
        String[] parsedResults = new String[3];

        return getCallingMethodName(calledMethod, parsedResults);
    }
}
