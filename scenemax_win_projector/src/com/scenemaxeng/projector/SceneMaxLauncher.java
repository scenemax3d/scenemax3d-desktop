package com.scenemaxeng.projector;

import com.scenemaxeng.common.types.*;
import org.apache.commons.io.FileUtils;
import javax.swing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import static java.lang.System.getProperty;

public class SceneMaxLauncher implements IAppObserver {

    private final String program;
    private MainWinApp mainApp;

    public static synchronized String getPlatform() {

        String platform = null;

        if (platform == null) {
            String model = getProperty("sun.arch.data.model");
            String os = getProperty("os.name").toLowerCase(Locale.ENGLISH);
            if (os.startsWith("windows")) {
                platform = "w";
            } else if (os.startsWith("mac") || os.startsWith("darwin")) {
                platform = "m";
            } else {
                if (!os.startsWith("linux")) {
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
        String os = getProperty("os.name", "").toLowerCase(Locale.ENGLISH);
        String arch = getProperty("os.arch", "").toLowerCase(Locale.ENGLISH);

        if (os.startsWith("windows")) {
            String[] libraries = arch.contains("64")
                    ? new String[]{"lwjgl64.dll", "OpenAL64.dll", "jinput-raw_64.dll", "jinput-dx8_64.dll", "native/windows/x86_64/bulletjme.dll"}
                    : new String[]{"lwjgl.dll", "OpenAL32.dll", "jinput-wintab.dll"};
            extractLibrariesOnce("lwjgl" + (arch.contains("64") ? "64.dll" : ".dll"), libraries);
            return;
        }

        if (os.startsWith("linux")) {
            String[] libraries = arch.contains("64")
                    ? new String[]{"liblwjgl64.so", "libopenal64.so", "libjinput-linux64.so", "native/linux/x86_64/libbulletjme.so"}
                    : new String[]{"liblwjgl.so", "libopenal.so"};
            extractLibrariesOnce(arch.contains("64") ? "liblwjgl64.so" : "liblwjgl.so", libraries);
            return;
        }

        if (os.startsWith("mac") || os.startsWith("darwin")) {
            String bulletLib = arch.contains("aarch64") || arch.contains("arm64")
                    ? "native/osx/arm64/libbulletjme.dylib"
                    : "native/osx/x86_64/libbulletjme.dylib";
            String[] libraries = new String[]{"liblwjgl.dylib", "openal.dylib", "libjinput-osx.jnilib", bulletLib};
            extractLibrariesOnce("liblwjgl.dylib", libraries);
        }
    }

    private void extractLibrariesOnce(String markerFileName, String[] libraries) {
        File marker = new File(flattenLibraryName(markerFileName));
        if (marker.exists()) {
            return;
        }

        for (String library : libraries) {
            extractFileFromJarToDisk(library);
        }
    }

    private String flattenLibraryName(String fileName) {
        return new File(fileName).getName();
    }

    private boolean extractFileFromJarToDisk(String fileName) {

        try {
            InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(fileName);

            if(is==null) {
                return false;
            }

            File outputFile = new File(flattenLibraryName(fileName));
            OutputStream outputStream = new FileOutputStream(outputFile);

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

        try {
            if(!f.exists()) {
                System.out.println("script file: "+f.getAbsolutePath()+" not found.");
                return null;
            }

            return FileUtils.readFileToString(f,StandardCharsets.UTF_8);
        } catch (SecurityException ex) {
            // Web Start sandboxes local file access unless the app is granted permissions.
            System.out.println("script file access denied for " + f.getPath() + ", falling back to bundled resource.");
            return null;
        } catch(Exception ex) {
            ex.printStackTrace();
        }
        return null;

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
