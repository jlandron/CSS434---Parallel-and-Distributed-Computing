package UWAgent;

/**
 * Is an object input stream used for deserializing bytes into an agent.
 *
 * @author Duncan Smith (CSS, UWBothell)
 * @since  9/20/05
 * @version 5/22/07
 */
import java.io.*;
import java.util.List;
import java.util.ArrayList;

public class UWObjectInputStream extends ObjectInputStream {
    // Variables //////////////////////////////////////////////////////////////

    private ClassLoader cl;                     // UWClassLoader
    private List classNames = new ArrayList(); // Classes carried with agent

    /**
     * Is the constructor that initializes a givne input stream with 
     * the UWClassLoader and all classes carried with an agent.
     * @param im an input stream to initialize
     * @param cl the UWClassLoader
     * @param classNames the names of all classes carried with an agent
     */
    public UWObjectInputStream(InputStream im, ClassLoader cl, List classNames) throws IOException {
        super(im);
        this.cl = cl;
        this.classNames = classNames;
    }

    /**
     * Is automatically called when reading and deserializing a new agent.
     * @param v
     */
    protected Class resolveClass(ObjectStreamClass v) throws IOException {
        String className = v.getName();     // a class name found in a stream

        String tmpClassName = null;

        if (className.startsWith("L") && className.endsWith(";")) {
            tmpClassName = className;
            int subLen = className.length() - 1;
            // remove this garbage
            tmpClassName = className.substring(1, subLen);
        }

        /*
        if ( classNames.contains( className ) ) {
         */
        // the given className found in the collection of classes
        // carried with the agent.
        try {
            if (tmpClassName != null) {
                // if "[L" found
                // UWClassLoader must be called with the "[L" + name
                className = tmpClassName;
            }

            // load the corresponding class.
            Class cla = cl.loadClass(className);
            UWUtility.LogInfo("UWObjectInputStream.resolveClass[" +
                    Thread.currentThread().toString() +
                    "]: name = " +
                    className + ", class = " + cla);
            if (cla != null) {
                return cla;
            }
        } catch (Exception e) {
            UWUtility.Log("UWObjectInputStream.resolveClass @ loadClass: " + e.toString());
            e.printStackTrace();
        }
        /*
        } else {
         */
        try {
            // try the super class loader for \[java.lang.*;
            return super.resolveClass(v);
        } catch (ClassNotFoundException e) {
            UWUtility.Log("UWObjectInputStream.resolveClass @ super." + "resolveClass: " + e.toString());
            e.printStackTrace();
        }
        /*
        }
         */
        return null;
    }
}
