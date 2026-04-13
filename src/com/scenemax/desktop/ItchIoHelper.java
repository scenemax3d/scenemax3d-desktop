package com.scenemax.desktop;

import javax.swing.*;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

public final class ItchIoHelper {

    private static final Pattern ITCH_PAGE_PATTERN = Pattern.compile("^(?:https?://)?([a-z0-9_-]+)\\.itch\\.io/([a-z0-9_-]+)(?:/.*)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ITCH_TARGET_PATTERN = Pattern.compile("^([a-z0-9_-]+)/([a-z0-9_-]+)$", Pattern.CASE_INSENSITIVE);

    private ItchIoHelper() {
    }

    public static String normalizeGameTarget(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.length() == 0) {
            return "";
        }

        Matcher pageMatcher = ITCH_PAGE_PATTERN.matcher(value);
        if (pageMatcher.matches()) {
            return pageMatcher.group(1).toLowerCase(Locale.ROOT) + "/" + pageMatcher.group(2).toLowerCase(Locale.ROOT);
        }

        Matcher targetMatcher = ITCH_TARGET_PATTERN.matcher(value);
        if (targetMatcher.matches()) {
            return targetMatcher.group(1).toLowerCase(Locale.ROOT) + "/" + targetMatcher.group(2).toLowerCase(Locale.ROOT);
        }

        throw new IllegalArgumentException("Enter either your itch.io page URL (for example https://user.itch.io/game) or a target like user/game.");
    }

    public static String defaultChannel(String platformName, String configuredValue) {
        String value = configuredValue == null ? "" : configuredValue.trim();
        if (value.length() > 0) {
            return value;
        }
        return platformName;
    }

    public static String findBundledButlerExecutable() {
        String locallyInstalledButler = findInstalledButlerExecutable();
        if (locallyInstalledButler != null && locallyInstalledButler.trim().length() > 0) {
            return locallyInstalledButler;
        }

        String appData = System.getenv("APPDATA");
        if (appData == null || appData.trim().length() == 0) {
            return null;
        }

        File chosenVersionFile = new File(appData, "itch\\broth\\butler\\.chosen-version");
        if (!chosenVersionFile.isFile()) {
            return null;
        }

        try {
            String version = FileUtils.readFileToString(chosenVersionFile, StandardCharsets.UTF_8).trim();
            if (version.length() == 0) {
                return null;
            }

            File butlerExe = new File(appData, "itch\\broth\\butler\\versions\\" + version + "\\butler.exe");
            if (butlerExe.isFile()) {
                return butlerExe.getAbsolutePath();
            }
        } catch (Exception e) {
            return null;
        }

        return null;
    }

    public static String findInstalledButlerExecutable() {
        File toolsFolder = new File(Util.getWorkingDir(), "tools\\butler");
        File butlerExe = findButlerExecutableUnder(toolsFolder);
        return butlerExe == null ? null : butlerExe.getAbsolutePath();
    }

    public static boolean hasLocalCredentials() {
        String userProfile = System.getenv("USERPROFILE");
        if (userProfile == null || userProfile.trim().length() == 0) {
            return false;
        }
        File creds = new File(userProfile, ".config\\itch\\butler_creds");
        return creds.isFile();
    }

    public static String resolveButlerExecutablePath(String configuredPath) throws IOException {
        String value = configuredPath == null ? "" : configuredPath.trim();
        if (value.length() > 0) {
            File configuredFile = new File(value);
            if (!configuredFile.isFile()) {
                throw new IOException("Configured butler executable was not found: " + configuredFile.getAbsolutePath());
            }
            return configuredFile.getAbsolutePath();
        }

        String bundledButler = findBundledButlerExecutable();
        if (bundledButler != null && bundledButler.trim().length() > 0) {
            return bundledButler;
        }

        return "butler";
    }

    public static String promptAndRunButlerLogin(Window owner, String configuredPath) {
        String butlerExecutable;
        try {
            butlerExecutable = resolveButlerExecutablePath(configuredPath);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(owner, ex.getMessage(), "Butler Login", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        try {
            ensureButlerAvailable(butlerExecutable);
        } catch (IOException ex) {
            StringBuilder message = new StringBuilder();
            message.append(ex.getMessage());
            message.append("\n\n");
            message.append(buildButlerInstallInstructions());
            JTextArea area = new JTextArea(message.toString());
            area.setEditable(false);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
            area.setCaretPosition(0);
            JScrollPane scrollPane = new JScrollPane(area);
            scrollPane.setPreferredSize(new java.awt.Dimension(640, 260));
            JOptionPane.showMessageDialog(owner, scrollPane, "Butler Login", JOptionPane.INFORMATION_MESSAGE);
            return null;
        }

        ButlerLoginDialog dialog = new ButlerLoginDialog(owner, butlerExecutable);
        dialog.setVisible(true);
        return dialog.isLoginSucceeded() ? butlerExecutable : null;
    }

    private static void ensureButlerAvailable(String butlerExecutable) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(butlerExecutable, "version");
        processBuilder.directory(new File(Util.getWorkingDir()));
        processBuilder.redirectErrorStream(true);
        try {
            Process process = processBuilder.start();
            String output;
            try (InputStream inputStream = process.getInputStream()) {
                output = new String(Util.toByteArray(inputStream), StandardCharsets.UTF_8);
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String message = output == null ? "" : output.trim();
                throw new IOException("Unable to run butler." + (message.length() == 0 ? "" : " " + message));
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Butler verification was interrupted.", ex);
        } catch (IOException ex) {
            throw new IOException("Unable to run butler. Make sure the executable exists and is allowed to start on this machine.", ex);
        }
    }

    public static String installButlerFromZip(File zipFile) throws IOException {
        if (zipFile == null || !zipFile.isFile()) {
            throw new IOException("Selected butler zip file was not found.");
        }

        File toolsFolder = new File(Util.getWorkingDir(), "tools");
        File installFolder = new File(toolsFolder, "butler");
        if (installFolder.exists()) {
            FileUtils.deleteDirectory(installFolder);
        }
        FileUtils.forceMkdir(installFolder);

        String unzipError = Util.unzip(zipFile.getAbsolutePath(), installFolder.getAbsolutePath());
        if (unzipError != null && unzipError.trim().length() > 0) {
            throw new IOException("Failed to extract butler zip: " + unzipError.trim());
        }

        File butlerExe = findButlerExecutableUnder(installFolder);
        if (butlerExe == null) {
            throw new IOException("SceneMax extracted the zip, but could not find butler.exe inside it.");
        }

        return butlerExe.getAbsolutePath();
    }

    private static File findButlerExecutableUnder(File rootFolder) {
        if (rootFolder == null || !rootFolder.isDirectory()) {
            return null;
        }

        Collection<File> files = FileUtils.listFiles(rootFolder, null, true);
        for (File file : files) {
            if ("butler.exe".equalsIgnoreCase(file.getName())) {
                return file;
            }
        }
        return null;
    }

    public static String buildButlerInstallInstructions() {
        StringBuilder sb = new StringBuilder();
        sb.append("butler was not found. Install it once, then package again.\r\n\r\n");
        sb.append("Windows setup:\r\n");
        sb.append("1. Download butler from https://itchio.itch.io/butler\r\n");
        sb.append("2. In SceneMax Project Settings, browse to the downloaded butler zip file\r\n");
        sb.append("3. SceneMax will extract it into the installation tools folder and save the butler.exe path for you\r\n");
        sb.append("4. Verify it with: butler version\r\n");
        sb.append("5. Authenticate once with: butler login\r\n\r\n");
        sb.append("If you already have the itch desktop app installed, SceneMax can also use its bundled butler copy automatically.");
        return sb.toString();
    }
}
