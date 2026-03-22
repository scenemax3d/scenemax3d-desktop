package com.scenemaxeng.projector;

import com.scenemaxeng.common.types.*;
import org.apache.commons.io.FileUtils;
import javax.swing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static java.lang.System.getProperty;

public class SceneMaxLauncher implements IAppObserver {

    private final String program;
    private MainWinApp mainApp;

    public static synchronized String getPlatform() {

        String platform = null;

        if (platform == null) {
            String model = getProperty("sun.arch.data.model");
            String os = getProperty("os.name").toLowerCase();
            if (os.startsWith("windows")) {
                platform = "w";
            } else {
                if (!os.equals("linux")) {
                    System.out.println(os);
                    throw new UnsupportedOperationException("Platform not supported " + os);
                }

                platform = "l";
            }

            platform = platform + model;
        }

        return platform;

    }

    public SceneMaxLauncher() {

        try {
            extractJniLibs();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // first, try loading main file from disk
        String prg = loadScript();

        // if main file was not found on disk, try loading from resources
        if(prg==null || prg.length()==0) {
            //InputStream script = SceneMaxLauncher.class.getClassLoader().getResourceAsStream("/running/main");
            InputStream script = SceneMaxLauncher.class.getResourceAsStream("/running/main");
            if(script!=null) {
                try {
                    prg = new String(Util.toByteArray(script));
                    System.out.println("Main script loaded: \r\n"+prg);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                System.out.println("SceneMaxLauncher.class.getResourceAsStream didn't load any script");
                prg="";
            }
        }

        this.program = prg;
        mainApp = new MainWinApp(null,prg,false);
        mainApp.parent=this;

    }

    private void extractJniLibs() {

        File f = new File("lwjgl.dll");
        if(!f.exists()) { // extract only once
            extractFileFromJarToDisk("lwjgl.dll");
            extractFileFromJarToDisk("lwjgl64.dll");
        }

    }

    private boolean extractFileFromJarToDisk(String fileName) {

        try {
            InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(fileName);

            if(is==null) {
                return false;
            }

            OutputStream outputStream = new FileOutputStream(new File(fileName));

            byte[] buffer = new byte[8 * 1024];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            is.close();
            outputStream.close();

            return true;

        } catch(Exception e) {

        }

        return false;

    }

    public static void main(String[] args) {
        new SceneMaxLauncher();
    }

    private static String loadScript() {

        File folder = new File("running");
        File f=new File(folder, "main");

        String program = "";
        if(!f.exists()) {
            System.out.println("script file: "+f.getAbsolutePath()+" not found.");
            return null;
        }

        try {
            program = FileUtils.readFileToString(f,StandardCharsets.UTF_8);

        } catch(Exception ex) {
            ex.printStackTrace();
        }

        return program;

    }

    @Override
    public void init() {

    }

    @Override
    public void showScriptEditor() {

    }

    @Override
    public void onEndCode(List<String> errors) {

    }

    @Override
    public void onStartCode() {

    }

    @Override
    public void message(final int msgType) {

        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    message(msgType);
                }
            });
            return;
        }

    }

    @Override
    public void message(int msgType, Object content) {

    }
}
