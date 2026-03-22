package com.scenemax.desktop;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

/**
 * A collection of jar utilities
 *
 * @author thomas.diesler@jboss.org
 * @version $Revision: 81011 $
 */
public class JarUtils {
    /**
     * Add jar contents to the deployment archive under the given prefix
     */
    public static String[] addJar(JarOutputStream outputStream, String prefix, File jar, Runnable progress) {

        try {
            ArrayList tmp = new ArrayList();
            FileInputStream fis = new FileInputStream(jar);
            JarInputStream jis = new JarInputStream(fis);
            JarEntry entry = jis.getNextJarEntry();
            while (entry != null) {

                if (progress != null) {
                    progress.run();
                }

                if (entry.isDirectory() == false) {
                    String entryName = prefix + entry.getName();
                    tmp.add(entryName);
                    addJarEntry(outputStream, entryName, jis);
                }
                entry = jis.getNextJarEntry();
            }
            jis.close();
            String[] names = new String[tmp.size()];
            tmp.toArray(names);
            return names;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Add a jar entry to the deployment archive
     */
    public static void addJarEntry(JarOutputStream outputStream, String entryName,
                                   InputStream inputStream) throws IOException {
        outputStream.putNextEntry(new JarEntry(entryName));
        copyStream(outputStream, inputStream);
    }

    /**
     * Copies the input stream to the output stream
     */
    public static void copyStream(OutputStream outputStream, InputStream inputStream)
            throws IOException {
        byte[] bytes = new byte[4096];
        int read = inputStream.read(bytes, 0, 4096);
        while (read > 0) {
            outputStream.write(bytes, 0, read);
            read = inputStream.read(bytes, 0, 4096);
        }
    }

}
