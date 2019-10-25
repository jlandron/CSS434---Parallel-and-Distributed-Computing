package UWAgent;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/*
 * JarExtraction is called from UWInject to extract all classes from a jar
 * file specified with -j option.
 * @author    Munehiro Fukuda (CSS, University of Washington, Bothell)
 * @since     8/1/04
 * @version   7/31/06
 */

class JarExtraction {

    private JarFile jar = null;        // a jar file specified with -j option
    private Enumeration items = null;  // all jar entries
    private String fileName;           // the name of the current class file
    private InputStream input;     // an input stream to read the current class

    /**
     * Extracts files from a given jarFile.
     *
     * @param jarFile the JAR file name.
     */
    public JarExtraction(String jarFile) {

        UWUtility.LogInfo("JarExtraction.constructor: extracts " + jarFile);
        try {
            jar = new JarFile(jarFile);
        } catch (IOException e) {
            System.err.println("jarExtraction.constructor: " + e);
        }
        // items includes all Jar file entries
        items = jar.entries();
    }

    /*
     * Locates the next file in a given JAR file.
     * @return true if a new file is found; otherwise false.
     */
    public boolean nextClass() {

        // find the very first entry that include a class file
        while (items.hasMoreElements()) {
            JarEntry jarEnt = (JarEntry) items.nextElement();
            UWUtility.LogInfo("JarExtraction.nextClass: name = " +
                    jarEnt.getName());

            // check if this is a class file
            // if ( ( fileName = jarEnt.getName( ) ).endsWith( ".class" ) ) {
            if (!(fileName = jarEnt.getName()).endsWith("/")) {
                try {
                    // retrieve a file
                    input = jar.getInputStream(jarEnt);
                } catch (IOException e) {
                    System.err.println("jarExtraction.nextClass:" + e);
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    /*
     * Gets an InputStream object related to the current Jar file entry that
     * includes a class.
     * @return an input stream to read the current class file.
     */
    public InputStream getInputStream() {
        return input;
    }

    /*
     * Get a file name (.class) of the current Jar file entry
     * @return a file name.
     */
    public String getFileName() {
        return fileName;
    }

    /*
     * Get a class name of the current Jar file entry
     * @return a class name.
     */
    public String getClassName() {
        String[] subStrings = fileName.split(".class");
        return subStrings[0];
    }

    /*
     * Get a byte array from the current Jar file entry.
     * @return an byte array of the current class file.
     */
    public byte[] getByteArray() {
        byte byteArrayClass[] = null;
        try {
            // allocate an array to receive a class content
            byteArrayClass = new byte[input.available()];
            BufferedInputStream bis = new BufferedInputStream(input);

            // read bytes
            bis.read(byteArrayClass, 0, byteArrayClass.length);
            bis.close();
            UWUtility.LogInfo("JarExtraction.geteByteArray: file = " +
                    fileName +
                    ", length = " + byteArrayClass.length);
        } catch (Exception e) {
            UWUtility.Log("JarExtraction.getByteArray: " + e);
            e.printStackTrace();
        }
        // return the array
        return byteArrayClass;
    }
}
