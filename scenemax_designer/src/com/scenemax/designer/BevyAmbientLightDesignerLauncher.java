package com.scenemax.designer;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class BevyAmbientLightDesignerLauncher {

    private BevyAmbientLightDesignerLauncher() {
    }

    public static LaunchProcess open(File projectRoot, File designFile) throws IOException {
        return open(projectRoot, designFile, null);
    }

    public static LaunchProcess open(File projectRoot, File designFile, String cameraSnapshotJson) throws IOException {
        if (designFile == null || !designFile.isFile()) {
            throw new IOException("Designer document is not available.");
        }

        File resolvedProjectRoot = resolveProjectRoot(projectRoot, designFile);
        CommandTarget target = commandTarget(resolvedProjectRoot, designFile);
        List<String> command = new ArrayList<>(target.command);
        command.add("ambient-light-designer");
        command.add("--design");
        command.add(designFile.getCanonicalFile().getAbsolutePath());
        command.add("--project-root");
        command.add(resolvedProjectRoot.getCanonicalFile().getAbsolutePath());
        if (cameraSnapshotJson != null && !cameraSnapshotJson.isBlank()) {
            command.add("--camera-snapshot");
            command.add(cameraSnapshotJson);
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        if (target.workingDirectory != null) {
            builder.directory(target.workingDirectory);
        }
        builder.environment().put("WGPU_BACKEND", "dx12");
        if (target.usedCargo) {
            builder.environment().put("SCENEMAX_EFFEKSEER_NATIVE_BUILD", "1");
        }
        builder.redirectErrorStream(true);
        Files.deleteIfExists(target.logFile.toPath());
        appendLogLine(target.logFile, "Launching: " + String.join(" ", command));
        appendLogLine(target.logFile, "Working directory: "
                + (target.workingDirectory == null ? "" : target.workingDirectory.getAbsolutePath()));
        appendLogLine(target.logFile, "WGPU_BACKEND: dx12");
        return new LaunchProcess(builder.start(), target.usedCargo, target.logFile);
    }

    private static File resolveProjectRoot(File projectRoot, File designFile) throws IOException {
        if (projectRoot != null && projectRoot.isDirectory()) {
            return projectRoot;
        }
        File current = designFile.getCanonicalFile().getParentFile();
        while (current != null) {
            if ("scripts".equalsIgnoreCase(current.getName()) && current.getParentFile() != null) {
                return current.getParentFile();
            }
            if (new File(current, "resources").isDirectory() && new File(current, "scripts").isDirectory()) {
                return current;
            }
            current = current.getParentFile();
        }
        throw new IOException("Could not resolve the Bevy project root for " + designFile.getAbsolutePath());
    }

    private static CommandTarget commandTarget(File projectRoot, File designFile) throws IOException {
        File nextgenRoot = resolveNextGenProjectorRoot(projectRoot, designFile);
        File debugExe = new File(nextgenRoot, "target/debug/scenemax_projector_nextgen.exe");
        File releaseExe = new File(nextgenRoot, "target/release/scenemax_projector_nextgen.exe");
        File builtExe = newestExistingFile(debugExe, releaseExe);
        String cargoCommand = resolveCargoCommand();
        boolean builtExeIsCurrent = builtExe != null && !isStaleDevExecutable(builtExe, nextgenRoot);
        boolean useCargo = !builtExeIsCurrent && cargoCommand != null && !cargoCommand.isBlank();

        List<String> command = new ArrayList<>();
        if (builtExeIsCurrent) {
            command.add(builtExe.getAbsolutePath());
            return new CommandTarget(command, nextgenRoot, false, new File(nextgenRoot, "bevy-ambient-light-designer.log"));
        }
        if (useCargo) {
            command.add(cargoCommand);
            command.add("run");
            command.add("-p");
            command.add("scenemax_projector_nextgen");
            command.add("--features");
            command.add("effekseer_native");
            command.add("--");
            return new CommandTarget(command, nextgenRoot, true, new File(nextgenRoot, "bevy-ambient-light-designer.log"));
        }
        if (builtExe != null) {
            command.add(builtExe.getAbsolutePath());
            return new CommandTarget(command, nextgenRoot, false, new File(nextgenRoot, "bevy-ambient-light-designer.log"));
        }
        throw new IOException("SceneMax NextGen projector was not found. Configure nextgen_projector_path or install Cargo.");
    }

    private static File resolveNextGenProjectorRoot(File projectRoot, File designFile) throws IOException {
        List<File> candidates = new ArrayList<>();
        addConfiguredNextGenCandidate(candidates);
        addNextGenCandidate(candidates, new File(env("SCENEMAX_NEXTGEN_PROJECTOR")));
        addAncestorCandidates(candidates, projectRoot);
        addAncestorCandidates(candidates, designFile);
        addAncestorCandidates(candidates, new File(System.getProperty("user.dir", ".")));
        File workingDir = reflectedWorkingDir();
        if (workingDir != null) {
            addAncestorCandidates(candidates, workingDir);
        }

        for (File candidate : candidates) {
            File root = normalizeNextGenRoot(candidate);
            if (root != null && new File(root, "Cargo.toml").isFile() && new File(root, "crates").isDirectory()) {
                return root.getCanonicalFile();
            }
        }
        throw new IOException("Could not find the scenemax_projector_nextgen workspace.");
    }

    private static void addConfiguredNextGenCandidate(List<File> candidates) {
        addNextGenCandidate(candidates, new File(reflectedParam("nextgen_projector_path")));
    }

    private static void addAncestorCandidates(List<File> candidates, File start) {
        if (start == null) {
            return;
        }
        File current = start.isFile() ? start.getParentFile() : start;
        while (current != null) {
            addNextGenCandidate(candidates, current);
            addNextGenCandidate(candidates, new File(current, "scenemax_projector_nextgen"));
            current = current.getParentFile();
        }
    }

    private static void addNextGenCandidate(List<File> candidates, File candidate) {
        if (candidate != null && !candidate.getPath().isBlank()) {
            candidates.add(candidate);
        }
    }

    private static File normalizeNextGenRoot(File candidate) {
        if (candidate == null || candidate.getPath().isBlank()) {
            return null;
        }
        File file = candidate;
        if (file.isFile()) {
            File parent = file.getParentFile();
            if (parent != null && ("debug".equalsIgnoreCase(parent.getName())
                    || "release".equalsIgnoreCase(parent.getName()))
                    && parent.getParentFile() != null
                    && parent.getParentFile().getParentFile() != null) {
                return parent.getParentFile().getParentFile();
            }
            return parent;
        }
        return file;
    }

    private static String resolveCargoCommand() {
        String configured = reflectedParam("rust_cargo_path");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        String cargoHome = env("CARGO_HOME");
        if (!cargoHome.isBlank()) {
            File cargo = new File(cargoHome, "bin/cargo.exe");
            if (cargo.isFile()) {
                return cargo.getAbsolutePath();
            }
        }
        String userProfile = env("USERPROFILE");
        if (!userProfile.isBlank()) {
            File cargo = new File(userProfile, ".cargo/bin/cargo.exe");
            if (cargo.isFile()) {
                return cargo.getAbsolutePath();
            }
        }
        return "cargo";
    }

    private static boolean isStaleDevExecutable(File exe, File nextgenRoot) {
        long exeModified = exe.lastModified();
        return newestRustSourceTimestamp(nextgenRoot) > exeModified;
    }

    private static File newestExistingFile(File... files) {
        File newest = null;
        for (File file : files) {
            if (file == null || !file.isFile()) {
                continue;
            }
            if (newest == null || file.lastModified() > newest.lastModified()) {
                newest = file;
            }
        }
        return newest;
    }

    private static long newestRustSourceTimestamp(File nextgenRoot) {
        long newest = 0L;
        Deque<File> pending = new ArrayDeque<>();
        pending.add(new File(nextgenRoot, "crates"));
        pending.add(new File(nextgenRoot, "Cargo.toml"));
        pending.add(new File(nextgenRoot, "Cargo.lock"));
        while (!pending.isEmpty()) {
            File file = pending.removeFirst();
            if (file == null || !file.exists()) {
                continue;
            }
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children == null) {
                    continue;
                }
                for (File child : children) {
                    pending.add(child);
                }
                continue;
            }
            String name = file.getName().toLowerCase();
            if (name.endsWith(".rs") || "cargo.toml".equals(name) || "cargo.lock".equals(name)) {
                newest = Math.max(newest, file.lastModified());
            }
        }
        return newest;
    }

    public static void appendLogLine(File log, String line) {
        try {
            Files.writeString(log.toPath(), line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Launch logging is diagnostic only.
        }
    }

    private static File reflectedWorkingDir() {
        try {
            Class<?> util = Class.forName("com.scenemax.desktop.Util");
            Method method = util.getMethod("getWorkingDir");
            Object value = method.invoke(null);
            return value == null ? null : new File(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String reflectedParam(String key) {
        try {
            Class<?> appDb = Class.forName("com.scenemax.desktop.AppDB");
            Object instance = appDb.getMethod("getInstance").invoke(null);
            Object value = appDb.getMethod("getParam", String.class).invoke(instance, key);
            return value == null ? "" : String.valueOf(value);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String env(String key) {
        String value = System.getenv(key);
        return value == null ? "" : value;
    }

    public static final class LaunchProcess {
        private final Process process;
        private final boolean usedCargo;
        private final File logFile;

        private LaunchProcess(Process process, boolean usedCargo, File logFile) {
            this.process = process;
            this.usedCargo = usedCargo;
            this.logFile = logFile;
        }

        public Process getProcess() {
            return process;
        }

        public boolean isUsedCargo() {
            return usedCargo;
        }

        public File getLogFile() {
            return logFile;
        }
    }

    private static final class CommandTarget {
        private final List<String> command;
        private final File workingDirectory;
        private final boolean usedCargo;
        private final File logFile;

        private CommandTarget(List<String> command, File workingDirectory, boolean usedCargo, File logFile) {
            this.command = command;
            this.workingDirectory = workingDirectory;
            this.usedCargo = usedCargo;
            this.logFile = logFile;
        }
    }
}
