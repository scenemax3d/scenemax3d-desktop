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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RunLauncherTask extends SwingWorker<Integer, String> {

    private final String launcherName;
    private final MacroFilter macroFilter;
    private File scriptFolder=null;
    private File sourceScriptFile = null;
    private String sourceScriptRelativePath = null;
    private File runningFolder = null;
    private String prg;
    private Runnable finish;
    private boolean waitForLauncherCreation = false;
    private int exitCode;

    public RunLauncherTask(String scriptFilePath, String prg, Runnable finish)  {

        String ver = Util.getAppVersion();
        this.launcherName = "launcher"+ver+".jar";

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
        saveScript();

        if(scriptFolder!=null) {

            try {
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
