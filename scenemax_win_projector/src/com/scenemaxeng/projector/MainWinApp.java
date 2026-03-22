package com.scenemaxeng.projector;

import com.scenemaxeng.common.types.*;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeCanvasContext;
import org.apache.commons.io.FileUtils;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainWinApp implements IAppObserver {

    private final int appType;
    public IAppObserver parent;
    private SceneMaxApp sceneMaxApp;
    private int customWidth = 0;
    private int customHeight = 0;
    private String projectName = null;

    public MainWinApp(File entryScriptFile, String prg, boolean showCodeChangeButton) {

        String workingFolder=null;
        String entryScriptFileName=null;

        if(entryScriptFile==null) {
            workingFolder= Paths.get("./running").toAbsolutePath().normalize().toString();
        } else {
            workingFolder = entryScriptFile.getParent();
            entryScriptFileName=entryScriptFile.getName();
        }

        this.appType=showCodeChangeButton?SceneMaxApp.HOST_APP_WINDOWS_ALLOW_CODE_CHANGE_BUTTON:SceneMaxApp.HOST_APP_WINDOWS;
        sceneMaxApp = new SceneMaxApp(appType);
        sceneMaxApp.setObserver(this);
        sceneMaxApp.setPauseOnLostFocus(false);
        sceneMaxApp.setWorkingFolder(workingFolder);
        sceneMaxApp.setEntryScriptFileName(entryScriptFileName);
        AppSettings settings = new AppSettings(true);
        settings.setGammaCorrection(false);
        settings.setSamples(4); // anti-aliasing
        settings.setVSync(true);

        prg=setProjectContext(prg);
        if(this.projectName!=null) {
            sceneMaxApp.setProjectName(this.projectName);
        }

        prg = setCanvasSize(settings,prg);
        prg = setWindowMode(settings,prg);

        settings.setTitle("");

        sceneMaxApp.setSettings(settings);
        sceneMaxApp.createCanvas(); // create canvas!

        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
        JmeCanvasContext ctx = (JmeCanvasContext) sceneMaxApp.getContext();
        ctx.setSystemListener(sceneMaxApp);
        ctx.getCanvas().setLocation(dim.width/2-this.customWidth/2, dim.height/2-this.customHeight/2);

        sceneMaxApp.start();

        if (prg == null || prg.length()==0) {
            prg = loadAppScript();
        }

        final String finalPrg = prg;
        runScript(finalPrg);

    }

    private String setProjectContext(String prg) {

        Pattern p = Pattern.compile("//\\$\\[project\\]=(.+?);", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        Matcher m = p.matcher(prg);

        while(m.find()) {
            this.projectName = m.group(1);
            prg=prg.replaceFirst("//\\$\\[project\\]=(.+?);","");

        }

        return prg;
    }

    private String setWindowMode(AppSettings settings, String prg) {

        String pat="screen\\.mode\\s+full";
        Pattern p = Pattern.compile(pat,Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        Matcher m = p.matcher(prg);

        if(m.find()) {
            settings.setFullscreen(true);
            settings.setFrequency(60);
        }

        prg=prg.replaceFirst(pat,"");

        return prg;

    }

    private String setCanvasSize(AppSettings settings, String prg) {

        String pat="^canvas\\.size\\s+((?<val1>\\d+),(?<val2>\\d+))";
        Pattern p = Pattern.compile(pat,Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        Matcher m = p.matcher(prg);
        int width = 1600;
        int height = 900;
        if(m.find()) {
            String w = m.group("val1");
            String h = m.group("val2");
            width=Integer.parseInt(w);
            height=Integer.parseInt(h);
            prg = m.replaceFirst("");
        }

        settings.setWidth(width);
        settings.setHeight(height);
        this.customWidth = width;
        this.customHeight = height;

        return prg;
    }


    private String loadAppScript() {

        String program = "";
        String path = "main";
        File folder = new File("running");
        File f=new File(folder, path);

        if(f.exists() && f.isFile()) {
            try {
                program= FileUtils.readFileToString(f,StandardCharsets.UTF_8);
                System.out.println("main program: "+program);
            } catch(Exception ex) {
                ex.printStackTrace();
            }

        } else {
            System.out.println("script file: "+f.getAbsolutePath()+" not found.");
        }

        return program;
    }

    public static void main(String[] args){
        MainWinApp app = new MainWinApp(null,null,false);

    }


    @Override
    public void init() {

    }

    @Override
    public void showScriptEditor() {

    }

    @Override
    public void onEndCode(final List<String> errors) {

    }

    @Override
    public void onStartCode() {
        if(parent!=null) {
            parent.onStartCode();
        }
    }

    @Override
    public void message(final int msgType) {

        if (!SwingUtilities.isEventDispatchThread()) {

            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    message(msgType);
                }
            });
            return;
        }

        if(parent!=null) {

            parent.message(msgType);
        }


    }

    @Override
    public void message(int msgType, Object content) {
        parent.message(msgType,content);
    }

    public void runScript(final String script) {

        sceneMaxApp.runOnGlThread(new Runnable() {
            @Override
            public void run() {
                sceneMaxApp.stopScript();
                sceneMaxApp.run(script);
            }
        });


    }

    public CanvasRect getRect() {
        return sceneMaxApp.getCanvasRect();
    }



}
