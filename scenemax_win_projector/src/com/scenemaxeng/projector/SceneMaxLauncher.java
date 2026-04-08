package com.scenemaxeng.projector;

import com.scenemaxeng.common.types.*;
import org.apache.commons.io.FileUtils;
import javax.swing.*;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static java.lang.System.getProperty;

public class SceneMaxLauncher implements IAppObserver {

    private final String program;
    private MainWinApp mainApp;
    private File sourceScriptFile;

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
            extractBundledRunningFiles();
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
        prg = extractSourceScriptPath(prg);
        mainApp = new MainWinApp(sourceScriptFile,prg,false);
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

    private void extractBundledRunningFiles() {
        File runningDir = new File("running");
        File marker = new File(runningDir, ".scenemax-extracted");
        if (marker.exists()) {
            return;
        }

        try {
            File jarFile = resolveOwningJarFile();
            if (jarFile == null || !jarFile.isFile()) {
                return;
            }

            boolean extractedAny = false;
            try (JarFile jar = new JarFile(jarFile)) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!name.startsWith("running/") || entry.isDirectory()) {
                        continue;
                    }

                    File output = new File(name.replace("/", File.separator));
                    File parent = output.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }

                    try (InputStream in = jar.getInputStream(entry)) {
                        Files.copy(in, output.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                    extractedAny = true;
                }
            }

            if (extractedAny) {
                if (!runningDir.exists()) {
                    runningDir.mkdirs();
                }
                if (!marker.exists()) {
                    marker.createNewFile();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private File resolveOwningJarFile() throws URISyntaxException {
        if (SceneMaxLauncher.class.getProtectionDomain() == null
                || SceneMaxLauncher.class.getProtectionDomain().getCodeSource() == null
                || SceneMaxLauncher.class.getProtectionDomain().getCodeSource().getLocation() == null) {
            return null;
        }

        File file = new File(SceneMaxLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return file.isFile() ? file : null;
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

    private String extractSourceScriptPath(String prg) {
        if (prg == null || prg.isEmpty()) {
            return prg;
        }

        java.util.regex.Pattern p = java.util.regex.Pattern.compile("//\\$\\[source_rel\\]=(.+?);", java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.MULTILINE);
        java.util.regex.Matcher m = p.matcher(prg);
        if (m.find()) {
            String path = m.group(1).trim().replace("/", File.separator);
            if (path.startsWith(File.separator)) {
                path = path.substring(1);
            }
            if (!path.isBlank()) {
                sourceScriptFile = new File(new File("running"), path);
            }
            prg = prg.replaceFirst("//\\$\\[source_rel\\]=(.+?);", "");
        }

        return prg;
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
