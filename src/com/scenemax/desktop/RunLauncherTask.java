package com.scenemax.desktop;

import com.scenemaxeng.compiler.ApplyMacroResults;
import com.scenemaxeng.compiler.MacroFilter;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import org.apache.commons.io.FileUtils;
//import org.omg.IOP.Encoding;

import javax.swing.*;
import java.io.*;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RunLauncherTask extends SwingWorker<Integer, String> {

    public enum RuntimeTarget {
        CLASSIC,
        NEXTGEN
    }

    private final String launcherName;
    private final RuntimeTarget runtimeTarget;
    private final MacroFilter macroFilter;
    private File scriptFolder=null;
    private File sourceScriptFile = null;
    private String sourceScriptRelativePath = null;
    private File runningFolder = null;
    private String prg;
    private Runnable finish;
    private Consumer<String> launchStatusConsumer;
    private boolean waitForLauncherCreation = false;
    private int exitCode;

    public RunLauncherTask(String scriptFilePath, String prg, Runnable finish)  {
        this(scriptFilePath, prg, finish, RuntimeTarget.CLASSIC);
    }

    public RunLauncherTask(String scriptFilePath, String prg, Runnable finish, RuntimeTarget runtimeTarget)  {
        this(scriptFilePath, prg, finish, runtimeTarget, null);
    }

    public RunLauncherTask(String scriptFilePath, String prg, Runnable finish, RuntimeTarget runtimeTarget,
                           Consumer<String> launchStatusConsumer)  {

        String ver = Util.getAppVersion();
        this.launcherName = "launcher"+ver+".jar";
        this.runtimeTarget = runtimeTarget == null ? RuntimeTarget.CLASSIC : runtimeTarget;
        this.launchStatusConsumer = launchStatusConsumer;

        this.macroFilter = new MacroFilter();
        this.macroFilter.loadMacroRulesFromMacroFolder(new File("macro"));

        this.prg=prg;
        this.finish=finish;

        if(scriptFilePath!=null) {
            File f = new File(scriptFilePath);
            if(f.isFile()) {
                this.sourceScriptFile = f;
                f=f.getParentFile();
            }

            // If the launched file lives in a sub-folder, walk up to the
            // project's scripts root so all sibling files (e.g. .smui docs)
            // get staged into the running/ folder alongside the entry script.
            this.scriptFolder = resolveProjectScriptsRoot(f);
            this.runningFolder = new File("running");
            try {
                if (this.runningFolder.exists()) {
                    FileUtils.deleteDirectory(this.runningFolder);
                }
                FileUtils.forceMkdir(this.runningFolder);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    public void done() {
        if(!waitForLauncherCreation) {
            int workerResult = 0;
            try {
                workerResult = get();
            } catch (Exception e) {
                workerResult = -1;
                try {
                    FileUtils.writeStringToFile(new File("log"), formatExceptionMessage(e), StandardCharsets.UTF_8);
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
            if(exitCode!=0 || workerResult!=0) {
                String msg = "";
                try {
                    msg = FileUtils.readFileToString(new File("log"), StandardCharsets.UTF_8);
                    Util.showScrollableMessageDialog(null, msg, "Run Error", JOptionPane.ERROR_MESSAGE);
                } catch (IOException e) {
                    e.printStackTrace();
                };

            }
            finish.run();
        }
    }

    private void saveScript() {

        prg=prg.replaceAll("\r","");
        String projectName = resolveProjectName();
        if(projectName != null && !projectName.isBlank()) {
            prg = "//$[project]=" + projectName + ";" + prg;
        }
        String projectGuid = resolveProjectGuid(projectName);
        if(projectGuid != null && !projectGuid.isBlank()) {
            prg = "//$[project_guid]=" + projectGuid + ";" + prg;
        }
        if(sourceScriptRelativePath!=null && !sourceScriptRelativePath.isBlank()) {
            prg = "//$[source_rel]=" + sourceScriptRelativePath + ";" + prg;
        }

        String path="main";
        try {
            ApplyMacroResults mr = this.macroFilter.apply(prg);
            FileUtils.write(new File(this.runningFolder, path),mr.finalPrg, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private String resolveProjectName() {
        File current = sourceScriptFile != null ? sourceScriptFile.getParentFile() : scriptFolder;
        while (current != null) {
            File resourcesFolder = new File(current, "resources");
            File scriptsFolder = new File(current, "scripts");
            if (resourcesFolder.isDirectory() && scriptsFolder.isDirectory()) {
                return current.getName();
            }
            current = current.getParentFile();
        }
        if (scriptFolder != null && scriptFolder.getParentFile() != null && scriptFolder.getParentFile().getParentFile() != null) {
            return scriptFolder.getParentFile().getParentFile().getName();
        }
        return null;
    }

    private String resolveProjectGuid(String projectName) {
        SceneMaxProject activeProject = Util.getActiveProject();
        if (activeProject == null) {
            return "";
        }
        if (projectName != null && !projectName.isBlank() && !matchesActiveProjectName(projectName, activeProject)) {
            return "";
        }
        if (activeProject.projectGuid == null || activeProject.projectGuid.trim().isEmpty()) {
            activeProject.projectGuid = java.util.UUID.randomUUID().toString();
            Util.saveProjectSettings(activeProject);
        }
        return activeProject.projectGuid.trim();
    }

    private boolean matchesActiveProjectName(String projectName, SceneMaxProject activeProject) {
        if (activeProject == null || projectName == null || projectName.isBlank()) {
            return false;
        }
        if (projectName.equals(activeProject.name)) {
            return true;
        }
        if (activeProject.path == null || activeProject.path.isBlank()) {
            return false;
        }
        return projectName.equals(new File(activeProject.path).getName());
    }


    @Override
    protected Integer doInBackground() throws Exception {

        if (sourceScriptFile != null) {
            sourceScriptRelativePath = getScriptRelativePath(sourceScriptFile);
        }
        publishLaunchStatus("Preparing SceneMax program...");
        saveScript();

        if(scriptFolder!=null) {

            try {
                publishLaunchStatus("Checking scripts and staging project files...");
                String parserPath = sourceScriptFile != null ? sourceScriptFile.getAbsolutePath() : scriptFolder.getAbsolutePath();
                SceneMaxLanguageParser parser = new SceneMaxLanguageParser(null, parserPath);
                SceneMaxLanguageParser.setMacroFilter(this.macroFilter);
                String cleanCode = getCleanCode(this.prg);
                parser.parse(cleanCode);

                File[] scriptFolderFiles = scriptFolder.listFiles();
                if (scriptFolderFiles != null) {
                    for (File f : scriptFolderFiles) {
                        writeScriptFile(f);
//                        String name = f.getName();
//                        if (SceneMaxLanguageParser.filesUsed.contains(name)) {
//                            String code = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
//                            ApplyMacroResults mr = this.macroFilter.apply(code);
//                            FileUtils.write(new File(name), mr.finalPrg, StandardCharsets.UTF_8);
//                        }
                    }
                }

            } catch (Exception e) {
              e.printStackTrace();
              exitCode = -1;
              FileUtils.writeStringToFile(new File("log"), formatExceptionMessage(e), StandardCharsets.UTF_8);
              return -1;
            }

            try {
                publishLaunchStatus("Checking Java extensions...");
                JavaExtensionBuildTool.BuildResult extensions = JavaExtensionBuildTool.buildExtensions(
                        scriptFolder,
                        new File(this.runningFolder, JavaExtensionBuildTool.EXTENSIONS_FOLDER_NAME),
                        System.out::println);
                if (extensions.hasExtensions()) {
                    System.out.println("Built Java extensions: " + extensions.extensions.size());
                }
            } catch (IOException e) {
                e.printStackTrace();
                exitCode = -1;
                String logText = formatExceptionMessage(e);
                FileUtils.writeStringToFile(new File("log"), logText, StandardCharsets.UTF_8);
                if (this.runningFolder != null) {
                    File javaLog = new File(
                            new File(this.runningFolder, JavaExtensionBuildTool.EXTENSIONS_FOLDER_NAME),
                            JavaExtensionBuildTool.COMPILE_LOG_NAME);
                    FileUtils.writeStringToFile(javaLog, logText, StandardCharsets.UTF_8);
                }
                return -1;
            }
        }

        if (runtimeTarget == RuntimeTarget.NEXTGEN) {
            publishLaunchStatus("Preparing Rust/Bevy projector...");
            runNextGenProjector();
            return exitCode;
        }

        // make sure there is a launcher for the current version exists in the main folder
        File f = new File(launcherName);
        if(!f.exists()) {
            waitForLauncherCreation = true; // do not callback finish until launcher is created and run

            new PrepareLauncherTask(scriptFolder, prg, new Runnable() {
                @Override
                public void run() {
                    runLauncher();
                    finish.run(); // manually call finish
                }
            }).execute();

        } else {
            runLauncher();
        }

        return 0;
    }

    private static String formatExceptionMessage(Throwable e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.toString() : message;
    }

    private void writeScriptFile(File f) {

        try {
            String name = f.getName();
            String relativePath = getScriptRelativePath(f);
            if (f.isDirectory()) {
                FileUtils.forceMkdir(new File(this.runningFolder, relativePath));
                File[] files = f.listFiles();
                if (files != null) {
                    for (File file : files) {
                        writeScriptFile(file);
                    }
                }

                return;
            }
            //if (isFileUsed(SceneMaxLanguageParser.filesUsed, f)) {
            if (!relativePath.equals("/main")) { // root main file is already copied with project meta-data
                String code = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
                ApplyMacroResults mr = this.macroFilter.apply(code);
                FileUtils.write(new File(this.runningFolder, relativePath), mr.finalPrg, StandardCharsets.UTF_8);
            }
            //}

        } catch (IOException e) {
            e.printStackTrace();
        }
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
        String path = file.getAbsolutePath();
        path = path.replace(scriptFolder.getAbsolutePath(), "");
        path = path.replace("\\", "/");

        return path;
    }

    private boolean isFileUsed(List<String> filesUsed, File file) {
        String path = this.getScriptRelativePath(file);
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        for (String usedPath : filesUsed) {
            if (usedPath.startsWith("/")) {
                usedPath = usedPath.substring(1);
            }
            if (path.equals(usedPath)) {
                return true;
            }

        }

        return false;
    }


    private String getCleanCode(String prg) {

        Pattern p = Pattern.compile("//\\$\\[project\\]=(.+?);", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        Matcher m = p.matcher(prg);

        while(m.find()) {
            prg=prg.replaceFirst("//\\$\\[project\\]=(.+?);","");
        }

        p = Pattern.compile("//\\$\\[source\\]=(.+?);", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        m = p.matcher(prg);
        while(m.find()) {
            prg=prg.replaceFirst("//\\$\\[source\\]=(.+?);","");
        }

        p = Pattern.compile("//\\$\\[source_rel\\]=(.+?);", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        m = p.matcher(prg);
        while(m.find()) {
            prg=prg.replaceFirst("//\\$\\[source_rel\\]=(.+?);","");
        }

        p = Pattern.compile("//\\$\\[disable_audio\\]=(.+?);", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        m = p.matcher(prg);
        while(m.find()) {
            prg=prg.replaceFirst("//\\$\\[disable_audio\\]=(.+?);","");
        }

        return prg;
    }

    private void runLauncher() {

        try {

            List<String> command = new ArrayList<>();
            command.add("java");
            command.add("-XX:MaxDirectMemorySize=1024m");

            String jvmArch = AppDB.getInstance().getParam("projector_jvm_arch");
            if(jvmArch!=null && (jvmArch.equals("64") || jvmArch.equals("32"))) {
                command.add("-d"+jvmArch);
            }

            command.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=" + nextAvailableDebugPort());
            command.add("-Dname=SceneMax3dProjector");
            addLocalMultiplayerProperties(command);
            command.add("-jar");
            command.add(launcherName);

            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command(command);
            File log = new File("log");
            if(log.exists()) {
                log.delete();
            }
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(log));
            Process process = processBuilder.start();

            StreamGobbler sg = new StreamGobbler(process.getInputStream(),System.out::println);
            Executors.newSingleThreadExecutor().submit(sg);
            exitCode = process.waitFor();
            System.out.printf("Program ended with exitCode %d", exitCode);
            cleanScriptFiles();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void runNextGenProjector() {

        try {
            publishLaunchStatus("Resolving NextGen projector...");
            File nextGenRoot = resolveNextGenProjectorRoot();
            File debugExe = new File(nextGenRoot, "target/debug/scenemax_projector_nextgen.exe");
            File releaseExe = new File(nextGenRoot, "target/release/scenemax_projector_nextgen.exe");
            File builtExe = newestExistingFile(debugExe, releaseExe);
            File stagedMain = new File(this.runningFolder, "main");
            File projectRoot = resolveProjectRoot();

            List<String> command = new ArrayList<>();
            File processDirectory;
            File cargoExe = resolveCargoExecutable();
            boolean builtExeHasNativeEffects = builtExe != null && hasNextGenNativeEffectsMarker(builtExe);
            boolean builtExeIsCurrent = builtExe != null
                    && builtExeHasNativeEffects
                    && !isNextGenExecutableStale(nextGenRoot, debugExe, releaseExe);
            boolean useCargo = !builtExeIsCurrent && cargoExe.isFile();
            if (builtExeIsCurrent) {
                command.add(builtExe.getAbsolutePath());
                processDirectory = nextGenRoot;
                publishLaunchStatus("Starting Bevy projector...");
            } else if (useCargo) {
                command.add(cargoExe.getAbsolutePath());
                command.add("run");
                command.add("-p");
                command.add("scenemax_projector_nextgen");
                addNextGenNativeFeatureArgs(command);
                command.add("--");
                processDirectory = nextGenRoot;
                publishLaunchStatus(builtExe == null
                        ? "Building Rust/Bevy projector, then opening the game..."
                        : builtExeHasNativeEffects
                        ? "Rust/Bevy projector changed. Rebuilding, then opening the game..."
                        : "Building Rust/Bevy projector with native Effekseer, then opening the game...");
            } else if (builtExe != null) {
                command.add(builtExe.getAbsolutePath());
                processDirectory = nextGenRoot;
                publishLaunchStatus(builtExeHasNativeEffects
                        ? "Cargo was not found, starting the existing Bevy projector..."
                        : "Cargo was not found, starting the existing Bevy projector without native Effekseer...");
            } else {
                throw new IOException("SceneMax NextGen projector is not built and Cargo was not found at: "
                        + cargoExe.getAbsolutePath());
            }

            command.add("run");
            command.add("--script");
            command.add(stagedMain.getAbsolutePath());
            if (projectRoot != null) {
                command.add("--project-root");
                command.add(projectRoot.getAbsolutePath());
            }
            command.add("--width");
            command.add("1600");
            command.add("--height");
            command.add("900");

            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command(command);
            processBuilder.directory(processDirectory);
            populateNextGenLaunchEnvironment(processBuilder, nextGenRoot, debugExe, releaseExe, builtExe,
                    cargoExe, command, builtExeIsCurrent, useCargo);
            if (useCargo) {
                processBuilder.environment().put("SCENEMAX_EFFEKSEER_NATIVE_BUILD", "1");
            }
            File log = new File("log");
            if(log.exists()) {
                log.delete();
            }
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            final boolean[] runtimeStarted = { !useCargo };
            if (!useCargo) {
                publishLaunchStatus("Bevy projector process started. Opening game window...");
            }
            StreamGobbler sg = new StreamGobbler(process.getInputStream(), line -> {
                System.out.println(line);
                appendLogLine(log, line);
                if (useCargo) {
                    updateNextGenCargoStatus(line, runtimeStarted);
                }
            });
            Executors.newSingleThreadExecutor().submit(sg);
            exitCode = process.waitFor();
            System.out.printf("NextGen projector ended with exitCode %d", exitCode);
        } catch (Exception e) {
            e.printStackTrace();
            exitCode = -1;
            try {
                FileUtils.writeStringToFile(new File("log"), formatExceptionMessage(e), StandardCharsets.UTF_8);
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }

    }

    private void updateNextGenCargoStatus(String line, boolean[] runtimeStarted) {
        if (line == null) {
            return;
        }
        String trimmed = line.trim();
        String lower = trimmed.toLowerCase();
        if (!runtimeStarted[0] && lower.contains("running") && lower.contains("scenemax_projector_nextgen")) {
            runtimeStarted[0] = true;
            publishLaunchStatus("Bevy projector process started. Opening game window...");
            return;
        }
        if (trimmed.startsWith("Compiling ")) {
            publishLaunchStatus("Compiling " + trimmed.substring("Compiling ".length()).trim() + "...");
        } else if (trimmed.startsWith("Checking ")) {
            publishLaunchStatus("Checking " + trimmed.substring("Checking ".length()).trim() + "...");
        } else if (trimmed.startsWith("Finished ")) {
            publishLaunchStatus("Rust/Bevy build finished. Opening Bevy window...");
        } else if (trimmed.startsWith("Blocking waiting for file lock")) {
            publishLaunchStatus("Waiting for Cargo build lock...");
        }
    }

    private void appendLogLine(File log, String line) {
        try {
            FileUtils.writeStringToFile(log, line + System.lineSeparator(), StandardCharsets.UTF_8, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void publishLaunchStatus(String message) {
        if (launchStatusConsumer == null || message == null || message.isBlank()) {
            return;
        }
        SwingUtilities.invokeLater(() -> launchStatusConsumer.accept(message));
    }

    private void populateNextGenLaunchEnvironment(ProcessBuilder processBuilder,
                                                  File nextGenRoot,
                                                  File debugExe,
                                                  File releaseExe,
                                                  File builtExe,
                                                  File cargoExe,
                                                  List<String> command,
                                                  boolean builtExeIsCurrent,
                                                  boolean useCargo) {
        processBuilder.environment().put("SCENEMAX_NEXTGEN_LAUNCH_ROOT", safePath(nextGenRoot));
        processBuilder.environment().put("SCENEMAX_NEXTGEN_LAUNCH_DEBUG_EXE", safePath(debugExe));
        processBuilder.environment().put("SCENEMAX_NEXTGEN_LAUNCH_RELEASE_EXE", safePath(releaseExe));
        processBuilder.environment().put("SCENEMAX_NEXTGEN_LAUNCH_BUILT_EXE", safePath(builtExe));
        processBuilder.environment().put("SCENEMAX_NEXTGEN_LAUNCH_CARGO_EXE", safePath(cargoExe));
        processBuilder.environment().put("SCENEMAX_NEXTGEN_LAUNCH_SELECTED",
                command == null || command.isEmpty() ? "" : command.get(0));
        processBuilder.environment().put("SCENEMAX_NEXTGEN_LAUNCH_USE_CARGO", Boolean.toString(useCargo));
        processBuilder.environment().put("SCENEMAX_NEXTGEN_LAUNCH_BUILT_CURRENT", Boolean.toString(builtExeIsCurrent));
    }

    private String safePath(File file) {
        return file == null ? "" : file.getAbsolutePath();
    }

    private File resolveNextGenProjectorRoot() {
        String configuredPath = AppDB.getInstance().getParam("nextgen_projector_path");
        if (configuredPath != null && !configuredPath.isBlank()) {
            File configured = new File(configuredPath.trim());
            if (configured.isFile()) {
                File targetDir = configured.getParentFile();
                if (targetDir != null && "debug".equalsIgnoreCase(targetDir.getName())
                        && targetDir.getParentFile() != null
                        && targetDir.getParentFile().getParentFile() != null) {
                    return targetDir.getParentFile().getParentFile();
                }
                if (targetDir != null && "release".equalsIgnoreCase(targetDir.getName())
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

    private void addNextGenNativeFeatureArgs(List<String> command) {
        command.add("--features");
        command.add("effekseer_native");
    }

    private boolean isNextGenExecutableStale(File nextGenRoot, File debugExe, File releaseExe) {
        File newestExe = newestExistingFile(debugExe, releaseExe);
        if (newestExe == null) {
            return true;
        }
        long newestSource = newestRustSourceTimestamp(nextGenRoot);
        return newestSource > newestExe.lastModified();
    }

    private boolean hasNextGenNativeEffectsMarker(File exe) {
        if (exe == null || exe.getParentFile() == null) {
            return false;
        }
        return new File(exe.getParentFile(), "scenemax_projector_nextgen.effekseer_native").isFile();
    }

    private File newestExistingFile(File... files) {
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

    private long newestRustSourceTimestamp(File nextGenRoot) {
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

    private File resolveCargoExecutable() {
        String configuredPath = AppDB.getInstance().getParam("rust_cargo_path");
        if (configuredPath != null && !configuredPath.isBlank()) {
            return new File(configuredPath.trim());
        }
        return new File(System.getProperty("user.home"), ".cargo/bin/cargo.exe");
    }

    private File resolveProjectRoot() {
        SceneMaxProject activeProject = Util.getActiveProject();
        if (activeProject != null && activeProject.path != null && !activeProject.path.isBlank()) {
            File projectRoot = new File(activeProject.path);
            if (projectRoot.isDirectory()) {
                return projectRoot;
            }
        }

        File current = sourceScriptFile != null ? sourceScriptFile.getParentFile() : scriptFolder;
        while (current != null) {
            File resourcesFolder = new File(current, "resources");
            File scriptsFolder = new File(current, "scripts");
            if (resourcesFolder.isDirectory() && scriptsFolder.isDirectory()) {
                return current;
            }
            current = current.getParentFile();
        }

        if (scriptFolder != null
                && scriptFolder.getParentFile() != null
                && "scripts".equalsIgnoreCase(scriptFolder.getParentFile().getName())
                && scriptFolder.getParentFile().getParentFile() != null) {
            return scriptFolder.getParentFile().getParentFile();
        }
        return null;
    }

    private int nextAvailableDebugPort() {
        for (int port = 5005; port < 5025; port++) {
            try (ServerSocket ignored = new ServerSocket(port)) {
                return port;
            } catch (IOException ignored) {
            }
        }
        return 5005;
    }

    private void addLocalMultiplayerProperties(List<String> command) {
        if (!programUsesMultiplayer()) {
            return;
        }

        SceneMaxProject project = Util.getActiveProject();
        String serverIp = project != null && project.multiplayerServerIp != null && !project.multiplayerServerIp.isBlank()
                ? project.multiplayerServerIp.trim()
                : "127.0.0.1";
        int serverPort = project != null && project.multiplayerServerPort > 0
                ? project.multiplayerServerPort
                : 9001;
        String sessionName = project != null && project.name != null && !project.name.isBlank()
                ? project.name.trim()
                : "local";

        command.add("-Dscenemax.multiplayer.server=" + serverIp);
        command.add("-Dscenemax.multiplayer.port=" + serverPort);
        if (project != null && project.multiplayerPassword != null && !project.multiplayerPassword.isBlank()) {
            command.add("-Dscenemax.multiplayer.password=" + project.multiplayerPassword);
        }
        String projectGuid = project != null && project.projectGuid != null ? project.projectGuid.trim() : "";
        if (!projectGuid.isBlank()) {
            command.add("-Dscenemax.multiplayer.projectGuid=" + projectGuid);
        }
        command.add("-Dscenemax.multiplayer.sessionId=1000");
        command.add("-Dscenemax.multiplayer.createSession=false");
        command.add("-Dscenemax.multiplayer.sessionName=" + sessionName);
        command.add("-Dscenemax.multiplayer.scene=main");
        command.add("-Dscenemax.multiplayer.player=" + localPlayerName());
    }

    private boolean programUsesMultiplayer() {
        return MultiplayerSourceDetector.usesMultiplayerInReachableScripts(scriptFolder, prg);
    }

    private String localPlayerName() {
        String userName = System.getProperty("user.name", "player");
        if (userName == null || userName.trim().isEmpty()) {
            userName = "player";
        }
        userName = userName.trim().replaceAll("\\s+", "_");
        return userName + "_" + (System.currentTimeMillis() % 100000);
    }

    private void cleanScriptFiles() {

        if (scriptFolder != null) {
            File[] scriptFolderFiles = scriptFolder.listFiles();
            if (scriptFolderFiles != null) {
                for (File f : scriptFolderFiles) {
                    File f1 = new File(f.getName());
                    if (f1.exists()) {
                        f1.delete();
                    }
                }
            }
        }
    }





}
