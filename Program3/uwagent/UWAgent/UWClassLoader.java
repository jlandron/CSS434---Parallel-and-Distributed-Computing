package UWAgent;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * UWPlace instantiates this class upon receiving a new agent so as to
 * find and load a class carried with this agent.
 *
 * @author    Koichi Kashiwagi (CSS, University of Washington, Bothell)
 * @since     10/01/04
 * @version   8/01/06
 */
public class UWClassLoader extends ClassLoader {

    InputStream in = null;
    HashMap<String, byte[]> byteHash = new HashMap<String, byte[]>();
    HashMap<String, Class> classHash = new HashMap<String, Class>();

    /**
     * Is used by UWPlace to find and load a class carried with an incoming 
     * agent. 
     *
     * @param cHash a hash table of class names and their corresponding
     *              bodies in a byte representation.
     * @param parent a parent classloader
     */
    public UWClassLoader(HashMap cHash, ClassLoader parent) {
        super(parent);
        final Set<String> packageNames = new TreeSet<String>();
        // all names must be in a dot format like aa.bb.cc.name
        for (Iterator i = cHash.keySet().iterator(); i.hasNext();) {
            String name = (String) i.next();
            String dotName = name.replace('/', '.');
            byteHash.put(dotName, (byte[]) cHash.get(name));
            if (dotName.startsWith(".")) {
                dotName = dotName.substring(1);
            }
            int index = dotName.lastIndexOf('.');
            if (index > -1) {
                final String packageName = dotName.substring(0, index);
                packageNames.add(packageName);
            }
        }

        for (String name : packageNames) {
            Package pkg = getPackage(name);
            if (pkg == null) {
                definePackage(name, name, name, name, name, name, name, null);
                UWUtility.LogInfo("UWClassLoader definePackage: " + name);
            }
        }

        UWUtility.LogInfo("UWClassLoader created: " + this);
    }

    @Override
    public URL getResource(String name) {
        URL result = null;
        String dotName = name.replace('/', '.');
        if (dotName.startsWith(".")) {
            dotName = dotName.substring(1);
        }
        if (byteHash.containsKey(dotName)) {
            BufferedOutputStream bos = null;
            try {
                ByteArrayInputStream input = new ByteArrayInputStream((byte[]) byteHash.get(dotName));
                File temp = File.createTempFile("temp", dotName);
                bos = new BufferedOutputStream(new FileOutputStream(temp));
                byte[] buff = new byte[4096];
                for (int n = input.read(buff); n > -1; n = input.read(buff)) {
                    bos.write(buff, 0, n);
                }
                input.close();
                bos.close();
                result = temp.toURL();
            } catch (IOException ex) {
                Logger.getLogger(UWClassLoader.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                try {
                    bos.close();
                } catch (IOException ex) {
                    Logger.getLogger(UWClassLoader.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        } else {
            result = getParent().getResource(name);
        }

        if (result == null) {
            result = super.getResource(name);
        }

        return result;
    }

    /**
     * Returns an input stream for reading the specified resource.
     *
     * @param  name
     *         The resource name
     * @return  An input stream for reading the resource, or <tt>null</tt>
     *          if the resource could not be found
     *
     */
    @Override
    public InputStream getResourceAsStream(String name) {
        InputStream resultIn = null;
        String dotName = name.replace('/', '.');
        if (dotName.startsWith(".")) {
            dotName = dotName.substring(1);
        }
        if (byteHash.containsKey(dotName)) {
            resultIn = new ByteArrayInputStream((byte[]) byteHash.get(dotName));
        } else {
            resultIn = getParent().getResourceAsStream(name);
        }

        if (resultIn == null) {
            resultIn = super.getResourceAsStream(name);
        }

        return resultIn;
    }

    /**
     * Retrieves the class specified with a given class name from a class
     * hash table carried with an incoming agent.
     * 
     * @param name a class name.
     * @return the class specified with name.
     */
    @Override
    public Class findClass(String name) throws ClassNotFoundException {

        // Load the corresponding class
        Class definedClass = null;
        if ((definedClass = (Class) classHash.get(name)) == null) {

            // name may be a nick name rather than aa.bb.cc.name
            for (Iterator i = classHash.keySet().iterator(); i.hasNext();) {
                String formalName = (String) i.next();
                if (formalName.endsWith(name)) {
                    name = formalName;  // found!
                    // load the class body with this formalName
                    definedClass = (Class) classHash.get(name);
                    break;
                }
            }
        }

        if (definedClass != null) { // class already defined.
            return definedClass;
        }

        // class not defined yet.
        // Load the corresponding class body in bytes.
        byte[] bytecode = null;
        if ((bytecode = (byte[]) byteHash.get(name)) == null) {

            // name may be a nick name rather than aa.bb.cc.name
            for (Iterator i = byteHash.keySet().iterator(); i.hasNext();) {
                String formalName = (String) i.next();

                if (formalName.endsWith(name)) {
                    name = formalName;  // found!

                    // load the class body with this formalName
                    bytecode = (byte[]) byteHash.get(name);

                    break;
                }
            }
        }

        // check if bytecode has been found
        if (bytecode == null) {
            UWUtility.LogInfo("UWClassLoader.findClass: " + name + " not found!");
            return null;
        }

        // copy this bytecode to data[] so as to register it as a class.
        ByteArrayInputStream bais = new ByteArrayInputStream(bytecode);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
            byte buff[] = new byte[1024];
            int n, m;
            int len = 0;
            while ((n = bais.read(buff, 0, 1024)) >= 0) {
                out.write(buff, 0, n);
                len += n;
            }

            byte data[] = new byte[len];
            data = out.toByteArray();

            // Make this byte representation a class, register it with a given
            // name, and return it.
            definedClass = defineClass(name, data, 0, len);
            classHash.put(name, definedClass);
            return definedClass;

        } catch (Throwable e) {
            UWUtility.Log("UWClassLoader.findClass: " + e);
            e.printStackTrace();
            throw new ClassNotFoundException();
        }
    }

    /**
     * Loads the class specified with a given name from memory first. If it
     * is not found in memory, loadClass( ) will try to find the class from
     * the hash table carried with an incoming agent.
     *
     * @param name a class name.
     * @return the class specified with name.
     */
    @Override
    public Class loadClass(String name) throws ClassNotFoundException {
        // a given name must be in a dot format
        name = name.replace('/', '.');

        // Load the class from memory first.
        Class c = findLoadedClass(name);

        if (c == null) {
            // Couldn't find it and thus ask the super loader to load it from
            // the disk

            try {
                if ((c = super.loadClass(name)) == null) {
                    c = getParent().loadClass(name);
                }
            } catch (ClassNotFoundException ignored) {
            }

            if (c == null) {
                // Couldn't find it from the super class and thus load it
                // from the current hash table

                try {
                    // Load the class from the current hash table.
                    c = findClass(name);
                } catch (Exception e) {
                    UWUtility.Log(e.toString());
                    UWUtility.Log("Cause: " + e.getCause());
                }
            }
        }
        return c;
    }

    /**
     * Loads the class specified with a given name from memory first. If it
     * is not found in memory, loadClass( ) will try to find the class from
     * the hash table carried with an incoming agent.
     *
     * @param name a class name.
     * @return the class specified with name.
     */
    public Class loadClass_new(String name) throws ClassNotFoundException {
        Class c = null;

        // Load the class from the current hash table
        try {
            c = findClass(name);
        } catch (Exception e) {
            UWUtility.Log(e.toString());
        }

        if (c == null) {
            // Load the class from memory first.
            c = findLoadedClass(name);

            if (c == null) {
                // Couldn't find it and thus ask the super loader to load it
                // from the disk
                c = super.loadClass(name);
            }

        }

        return c;
    }
}
