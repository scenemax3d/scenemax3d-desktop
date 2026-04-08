package com.scenemax.desktop;

import com.scenemaxeng.compiler.ApplyMacroResults;
import com.scenemaxeng.compiler.MacroFilter;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import org.apache.commons.io.FileUtils;
//import org.omg.IOP.Encoding;

import javax.swing.*;
import java.io.*;
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

            this.scriptFolder = f;
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
            if(exitCode!=0) {
                String msg = "";
                try {
                    msg = FileUtils.readFileToString(new File("log"), StandardCharsets.UTF_8);
                    JOptionPane.showMessageDialog(null,msg);
                } catch (IOException e) {
                    e.printStackTrace();
                };

            }
            finish.run();
        }
    }

    private void saveScript() {

        prg=prg.replaceAll("\r","");
        if(scriptFolder!=null) {
            prg = "//$[project]=" + scriptFolder.getParentFile().getParentFile().getName() + ";" + prg;
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

            command.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005");
            command.add("-Dname=SceneMax3dProjector");
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
