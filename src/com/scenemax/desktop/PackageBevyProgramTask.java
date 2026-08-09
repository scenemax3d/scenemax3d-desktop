package com.scenemax.desktop;

import com.scenemaxeng.compiler.ApplyMacroResults;
import com.scenemaxeng.compiler.MacroFilter;
import org.apache.commons.io.FileUtils;

import javax.swing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class PackageBevyProgramTask extends SwingWorker<Integer, String> {

    public enum PackageTarget {
        WINDOWS,
        LINUX,
        MAC_OSX,
        WEB
    }

    public static class PackageOptions {
        public final boolean uploadToItch;
        public final String itchButlerPath;
        public final String itchGameTarget;
        public final String itchApiKey;
        public final String itchWindowsChannel;
        public final String itchLinuxChannel;
        public final String itchMacChannel;
        public final String itchWebChannel;

        public PackageOptions(boolean uploadToItch,
                              String itchButlerPath,
                              String itchGameTarget,
                              String itchApiKey,
                              String itchWindowsChannel,
                              String itchLinuxChannel,
                              String itchMacChannel,
                              String itchWebChannel) {
            this.uploadToItch = uploadToItch;
            this.itchButlerPath = itchButlerPath == null ? "" : itchButlerPath.trim();
            this.itchGameTarget = itchGameTarget == null ? "" : itchGameTarget.trim();
            this.itchApiKey = itchApiKey == null ? "" : itchApiKey.trim();
            this.itchWindowsChannel = itchWindowsChannel == null ? "" : itchWindowsChannel.trim();
            this.itchLinuxChannel = itchLinuxChannel == null ? "" : itchLinuxChannel.trim();
            this.itchMacChannel = itchMacChannel == null ? "" : itchMacChannel.trim();
            this.itchWebChannel = itchWebChannel == null ? "" : itchWebChannel.trim();
        }
    }

    private final String prg;
    private final EnumSet<PackageTarget> targets;
    private final PackageOptions options;
    private final Runnable finish;
    private final Runnable canceled;
    private final MacroFilter macroFilter;
    private final List<File> producedArtifacts = new ArrayList<>();
    private final StringBuilder completionNotes = new StringBuilder();
    private File sourceScriptFile;
    private File scriptFolder;
    private File projectRoot;
    private File outputFolder;
    private File stagedProjectFolder;
    private volatile String statusNote = "";
    private volatile String failureMessage = "";

    public PackageBevyProgramTask(String scriptFilePath,
                                  String prg,
                                  List<PackageTarget> targets,
                                  PackageOptions options,
                                  Runnable finish,
                                  Runnable canceled) {
        this.prg = prg == null ? "" : prg;
        this.finish = finish;
        this.canceled = canceled;
        this.options = options == null
                ? new PackageOptions(false, "", "", "", "", "", "", "web")
                : options;
        this.targets = targets == null || targets.isEmpty()
                ? EnumSet.of(PackageTarget.WINDOWS)
                : EnumSet.copyOf(targets);
        this.macroFilter = new MacroFilter();
        this.macroFilter.loadMacroRulesFromMacroFolder(new File("macro"));

        if (scriptFilePath != null) {
            File f = new File(scriptFilePath);
            if (f.isFile()) {
                this.sourceScriptFile = f;
                f = f.getParentFile();
            }
            this.scriptFolder = resolveProjectScriptsRoot(f);
        }
        this.projectRoot = resolveProjectRoot();
    }

    @Override
    protected Integer doInBackground() throws Exception {
        validateInputs();
        String gameName = getGameName();
        outputFolder = new File("build_games", gameName + "_nextgen");
        FileUtils.deleteDirectory(outputFolder);
        FileUtils.forceMkdir(outputFolder);

        updateStatus("Preparing NextGen project layout...");
        stagedProjectFolder = new File(outputFolder, "staged_project");
        stageProject(stagedProjectFolder);
        setProgress(15);

        int index = 0;
        for (PackageTarget target : targets) {
            int base = 15 + (index * 75 / Math.max(1, targets.size()));
            int end = 15 + ((index + 1) * 75 / Math.max(1, targets.size()));
            packageTarget(target, gameName, base, end);
            index++;
        }

        uploadToItchIfRequested();
        setProgress(100);
        return 0;
    }

    private void validateInputs() throws IOException {
        if (scriptFolder == null || !scriptFolder.isDirectory()) {
            throw new IOException("Could not find the SceneMax scripts folder for packaging.");
        }
        if (projectRoot == null || !projectRoot.isDirectory()) {
            throw new IOException("Could not resolve the active SceneMax project root.");
        }
        File nextGenRoot = resolveNextGenProjectorRoot();
        if (!nextGenRoot.isDirectory()) {
            throw new IOException("SceneMax NextGen projector folder was not found: " + nextGenRoot.getAbsolutePath());
        }
        File cargo = resolveCargoExecutable();
        if (!cargo.isFile()) {
            throw new IOException("Cargo was not found. Configure rust_cargo_path or install Rust at: " + cargo.getAbsolutePath());
        }
    }

    private void packageTarget(PackageTarget target, String gameName, int progressBase, int progressEnd) throws IOException {
        updateStatus("Building " + targetLabel(target) + " Bevy runtime...");
        File runtimeBinary = buildRuntimeForTarget(target);
        setProgress(progressBase + Math.max(1, (progressEnd - progressBase) / 2));

        if (target == PackageTarget.WEB) {
            updateStatus("Creating Web package...");
            File webFolder = new File(outputFolder, "web");
            prepareWebPackage(runtimeBinary, webFolder, gameName);
            File webZip = createZip(webFolder, new File(outputFolder, gameName + "_nextgen_web.zip"));
            producedArtifacts.add(webZip);
            appendCompletionNote("Web target is experimental and requires the Bevy/WebAssembly runtime path to stay compatible with browser asset loading.");
            setProgress(progressEnd);
            return;
        }

        updateStatus("Creating " + targetLabel(target) + " package...");
        String folderName = platformFolderName(target);
        File packageFolder = new File(outputFolder, folderName);
        FileUtils.deleteDirectory(packageFolder);
        FileUtils.forceMkdir(packageFolder);
        copyStagedProjectInto(packageFolder);

        String executableName = target == PackageTarget.WINDOWS ? gameName + ".exe" : gameName;
        File packagedBinary = new File(packageFolder, executableName);
        FileUtils.copyFile(runtimeBinary, packagedBinary);
        packagedBinary.setExecutable(true, false);
        writeDesktopLaunchers(packageFolder, executableName, gameName, target);
        writePackageReadme(packageFolder, target);

        File artifact = target == PackageTarget.WINDOWS
                ? packagedBinary
                : createZip(packageFolder, new File(outputFolder, gameName + "_nextgen_" + folderName + ".zip"));
        producedArtifacts.add(artifact);
        setProgress(progressEnd);
    }

    private File buildRuntimeForTarget(PackageTarget target) throws IOException {
        File prebuilt = resolvePrebuiltRuntimeBinary(target);
        if (prebuilt != null && prebuilt.isFile()) {
            publish("Using shipped NextGen " + targetLabel(target) + " projector: " + prebuilt.getAbsolutePath());
            return prebuilt;
        }

        File nextGenRoot = resolveNextGenProjectorRoot();
        List<String> command = new ArrayList<>();
        command.add(resolveCargoExecutable().getAbsolutePath());
        command.add("build");
        command.add("-p");
        command.add("scenemax_projector_nextgen");
        command.add("--release");

        String rustTarget = rustTargetTriple(target);
        if (rustTarget.length() > 0) {
            command.add("--target");
            command.add(rustTarget);
        }

        try {
            runCommand(command, nextGenRoot, "cargo");
        } catch (IOException ex) {
            throw new IOException(buildTargetFailureMessage(target, ex), ex);
        }

        File binary = resolveBuiltRuntimeBinary(nextGenRoot, target, rustTarget);
        if (!binary.isFile()) {
            throw new IOException("Cargo finished but the expected " + targetLabel(target)
                    + " runtime binary was not found: " + binary.getAbsolutePath());
        }
        return binary;
    }

    private File resolvePrebuiltRuntimeBinary(PackageTarget target) {
        List<File> candidates = new ArrayList<>();
        String configuredPath = AppDB.getInstance().getParam("nextgen_projector_path");
        if (configuredPath != null && !configuredPath.isBlank()) {
            addPrebuiltRuntimeCandidates(candidates, new File(configuredPath.trim()), target);
        }
        File workingDir = new File(Util.getWorkingDir());
        addPrebuiltRuntimeCandidates(candidates, new File(workingDir, "runtime\\nextgen"), target);
        addPrebuiltRuntimeCandidates(candidates, new File(workingDir, "projectors\\nextgen"), target);
        addPrebuiltRuntimeCandidates(candidates, new File(workingDir, "bin\\nextgen"), target);
        addPrebuiltRuntimeCandidates(candidates, new File(workingDir, "scenemax_projector_nextgen"), target);
        for (File candidate : candidates) {
            if (candidate != null && candidate.isFile()) {
                return candidate;
            }
        }
        return null;
    }

    private void addPrebuiltRuntimeCandidates(List<File> candidates, File base, PackageTarget target) {
        if (candidates == null || base == null || base.getPath().isBlank()) {
            return;
        }
        if (base.isFile()) {
            if (target == PackageTarget.WINDOWS && base.getName().toLowerCase(Locale.ROOT).endsWith(".exe")) {
                candidates.add(base);
            }
            return;
        }
        String platform = platformFolderName(target);
        String exe = target == PackageTarget.WINDOWS ? "scenemax_projector_nextgen.exe" : "scenemax_projector_nextgen";
        candidates.add(new File(base, platform + "\\" + exe));
        candidates.add(new File(base, "bin\\" + platform + "\\" + exe));
        candidates.add(new File(base, "projectors\\" + platform + "\\" + exe));
        candidates.add(new File(base, exe));
        if (target == PackageTarget.WINDOWS) {
            candidates.add(new File(base, "target\\release\\scenemax_projector_nextgen.exe"));
            candidates.add(new File(base, "target\\debug\\scenemax_projector_nextgen.exe"));
            candidates.add(new File(base, "windows\\scenemax_projector_nextgen.exe"));
            candidates.add(new File(base, "win64\\scenemax_projector_nextgen.exe"));
        }
    }

    private String buildTargetFailureMessage(PackageTarget target, IOException ex) {
        String hint;
        switch (target) {
            case LINUX:
                hint = "Install the Rust target with `rustup target add x86_64-unknown-linux-gnu` and make sure a compatible Linux linker/toolchain is available.";
                break;
            case MAC_OSX:
                hint = "macOS packaging from Windows requires the Rust macOS target plus an Apple-compatible linker/SDK; in practice this is usually built on macOS CI.";
                break;
            case WEB:
                hint = "Install the WebAssembly target with `rustup target add wasm32-unknown-unknown` and install wasm-bindgen-cli for the browser glue step.";
                break;
            default:
                hint = "Make sure the Rust/C++ build tools are installed and the NextGen projector builds with Cargo.";
                break;
        }
        return "Failed to build the " + targetLabel(target) + " NextGen runtime.\n\n" + hint + "\n\n" + ex.getMessage();
    }

    private File resolveBuiltRuntimeBinary(File nextGenRoot, PackageTarget target, String rustTarget) {
        String exe = target == PackageTarget.WINDOWS ? "scenemax_projector_nextgen.exe" : "scenemax_projector_nextgen";
        if (rustTarget.length() == 0) {
            return new File(nextGenRoot, "target/release/" + exe);
        }
        return new File(nextGenRoot, "target/" + rustTarget + "/release/" + exe);
    }

    private String rustTargetTriple(PackageTarget target) {
        switch (target) {
            case WINDOWS:
                return isWindowsHost() ? "" : "x86_64-pc-windows-msvc";
            case LINUX:
                return "x86_64-unknown-linux-gnu";
            case MAC_OSX:
                return "x86_64-apple-darwin";
            case WEB:
                return "wasm32-unknown-unknown";
            default:
                return "";
        }
    }

    private void prepareWebPackage(File wasmBinary, File webFolder, String gameName) throws IOException {
        FileUtils.deleteDirectory(webFolder);
        FileUtils.forceMkdir(webFolder);
        copyStagedProjectInto(webFolder);

        File wasmBindgen = resolveWasmBindgenExecutable();
        if (wasmBindgen.isFile()) {
            List<String> command = new ArrayList<>();
            command.add(wasmBindgen.getAbsolutePath());
            command.add("--out-dir");
            command.add(webFolder.getAbsolutePath());
            command.add("--target");
            command.add("web");
            command.add(wasmBinary.getAbsolutePath());
            runCommand(command, webFolder, "wasm-bindgen");
        } else {
            FileUtils.copyFile(wasmBinary, new File(webFolder, "scenemax_projector_nextgen.wasm"));
            appendCompletionNote("wasm-bindgen was not found, so the Web package contains the raw WASM binary only. Install wasm-bindgen-cli for a runnable browser package.");
        }

        File index = new File(webFolder, "index.html");
        String moduleName = "scenemax_projector_nextgen";
        File js = new File(webFolder, moduleName + ".js");
        String script = js.isFile()
                ? "<script type=\"module\">\n  import init from './" + moduleName + ".js';\n  init();\n</script>\n"
                : "<p>Install wasm-bindgen-cli and repackage to generate the JavaScript loader.</p>\n";
        String html = "<!doctype html>\n<html>\n<head>\n"
                + "  <meta charset=\"utf-8\">\n"
                + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
                + "  <title>" + escapeHtml(gameName) + " - SceneMax NextGen</title>\n"
                + "  <style>html,body{margin:0;width:100%;height:100%;background:#111;color:#eee;font-family:sans-serif;}canvas{width:100%;height:100%;display:block;}</style>\n"
                + "</head>\n<body>\n" + script + "</body>\n</html>\n";
        FileUtils.write(index, html, StandardCharsets.UTF_8);
        writePackageReadme(webFolder, PackageTarget.WEB);
    }

    private void stageProject(File destination) throws IOException {
        FileUtils.deleteDirectory(destination);
        FileUtils.forceMkdir(destination);

        File resources = new File(projectRoot, "resources");
        if (resources.isDirectory()) {
            FileUtils.copyDirectory(resources, new File(destination, "resources"));
        }

        File running = new File(destination, "running");
        FileUtils.forceMkdir(running);
        String main = prg.replace("\r", "");
        String projectName = getGameName();
        if (!projectName.isBlank()) {
            main = "//$[project]=" + projectName + ";" + main;
        }
        SceneMaxProject activeProject = Util.getActiveProject();
        if (activeProject != null && activeProject.projectGuid != null && !activeProject.projectGuid.isBlank()) {
            main = "//$[project_guid]=" + activeProject.projectGuid.trim() + ";" + main;
        }
        ApplyMacroResults mainMacro = macroFilter.apply(main);
        FileUtils.write(new File(running, "main"), mainMacro.finalPrg, StandardCharsets.UTF_8);

        File[] scriptFiles = scriptFolder.listFiles();
        if (scriptFiles != null) {
            for (File file : scriptFiles) {
                writeScriptFile(file, running);
            }
        }
    }

    private void writeScriptFile(File source, File running) throws IOException {
        String relativePath = getScriptRelativePath(source);
        File target = new File(running, relativePath);
        if (source.isDirectory()) {
            FileUtils.forceMkdir(target);
            File[] children = source.listFiles();
            if (children != null) {
                for (File child : children) {
                    writeScriptFile(child, running);
                }
            }
            return;
        }
        if ("main".equals(relativePath.replace("\\", "/"))) {
            return;
        }
        String code = FileUtils.readFileToString(source, StandardCharsets.UTF_8);
        ApplyMacroResults mr = macroFilter.apply(code);
        FileUtils.forceMkdirParent(target);
        FileUtils.write(target, mr.finalPrg, StandardCharsets.UTF_8);
    }

    private void copyStagedProjectInto(File targetFolder) throws IOException {
        File resources = new File(stagedProjectFolder, "resources");
        if (resources.isDirectory()) {
            FileUtils.copyDirectory(resources, new File(targetFolder, "resources"));
        }
        File running = new File(stagedProjectFolder, "running");
        if (running.isDirectory()) {
            FileUtils.copyDirectory(running, new File(targetFolder, "running"));
        }
    }

    private void writeDesktopLaunchers(File packageFolder, String executableName, String gameName, PackageTarget target) throws IOException {
        if (target == PackageTarget.WINDOWS) {
            File bat = new File(packageFolder, "run_" + gameName + ".bat");
            String text = "@echo off\r\n"
                    + "cd /d \"%~dp0\"\r\n"
                    + "\"" + executableName + "\" run --script \"%~dp0running\\main\" --project-root \"%~dp0\"\r\n";
            FileUtils.write(bat, text, StandardCharsets.UTF_8);
        } else {
            File sh = new File(packageFolder, "run_" + gameName + ".sh");
            String text = "#!/usr/bin/env sh\n"
                    + "DIR=$(CDPATH= cd -- \"$(dirname -- \"$0\")\" && pwd)\n"
                    + "\"$DIR/" + executableName + "\" run --script \"$DIR/running/main\" --project-root \"$DIR\"\n";
            FileUtils.write(sh, text, StandardCharsets.UTF_8);
            sh.setExecutable(true, false);
        }
    }

    private void writePackageReadme(File packageFolder, PackageTarget target) throws IOException {
        String text = "SceneMax3D NextGen package\n"
                + "Target: " + targetLabel(target) + "\n\n"
                + "The package includes the Rust/Bevy projector plus staged SceneMax code under running/ and project assets under resources/.\n"
                + "Desktop packages can run the executable directly, or use the generated run script if command-line arguments are needed.\n";
        FileUtils.write(new File(packageFolder, "README-nextgen-package.txt"), text, StandardCharsets.UTF_8);
    }

    private File createZip(File sourceFolder, File zipFile) throws IOException {
        if (zipFile.exists()) {
            zipFile.delete();
        }
        try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)))) {
            zipFolder(sourceFolder, sourceFolder, out);
        }
        return zipFile;
    }

    private void zipFolder(File root, File current, ZipOutputStream out) throws IOException {
        File[] files = current.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            String relative = root.toPath().relativize(file.toPath()).toString().replace("\\", "/");
            if (file.isDirectory()) {
                if (!relative.endsWith("/")) {
                    relative += "/";
                }
                out.putNextEntry(new ZipEntry(relative));
                out.closeEntry();
                zipFolder(root, file, out);
            } else {
                out.putNextEntry(new ZipEntry(relative));
                Files.copy(file.toPath(), out);
                out.closeEntry();
            }
        }
    }

    private void uploadToItchIfRequested() throws IOException {
        if (!options.uploadToItch) {
            return;
        }
        updateStatus("Preparing itch.io upload...");
        List<String> butlerCommand = resolveButlerCommand();
        ensureButlerAvailable(butlerCommand);

        for (PackageTarget target : targets) {
            File artifact = artifactForTarget(target);
            if (artifact != null && artifact.exists()) {
                uploadArtifactToItch(
                        butlerCommand,
                        artifact,
                        channelForTarget(target),
                        targetLabel(target));
            }
        }
        appendCompletionNote("Updated itch.io page " + options.itchGameTarget + " using butler.");
        setProgress(98);
    }

    private File artifactForTarget(PackageTarget target) {
        String gameName = getGameName();
        switch (target) {
            case WINDOWS:
                return new File(new File(outputFolder, "windows"), gameName + ".exe");
            case LINUX:
                return new File(outputFolder, gameName + "_nextgen_linux.zip");
            case MAC_OSX:
                return new File(outputFolder, gameName + "_nextgen_macos.zip");
            case WEB:
                return new File(outputFolder, gameName + "_nextgen_web.zip");
            default:
                return null;
        }
    }

    private String channelForTarget(PackageTarget target) {
        switch (target) {
            case WINDOWS:
                return ItchIoHelper.defaultChannel("windows", options.itchWindowsChannel);
            case LINUX:
                return ItchIoHelper.defaultChannel("linux", options.itchLinuxChannel);
            case MAC_OSX:
                return ItchIoHelper.defaultChannel("macos", options.itchMacChannel);
            case WEB:
                return ItchIoHelper.defaultChannel("web", options.itchWebChannel);
            default:
                return "nextgen";
        }
    }

    private List<String> resolveButlerCommand() throws IOException {
        if (options.itchButlerPath.length() > 0) {
            File configured = new File(options.itchButlerPath);
            if (!configured.isFile()) {
                throw new IOException("Configured butler executable was not found: " + configured.getAbsolutePath());
            }
            return Collections.singletonList(configured.getAbsolutePath());
        }

        String bundledButler = ItchIoHelper.findBundledButlerExecutable();
        if (bundledButler != null && bundledButler.trim().length() > 0) {
            return Collections.singletonList(bundledButler);
        }

        return Collections.singletonList("butler");
    }

    private void ensureButlerAvailable(List<String> butlerCommand) throws IOException {
        List<String> versionCommand = new ArrayList<>(butlerCommand);
        versionCommand.add("version");
        try {
            runCommand(versionCommand, null, "butler");
        } catch (IOException e) {
            if (butlerCommand.size() == 1 && "butler".equalsIgnoreCase(butlerCommand.get(0))) {
                throw new IOException(ItchIoHelper.buildButlerInstallInstructions(), e);
            }
            throw new IOException("Unable to run butler from " + butlerCommand.get(0) + ". " + e.getMessage(), e);
        }
    }

    private void uploadArtifactToItch(List<String> butlerCommand, File artifact, String channel, String platformLabel) throws IOException {
        if (artifact == null || !artifact.exists()) {
            throw new IOException(platformLabel + " artifact was not found for itch.io upload: "
                    + (artifact == null ? "(missing)" : artifact.getAbsolutePath()));
        }

        updateStatus("Uploading " + platformLabel + " build to itch.io...");
        List<String> command = new ArrayList<>(butlerCommand);
        command.add("push");
        command.add(artifact.getAbsolutePath());
        command.add(options.itchGameTarget + ":" + channel);

        Map<String, String> env = Collections.emptyMap();
        if (options.itchApiKey.length() > 0) {
            env = new LinkedHashMap<>();
            env.put("BUTLER_API_KEY", options.itchApiKey);
        }

        runCommand(command, artifact.getParentFile(), "butler", env);
        appendCompletionNote("Uploaded " + platformLabel + " build to " + options.itchGameTarget + ":" + channel + ".");
    }

    private void runCommand(List<String> command, File directory, String label) throws IOException {
        runCommand(command, directory, label, Collections.emptyMap());
    }

    private void runCommand(List<String> command, File directory, String label, Map<String, String> environment) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (directory != null) {
            pb.directory(directory);
        }
        if (environment != null && !environment.isEmpty()) {
            pb.environment().putAll(environment);
        }
        pb.redirectErrorStream(true);
        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
                publish(label + ": " + line);
            }
        }
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException(label + " failed with exit code " + exitCode + ".\n" + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(label + " was interrupted.", e);
        }
    }

    private File resolveNextGenProjectorRoot() {
        String configuredPath = AppDB.getInstance().getParam("nextgen_projector_path");
        if (configuredPath != null && !configuredPath.isBlank()) {
            File configured = new File(configuredPath.trim());
            if (configured.isFile()) {
                File targetDir = configured.getParentFile();
                if (targetDir != null && ("debug".equalsIgnoreCase(targetDir.getName()) || "release".equalsIgnoreCase(targetDir.getName()))
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

    private File resolveCargoExecutable() {
        String configuredPath = AppDB.getInstance().getParam("rust_cargo_path");
        if (configuredPath != null && !configuredPath.isBlank()) {
            return new File(configuredPath.trim());
        }
        return new File(System.getProperty("user.home"), ".cargo/bin/cargo.exe");
    }

    private File resolveWasmBindgenExecutable() {
        String configuredPath = AppDB.getInstance().getParam("wasm_bindgen_path");
        if (configuredPath != null && !configuredPath.isBlank()) {
            return new File(configuredPath.trim());
        }
        File cargoBin = new File(System.getProperty("user.home"), ".cargo/bin");
        File exe = new File(cargoBin, isWindowsHost() ? "wasm-bindgen.exe" : "wasm-bindgen");
        return exe;
    }

    private File resolveProjectRoot() {
        SceneMaxProject activeProject = Util.getActiveProject();
        if (activeProject != null && activeProject.path != null && !activeProject.path.isBlank()) {
            File root = new File(activeProject.path);
            if (root.isDirectory()) {
                return root;
            }
        }
        File current = sourceScriptFile != null ? sourceScriptFile.getParentFile() : scriptFolder;
        while (current != null) {
            if (new File(current, "resources").isDirectory() && new File(current, "scripts").isDirectory()) {
                return current;
            }
            current = current.getParentFile();
        }
        if (scriptFolder != null && scriptFolder.getParentFile() != null
                && "scripts".equalsIgnoreCase(scriptFolder.getParentFile().getName())) {
            return scriptFolder.getParentFile().getParentFile();
        }
        return null;
    }

    private File resolveProjectScriptsRoot(File startDir) {
        if (startDir == null) {
            return null;
        }
        File current = startDir;
        while (current != null) {
            File parent = current.getParentFile();
            if (parent != null && "scripts".equalsIgnoreCase(parent.getName())) {
                return current;
            }
            current = parent;
        }
        return startDir;
    }

    private String getScriptRelativePath(File file) {
        String path = file.getAbsolutePath().replace(scriptFolder.getAbsolutePath(), "");
        path = path.replace("\\", "/");
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }

    private String getGameName() {
        SceneMaxProject activeProject = Util.getActiveProject();
        String name = activeProject != null && activeProject.name != null && !activeProject.name.isBlank()
                ? activeProject.name
                : projectRoot == null ? "scenemax_game" : projectRoot.getName();
        return sanitizeFileName(name);
    }

    private String sanitizeFileName(String value) {
        String cleaned = value == null ? "" : value.trim().replaceAll("[^A-Za-z0-9._-]+", "_");
        return cleaned.isBlank() ? "scenemax_game" : cleaned;
    }

    private String targetLabel(PackageTarget target) {
        switch (target) {
            case WINDOWS:
                return "Windows";
            case LINUX:
                return "Linux";
            case MAC_OSX:
                return "macOS";
            case WEB:
                return "Web";
            default:
                return target.name();
        }
    }

    private String platformFolderName(PackageTarget target) {
        switch (target) {
            case WINDOWS:
                return "windows";
            case LINUX:
                return "linux";
            case MAC_OSX:
                return "macos";
            default:
                return "web";
        }
    }

    private boolean isWindowsHost() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private String escapeHtml(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private void updateStatus(String status) {
        statusNote = status == null ? "" : status;
        publish(statusNote);
    }

    private void appendCompletionNote(String note) {
        if (note == null || note.trim().isEmpty()) {
            return;
        }
        if (completionNotes.length() > 0) {
            completionNotes.append("\r\n");
        }
        completionNotes.append(note.trim());
    }

    @Override
    protected void process(List<String> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        String last = chunks.get(chunks.size() - 1);
        if (last != null && !last.isBlank()) {
            statusNote = last;
        }
    }

    @Override
    public void done() {
        if (isCancelled()) {
            failureMessage = "NextGen packaging was canceled before it completed.";
            canceled.run();
            return;
        }
        try {
            get();
            finish.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failureMessage = "NextGen packaging was interrupted.";
            canceled.run();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            failureMessage = cause.getMessage() == null ? cause.toString() : cause.getMessage();
            canceled.run();
        }
    }

    public List<File> getProducedArtifacts() {
        return Collections.unmodifiableList(producedArtifacts);
    }

    public File getOutputFolder() {
        return outputFolder == null ? new File("build_games") : outputFolder;
    }

    public String getCompletionNotes() {
        return completionNotes.toString();
    }

    public String getStatusNote() {
        return statusNote;
    }

    public String getFailureMessage() {
        return failureMessage;
    }
}
