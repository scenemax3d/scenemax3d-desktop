package com.scenemax.desktop;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

public final class BevyShaderDesignerLauncher {

    private BevyShaderDesignerLauncher() {
    }

    public static void launch(Component parent, SceneMaxProject project, File shaderFile) {
        launch(parent, project, shaderFile, null);
    }

    public static void launch(Component parent, SceneMaxProject project, File shaderFile,
                              Consumer<String> statusConsumer) {
        if (shaderFile == null) {
            return;
        }

        Thread thread = new Thread(() -> {
            try {
                publishStatus(statusConsumer, "Resolving Bevy shader designer...");
                LaunchProcess launchProcess = startProcess(project, shaderFile, statusConsumer);
                Process process = launchProcess.process;
                File log = new File("bevy-shader-designer.log");
                boolean[] runtimeStarted = { !launchProcess.usedCargo };
                if (!launchProcess.usedCargo) {
                    publishStatus(statusConsumer, "Bevy shader designer process started. Opening designer window...");
                }
                StreamGobbler output = new StreamGobbler(process.getInputStream(), line -> {
                    System.out.println(line);
                    appendLogLine(log, line);
                    if (launchProcess.usedCargo) {
                        updateCargoStatus(line, runtimeStarted, statusConsumer);
                    }
                });
                Thread outputThread = new Thread(output, "bevy-shader-designer-output");
                outputThread.setDaemon(true);
                outputThread.start();
                int exitCode = process.waitFor();
                if (!runtimeStarted[0] || exitCode != 0) {
                    publishStatus(statusConsumer, "Bevy shader designer stopped. Check bevy-shader-designer.log for details.");
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                publishStatus(statusConsumer, "Bevy shader designer failed to start.");
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(parent,
                        "Could not open the Bevy shader designer:\n" + ex.getMessage(),
                        "Bevy Shader Designer",
                        JOptionPane.ERROR_MESSAGE));
            }
        }, "bevy-shader-designer-launcher");
        thread.setDaemon(true);
        thread.start();
    }

    private static LaunchProcess startProcess(SceneMaxProject project, File shaderFile,
                                              Consumer<String> statusConsumer) throws IOException {
        File nextGenRoot = resolveNextGenProjectorRoot();
        File debugExe = new File(nextGenRoot, "target/debug/scenemax_projector_nextgen.exe");
        File releaseExe = new File(nextGenRoot, "target/release/scenemax_projector_nextgen.exe");
        File builtExe = newestExistingFile(debugExe, releaseExe);
        File cargoExe = resolveCargoExecutable();

        List<String> command = new ArrayList<>();
        File processDirectory;
        boolean builtExeIsCurrent = builtExe != null && !isNextGenExecutableStale(nextGenRoot, debugExe, releaseExe);
        boolean useCargo = !builtExeIsCurrent && cargoExe.isFile();
        if (builtExeIsCurrent) {
            command.add(builtExe.getAbsolutePath());
            processDirectory = nextGenRoot;
            publishStatus(statusConsumer, "Starting the current Bevy shader designer...");
        } else if (useCargo) {
            command.add(cargoExe.getAbsolutePath());
            command.add("run");
            command.add("-p");
            command.add("scenemax_projector_nextgen");
            command.add("--features");
            command.add("effekseer_native");
            command.add("--");
            processDirectory = nextGenRoot;
            publishStatus(statusConsumer, builtExe == null
                    ? "Building Rust/Bevy shader designer..."
                    : "Rust/Bevy shader designer changed. Rebuilding...");
        } else if (builtExe != null) {
            command.add(builtExe.getAbsolutePath());
            processDirectory = nextGenRoot;
            publishStatus(statusConsumer, "Cargo was not found, starting the existing Bevy shader designer...");
        } else {
            throw new IOException("SceneMax NextGen projector was not found. Configure nextgen_projector_path or install Cargo.");
        }

        command.add("shader-designer");
        command.add("--shader");
        command.add(shaderFile.getAbsolutePath());
        if (project != null && project.path != null && !project.path.isBlank()) {
            command.add("--project-root");
            command.add(new File(project.path).getAbsolutePath());
        }

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(processDirectory);
        if (useCargo) {
            processBuilder.environment().put("SCENEMAX_EFFEKSEER_NATIVE_BUILD", "1");
        }
        processBuilder.redirectErrorStream(true);
        Files.deleteIfExists(new File("bevy-shader-designer.log").toPath());
        return new LaunchProcess(processBuilder.start(), useCargo);
    }

    private static void updateCargoStatus(String line, boolean[] runtimeStarted,
                                          Consumer<String> statusConsumer) {
        if (line == null) {
            return;
        }
        String trimmed = line.trim();
        String lower = trimmed.toLowerCase();
        if (!runtimeStarted[0] && lower.contains("running") && lower.contains("scenemax_projector_nextgen")) {
            runtimeStarted[0] = true;
            publishStatus(statusConsumer, "Bevy shader designer process started. Opening designer window...");
            return;
        }
        if (trimmed.startsWith("Compiling ")) {
            publishStatus(statusConsumer, "Compiling " + trimmed.substring("Compiling ".length()).trim() + "...");
        } else if (trimmed.startsWith("Checking ")) {
            publishStatus(statusConsumer, "Checking " + trimmed.substring("Checking ".length()).trim() + "...");
        } else if (trimmed.startsWith("Finished ")) {
            publishStatus(statusConsumer, "Rust/Bevy build finished. Opening shader designer...");
        } else if (trimmed.startsWith("Blocking waiting for file lock")) {
            publishStatus(statusConsumer, "Waiting for Cargo build lock...");
        }
    }

    private static void appendLogLine(File log, String line) {
        try {
            Files.writeString(log.toPath(), line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void publishStatus(Consumer<String> statusConsumer, String message) {
        if (statusConsumer == null || message == null || message.isBlank()) {
            return;
        }
        SwingUtilities.invokeLater(() -> statusConsumer.accept(message));
    }

    private static File resolveNextGenProjectorRoot() {
        String configuredPath = AppDB.getInstance().getParam("nextgen_projector_path");
        if (configuredPath != null && !configuredPath.isBlank()) {
            File configured = new File(configuredPath.trim());
            if (configured.isFile()) {
                File targetDir = configured.getParentFile();
                if (targetDir != null && ("debug".equalsIgnoreCase(targetDir.getName())
                        || "release".equalsIgnoreCase(targetDir.getName()))
                        && targetDir.getParentFile() != null
                        && targetDir.getParentFile().getParentFile() != null) {
                    return targetDir.getParentFile().getParentFile();
                }
                return configured.getParentFile();
            }
            return configured;
        }
        return new File(Util.getWorkingDir(), "scenemax_projector_nextgen");
    }

    private static boolean isNextGenExecutableStale(File nextGenRoot, File debugExe, File releaseExe) {
        File newestExe = newestExistingFile(debugExe, releaseExe);
        if (newestExe == null) {
            return true;
        }
        long newestSource = newestRustSourceTimestamp(nextGenRoot);
        return newestSource > newestExe.lastModified();
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

    private static long newestRustSourceTimestamp(File nextGenRoot) {
        long newest = 0L;
        Deque<File> pending = new ArrayDeque<>();
        pending.add(new File(nextGenRoot, "crates"));
        pending.add(new File(nextGenRoot, "Cargo.toml"));
        pending.add(new File(nextGenRoot, "Cargo.lock"));
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

    private static File resolveCargoExecutable() {
        String configuredPath = AppDB.getInstance().getParam("rust_cargo_path");
        if (configuredPath != null && !configuredPath.isBlank()) {
            return new File(configuredPath.trim());
        }
        String userProfile = System.getenv("USERPROFILE");
        if (userProfile != null && !userProfile.isBlank()) {
            return new File(userProfile, ".cargo/bin/cargo.exe");
        }
        return new File("cargo");
    }

    private static final class LaunchProcess {
        private final Process process;
        private final boolean usedCargo;

        private LaunchProcess(Process process, boolean usedCargo) {
            this.process = process;
            this.usedCargo = usedCargo;
        }
    }
}
