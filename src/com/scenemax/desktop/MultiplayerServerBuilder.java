package com.scenemax.desktop;

import org.apache.commons.io.FileUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MultiplayerServerBuilder {

    private static final byte[] CONFIG_BEGIN = "SCENEMAX_MP_CONFIG_BEGIN".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CONFIG_END = "SCENEMAX_MP_CONFIG_END".getBytes(StandardCharsets.US_ASCII);
    private static final int CONFIG_PAYLOAD_SIZE = 4096;

    public File build(SceneMaxProject project) throws IOException {
        if (project == null) {
            throw new IOException("No active project is selected.");
        }

        File outputDir = new File(project.path, "multiplayer_server");
        FileUtils.forceMkdir(outputDir);

        String platform = normalizePlatform(project.multiplayerDeployOs);
        String executableName = platform.equals("windows-x64") ? "scenemax-mp-server.exe" : "scenemax-mp-server";
        File output = new File(outputDir, executableName);
        File prebuilt = new File("tools/multiplayer-server/bin/" + platform + "/" + executableName);
        File sourceExecutable = null;
        IOException zigFallbackReason = null;

        try {
            sourceExecutable = compileWithZig(platform, executableName);
        } catch (ZigUnavailableException ex) {
            zigFallbackReason = ex;
        }

        if (sourceExecutable != null && sourceExecutable.exists()) {
            installServerExecutable(sourceExecutable, output, project);
            return output;
        }

        if (prebuilt.exists()) {
            installServerExecutable(prebuilt, output, project);
            return output;
        }

        if (zigFallbackReason != null) {
            throw zigFallbackReason;
        }

        if (sourceExecutable == null || !sourceExecutable.exists()) {
            throw new IOException("Missing multiplayer server template for " + project.multiplayerDeployOs
                    + ". Expected " + prebuilt.getPath()
                    + ", and Zig fallback did not produce "
                    + (sourceExecutable == null ? "an output executable" : sourceExecutable.getPath()) + ".");
        }

        throw new IOException("Could not prepare the multiplayer server executable: " + sourceExecutable.getPath());
    }

    private File compileWithZig(String platform, String executableName) throws IOException {
        File buildFile = new File("tools/multiplayer-server/build.zig");
        if (!buildFile.exists()) {
            throw new ZigUnavailableException("Missing Zig multiplayer server build file: " + buildFile.getPath());
        }

        ProcessBuilder builder = new ProcessBuilder(
                "zig",
                "build",
                "--build-file",
                buildFile.getAbsolutePath(),
                "--summary",
                "all");
        builder.redirectErrorStream(true);
        Process process;
        try {
            process = builder.start();
        } catch (IOException ex) {
            throw new ZigUnavailableException("Zig compiler was not found. Add a prebuilt server executable or install Zig 0.16.x.", ex);
        }

        ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
        try {
            byte[] chunk = new byte[8192];
            int read;
            while ((read = process.getInputStream().read(chunk)) >= 0) {
                outputBuffer.write(chunk, 0, read);
            }
        } catch (IOException ex) {
            throw new IOException("Failed to read Zig compiler output.", ex);
        }
        try {
            int code = process.waitFor();
            if (code != 0) {
                throw new IOException("Zig multiplayer server build failed with exit code " + code + ".\n"
                        + outputBuffer.toString(StandardCharsets.UTF_8.name()));
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Zig multiplayer server build was interrupted.", ex);
        }
        return new File("tools/multiplayer-server/bin/" + platform + "/" + executableName);
    }

    private static class ZigUnavailableException extends IOException {
        ZigUnavailableException(String message) {
            super(message);
        }

        ZigUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private void installServerExecutable(File source, File output, SceneMaxProject project) throws IOException {
        try {
            stopRunningOutputProcesses(output);
            Files.copy(source.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
            patchEmbeddedConfig(output, project);
            output.setExecutable(true, false);
        } catch (IOException ex) {
            throw describeOutputWriteFailure(output, ex);
        }
    }

    private void stopRunningOutputProcesses(File output) throws IOException {
        List<ProcessHandle> processes = findRunningProcessesFor(output);
        for (ProcessHandle process : processes) {
            if (process.pid() == ProcessHandle.current().pid()) {
                continue;
            }

            process.destroy();
            try {
                process.onExit().get(3, TimeUnit.SECONDS);
            } catch (ExecutionException ex) {
                throw new IOException("Could not stop the running multiplayer server process before rebuilding:\n"
                        + output.getAbsolutePath()
                        + "\n\nRunning process: " + formatProcess(process), ex);
            } catch (TimeoutException ex) {
                process.destroyForcibly();
                try {
                    process.onExit().get(3, TimeUnit.SECONDS);
                } catch (ExecutionException forceEx) {
                    throw new IOException("Could not stop the running multiplayer server process before rebuilding:\n"
                            + output.getAbsolutePath()
                            + "\n\nRunning process: " + formatProcess(process), forceEx);
                } catch (TimeoutException forceEx) {
                    throw new IOException("Could not stop the running multiplayer server process before rebuilding:\n"
                            + output.getAbsolutePath()
                            + "\n\nRunning process: " + formatProcess(process), forceEx);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while stopping the running multiplayer server process.", interrupted);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while stopping the running multiplayer server process.", interrupted);
            }

            if (process.isAlive()) {
                throw new IOException("Could not stop the running multiplayer server process before rebuilding:\n"
                        + output.getAbsolutePath()
                        + "\n\nRunning process: " + formatProcess(process));
            }
        }
    }

    private IOException describeOutputWriteFailure(File output, IOException cause) {
        String runningProcess = findRunningProcessFor(output);
        if (runningProcess != null) {
            return new IOException("Cannot rebuild the multiplayer server because it is currently running:\n"
                    + output.getAbsolutePath()
                    + "\n\nRunning process: " + runningProcess
                    + "\n\nStop that server process, then click Build Multiplayer Server again.", cause);
        }
        if (cause instanceof AccessDeniedException) {
            return new IOException("Cannot write the multiplayer server executable:\n"
                    + output.getAbsolutePath()
                    + "\n\nClose any app or terminal that is using this file, then try again.", cause);
        }
        return cause;
    }

    private String findRunningProcessFor(File executable) {
        List<ProcessHandle> processes = findRunningProcessesFor(executable);
        return processes.isEmpty() ? null : formatProcess(processes.get(0));
    }

    private List<ProcessHandle> findRunningProcessesFor(File executable) {
        String expected;
        try {
            expected = executable.getCanonicalPath();
        } catch (IOException ex) {
            expected = executable.getAbsolutePath();
        }
        final String normalizedExpected = normalizePath(expected);

        List<ProcessHandle> matches = new ArrayList<>();
        ProcessHandle.allProcesses()
                .filter(process -> process.info().command().isPresent())
                .filter(process -> normalizedExpected.equals(normalizePath(process.info().command().get())))
                .forEach(matches::add);
        return matches;
    }

    private String formatProcess(ProcessHandle process) {
        ProcessHandle.Info info = process.info();
        Optional<String> command = info.command();
        return "pid=" + process.pid() + (command.isPresent() ? " command=" + command.get() : "");
    }

    private String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        try {
            return new File(path).getCanonicalPath().toLowerCase(Locale.ROOT);
        } catch (IOException ex) {
            return new File(path).getAbsolutePath().toLowerCase(Locale.ROOT);
        }
    }

    private void patchEmbeddedConfig(File executable, SceneMaxProject project) throws IOException {
        byte[] bytes = Files.readAllBytes(executable.toPath());
        int begin = indexOf(bytes, CONFIG_BEGIN, 0);
        if (begin < 0) {
            throw new IOException("The multiplayer server executable does not contain a SceneMax config block.");
        }
        int payloadStart = begin + CONFIG_BEGIN.length;
        int end = indexOf(bytes, CONFIG_END, payloadStart);
        if (end < 0 || end - payloadStart < CONFIG_PAYLOAD_SIZE) {
            throw new IOException("The multiplayer server config block is too small or malformed.");
        }

        byte[] payload = createConfigPayload(project);
        Arrays.fill(bytes, payloadStart, end, (byte) 0);
        System.arraycopy(payload, 0, bytes, payloadStart, Math.min(payload.length, end - payloadStart));
        Files.write(executable.toPath(), bytes);
    }

    private byte[] createConfigPayload(SceneMaxProject project) throws IOException {
        ensureProjectGuid(project);
        ByteBuffer buffer = ByteBuffer.allocate(CONFIG_PAYLOAD_SIZE);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(new byte[]{'S', 'M', 'X', 'M', 'P', 'C', 'F', 'G'});
        buffer.putShort((short) 3);
        buffer.putShort((short) 0);
        buffer.putInt(project.multiplayerServerPort <= 0 ? SceneMaxProject.DEFAULT_MULTIPLAYER_PORT : project.multiplayerServerPort);
        putFixedString(buffer, project.name, 128);
        putFixedString(buffer, project.path, 256);
        buffer.put(passwordHashOrDisabled(project.multiplayerPassword));
        putFixedString(buffer, project.projectGuid, 64);
        return buffer.array();
    }

    private void ensureProjectGuid(SceneMaxProject project) {
        if (project.projectGuid == null || project.projectGuid.trim().isEmpty()) {
            project.projectGuid = UUID.randomUUID().toString();
            Util.saveProjectSettings(project);
        }
    }

    private byte[] passwordHashOrDisabled(String password) throws IOException {
        if (password == null || password.trim().isEmpty()) {
            return new byte[32];
        }
        return passwordHash(password);
    }

    private byte[] passwordHash(String password) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest((password == null ? "" : password).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("SHA-256 digest is unavailable.", ex);
        }
    }

    private void putFixedString(ByteBuffer buffer, String value, int len) {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        int count = Math.min(bytes.length, len - 1);
        buffer.put(bytes, 0, count);
        for (int i = count; i < len; i++) {
            buffer.put((byte) 0);
        }
    }

    private int indexOf(byte[] source, byte[] target, int from) {
        outer:
        for (int i = from; i <= source.length - target.length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (source[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private String normalizePlatform(String os) {
        String normalized = os == null ? "" : os.toLowerCase(Locale.ROOT);
        if (normalized.contains("linux")) {
            return "linux-x64";
        }
        if (normalized.contains("mac")) {
            return "macos-x64";
        }
        return "windows-x64";
    }

}
