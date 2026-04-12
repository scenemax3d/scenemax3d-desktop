package com.scenemax.designer.animation;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class AnimationImportProcessRunner {

    public static AnimationImportResult inspect(File sourceFile) throws IOException {
        File outputFile = File.createTempFile("scenemax-animation-inspect-", ".json");
        try {
            runWorker("inspect", sourceFile.getAbsolutePath(), outputFile.getAbsolutePath());
            return readResult(outputFile);
        } finally {
            FileUtils.deleteQuietly(outputFile);
        }
    }

    public static AnimationImportResult importAnimation(File sourceFile, File resourcesFolder, String requestedName) throws IOException {
        File outputFile = File.createTempFile("scenemax-animation-import-", ".json");
        try {
            runWorker("import", sourceFile.getAbsolutePath(), resourcesFolder.getAbsolutePath(), requestedName, outputFile.getAbsolutePath());
            return readResult(outputFile);
        } finally {
            FileUtils.deleteQuietly(outputFile);
        }
    }

    private static void runWorker(String... args) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        command.add("-cp");
        command.add(workerClasspath());
        command.add("com.scenemax.designer.animation.AnimationImportWorker");
        for (String arg : args) {
            command.add(arg);
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output;
        try {
            output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Animation import worker failed with exit code " + exitCode + ".\n" + tail(output));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Animation import worker was interrupted.", e);
        }
    }

    private static String workerClasspath() throws IOException {
        List<String> entries = new ArrayList<>();
        entries.addAll(baseWorkerClasspath());
        entries.addAll(extractImporterLibraries());
        return String.join(File.pathSeparator, entries);
    }

    private static List<String> baseWorkerClasspath() {
        List<String> result = new ArrayList<>();
        String classPath = System.getProperty("java.class.path", "");
        for (String entry : classPath.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (entry == null || entry.isBlank() || isLwjgl2CanvasEntry(entry)) {
                continue;
            }
            result.add(entry);
        }
        return result;
    }

    private static boolean isLwjgl2CanvasEntry(String classpathEntry) {
        String name = new File(classpathEntry).getName().toLowerCase();
        return name.startsWith("jme3-lwjgl-")
                || name.startsWith("lwjgl-platform-")
                || name.startsWith("lwjgl_util-")
                || name.matches("lwjgl-2\\..*\\.jar");
    }

    private static List<String> extractImporterLibraries() throws IOException {
        List<String> result = new ArrayList<>();
        InputStream indexStream = AnimationImportProcessRunner.class.getClassLoader()
                .getResourceAsStream("animation-importer-libs/index.txt");
        if (indexStream == null) {
            return findImporterLibrariesOnDisk();
        }

        File targetDir = new File(System.getProperty("java.io.tmpdir"), "scenemax-animation-importer-libs");
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("Failed to create animation importer library folder: " + targetDir.getAbsolutePath());
        }

        String index = new String(indexStream.readAllBytes(), StandardCharsets.UTF_8);
        for (String rawName : index.split("\\R")) {
            String jarName = rawName.trim();
            if (jarName.isEmpty()) {
                continue;
            }
            File target = new File(targetDir, jarName);
            try (InputStream jarStream = AnimationImportProcessRunner.class.getClassLoader()
                    .getResourceAsStream("animation-importer-libs/" + jarName)) {
                if (jarStream == null) {
                    throw new IOException("Missing animation importer library resource: " + jarName);
                }
                Files.copy(jarStream, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            result.add(target.getAbsolutePath());
        }
        return result;
    }

    private static List<String> findImporterLibrariesOnDisk() throws IOException {
        List<File> candidates = new ArrayList<>();
        candidates.add(new File("scenemax_designer/build/resources/main/animation-importer-libs"));
        candidates.add(new File("scenemax_designer/build/generated/animation-importer-libs"));
        candidates.add(new File("build/resources/main/animation-importer-libs"));
        candidates.add(new File("build/generated/animation-importer-libs"));

        String classPath = System.getProperty("java.class.path", "");
        for (String entry : classPath.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            File cpEntry = new File(entry);
            if (cpEntry.isDirectory()) {
                candidates.add(new File(cpEntry, "animation-importer-libs"));
            }
        }

        for (File dir : candidates) {
            List<String> jars = jarsInDirectory(dir);
            if (!jars.isEmpty()) {
                return jars;
            }
        }
        return new ArrayList<>();
    }

    private static List<String> jarsInDirectory(File dir) throws IOException {
        List<String> result = new ArrayList<>();
        if (dir == null || !dir.isDirectory()) {
            return result;
        }
        File indexFile = new File(dir, "index.txt");
        if (indexFile.isFile()) {
            String index = FileUtils.readFileToString(indexFile, StandardCharsets.UTF_8);
            for (String rawName : index.split("\\R")) {
                String jarName = rawName.trim();
                if (jarName.isEmpty()) {
                    continue;
                }
                File jar = new File(dir, jarName);
                if (jar.isFile()) {
                    result.add(jar.getAbsolutePath());
                }
            }
        } else {
            File[] jars = dir.listFiles((folder, name) -> name.toLowerCase().endsWith(".jar"));
            if (jars != null) {
                for (File jar : jars) {
                    result.add(jar.getAbsolutePath());
                }
            }
        }
        return result;
    }

    private static AnimationImportResult readResult(File outputFile) throws IOException {
        JSONObject json = new JSONObject(FileUtils.readFileToString(outputFile, StandardCharsets.UTF_8));
        File assetFolder = json.has("assetFolder") ? new File(json.getString("assetFolder")) : null;
        File animationFile = json.has("animationFile") ? new File(json.getString("animationFile")) : null;
        return new AnimationImportResult(assetFolder, animationFile,
                toList(json.optJSONArray("clipNames")),
                toList(json.optJSONArray("clipSummaries")),
                json.optString("selectedClipName", null),
                null);
    }

    private static List<String> toList(JSONArray array) {
        List<String> result = new ArrayList<>();
        if (array == null) {
            return result;
        }
        for (int i = 0; i < array.length(); i++) {
            result.add(array.optString(i, ""));
        }
        return result;
    }

    private static String javaExecutable() {
        String javaHome = System.getProperty("java.home");
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
        return new File(new File(javaHome, "bin"), executable).getAbsolutePath();
    }

    private static String tail(String output) {
        if (output == null) {
            return "";
        }
        int maxLength = 6000;
        return output.length() <= maxLength ? output : output.substring(output.length() - maxLength);
    }
}
