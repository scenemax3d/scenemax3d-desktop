package com.scenemax.designer.effekseer;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.prefs.Preferences;
import java.util.concurrent.TimeUnit;

public class EffekseerTooling {

    private static final String DB_KEY = "effekseer_tool_path";
    private static final Preferences PREFS = Preferences.userNodeForPackage(EffekseerTooling.class);

    public static String getConfiguredToolPath() {
        String path = PREFS.get(DB_KEY, "");
        if (path == null || path.trim().isEmpty()) {
            path = loadConfigProperty("effekseer_tool_path");
        }
        path = path != null ? path.trim() : "";
        if (!path.isEmpty() && new File(path).isFile()) {
            return path;
        }

        File detected = detectInstalledTool();
        return detected != null ? detected.getAbsolutePath() : "";
    }

    public static void setConfiguredToolPath(String path) {
        PREFS.put(DB_KEY, path != null ? path.trim() : "");
    }

    public static File chooseToolExecutable(Component parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose Effekseer executable");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setFileFilter(new FileNameExtensionFilter("Executable files", "exe"));
        String current = getConfiguredToolPath();
        if (!current.isEmpty()) {
            File currentFile = new File(current);
            if (currentFile.getParentFile() != null) {
                chooser.setCurrentDirectory(currentFile.getParentFile());
            }
        }
        int result = chooser.showOpenDialog(parent);
        return result == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
    }

    public static boolean isConfiguredToolAvailable() {
        return resolveToolExecutable() != null;
    }

    public static String launchPreview(File effectFile) throws IOException {
        File toolFile = resolveToolExecutable();
        if (toolFile == null) {
            throw new IOException("Effekseer executable is not configured.");
        }
        if (effectFile == null || !effectFile.isFile()) {
            throw new IOException("Imported Effekseer effect file was not found.");
        }
        new ProcessBuilder(toolFile.getAbsolutePath(), effectFile.getAbsolutePath()).start();
        return toolFile.getAbsolutePath();
    }

    public static File exportRuntimeEffect(File importedEffectFile) throws IOException {
        if (importedEffectFile == null || !importedEffectFile.isFile()) {
            throw new IOException("Imported Effekseer effect file was not found.");
        }
        String name = importedEffectFile.getName().toLowerCase();
        if (!name.endsWith(".efkproj")) {
            return importedEffectFile;
        }

        File toolFile = resolveToolExecutable();
        if (toolFile == null) {
            throw new IOException("Effekseer executable is not configured.");
        }

        File outputFile = new File(importedEffectFile.getParentFile(), baseName(importedEffectFile.getName()) + ".efkefc");
        if (outputFile.isFile() && outputFile.lastModified() >= importedEffectFile.lastModified()) {
            return outputFile;
        }

        Process process = new ProcessBuilder(
                toolFile.getAbsolutePath(),
                "-cui",
                "-in",
                importedEffectFile.getAbsolutePath(),
                "-o",
                outputFile.getAbsolutePath()
        ).directory(toolFile.getParentFile()).start();

        boolean finished;
        try {
            finished = process.waitFor(180, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while exporting runtime Effekseer effect.", ex);
        }

        if (!finished) {
            process.destroy();
            try {
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }

        if (outputFile.isFile()) {
            return outputFile;
        }

        throw new IOException("Effekseer runtime export did not produce " + outputFile.getAbsolutePath());
    }

    public static void revealInExplorer(File file) throws IOException {
        if (file == null) {
            return;
        }
        Desktop.getDesktop().open(file.isDirectory() ? file : file.getParentFile());
    }

    private static File resolveToolExecutable() {
        String path = PREFS.get(DB_KEY, "");
        if (path == null || path.trim().isEmpty()) {
            path = loadConfigProperty("effekseer_tool_path");
        }
        if (path != null) {
            File configured = new File(path.trim());
            if (configured.isFile()) {
                return configured;
            }
        }
        return detectInstalledTool();
    }

    private static File detectInstalledTool() {
        List<File> candidates = new ArrayList<>();

        addIfFile(candidates, new File("C:\\dev\\scenemax_desktop\\tools\\Effekseer1.7.3.0Win\\Tool\\Effekseer.exe"));
        addIfFile(candidates, new File("C:\\Users\\adikt\\Downloads\\Effekseer1.7.3.0Win\\Tool\\Effekseer.exe"));

        String userHome = System.getProperty("user.home", "");
        if (!userHome.isBlank()) {
            scanForTool(candidates, new File(userHome, "Downloads"));
            scanForTool(candidates, new File(userHome, "Desktop"));
        }

        return candidates.stream()
                .filter(File::isFile)
                .max(Comparator.comparingLong(File::lastModified))
                .orElse(null);
    }

    private static void scanForTool(List<File> candidates, File root) {
        if (root == null || !root.isDirectory()) {
            return;
        }

        File direct = new File(root, "Effekseer.exe");
        addIfFile(candidates, direct);

        File[] children = root.listFiles();
        if (children == null) {
            return;
        }

        for (File child : children) {
            if (child == null || !child.isDirectory()) {
                continue;
            }
            addIfFile(candidates, new File(child, "Effekseer.exe"));
            addIfFile(candidates, new File(new File(child, "Tool"), "Effekseer.exe"));
        }
    }

    private static void addIfFile(List<File> candidates, File file) {
        if (file != null && file.isFile()) {
            candidates.add(file);
        }
    }

    private static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static String loadConfigProperty(String key) {
        File configFile = new File("config.properties");
        if (!configFile.isFile()) {
            return "";
        }
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(configFile)) {
            props.load(fis);
            return props.getProperty(key, "");
        } catch (IOException ex) {
            return "";
        }
    }
}
