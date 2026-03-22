package com.scenemax.desktop;

//import com.scenemaxeng.projector.SceneMaxLauncher;

import javax.swing.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class PrepareLauncherTask extends SwingWorker<Integer, String> {

    private File scriptFolder=null;
    private String prg;
    private Runnable finish;

    @Override
    public void done() {
        finish.run();
    }

    @Override
    protected Integer doInBackground() {

        try {
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "com.scenemaxeng.projector.SceneMaxLauncher" );//SceneMaxLauncher.class.getName()
            JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream("launcher" + Util.getAppVersion() + ".jar"), manifest);

            //addClass(SceneMaxLauncher.class, jarOutputStream);
            //addClass(Util.class, jarOutputStream);

            String projectorJarPath = null;

            if (scriptFolder == null) {
                projectorJarPath = "./out/artifacts/scenemax_win_projector.jar";
            } else {
                String workingDir = Util.getWorkingDir();
                projectorJarPath = workingDir + "/out/artifacts/scenemax_win_projector.jar";
            }

            File projectorFile = new File(projectorJarPath);
            JarUtils.addJar(jarOutputStream, "", projectorFile, null);

            // Close jar
            jarOutputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return 0;
    }

    private void addSingleFile(File source, JarOutputStream target) throws IOException {

        BufferedInputStream in = null;
        try
        {
            JarEntry entry = new JarEntry(source.getName());

            entry.setTime(source.lastModified());
            target.putNextEntry(entry);
            in = new BufferedInputStream(new FileInputStream(source));

            byte[] buffer = new byte[1024];
            while (true)
            {
                int count = in.read(buffer);
                if (count == -1)
                    break;
                target.write(buffer, 0, count);
            }
            target.closeEntry();

        } catch(Exception e) {
            e.printStackTrace();
        }
        finally
        {
            if (in != null) {
                in.close();
            }
        }

    }


    private void addClass(Class c, JarOutputStream jarOutputStream) throws IOException
    {
        String path = c.getName().replace('.', '/') + ".class";
        jarOutputStream.putNextEntry(new JarEntry(path));
        jarOutputStream.write(Util.toByteArray(c.getClassLoader().getResourceAsStream(path)));
        jarOutputStream.closeEntry();
    }

    public PrepareLauncherTask(File scriptFolder, String prg, Runnable finish) {
        this.prg=prg;
        this.finish=finish;
        this.scriptFolder = scriptFolder;
    }

}
