package com.scenemax.desktop;

import com.jdotsoft.jarloader.JarClassLoader;

public class MainAppLauncher {
    public static void main(String[] args) {
        // Must be set before any AWT/Swing class loading (see MainApp.main)
        System.setProperty("sun.java2d.uiScale", "1");
        JarClassLoader jcl = new JarClassLoader();
        try {
            jcl.invokeMain(MainApp.class.getName(), args);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
