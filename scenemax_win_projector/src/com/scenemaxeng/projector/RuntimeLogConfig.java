package com.scenemaxeng.projector;

import java.util.Locale;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

final class RuntimeLogConfig {

    private static final String PROPERTY_JAVA_LOG_LEVEL = "scenemax.runtime.javaLogLevel";

    private RuntimeLogConfig() {
    }

    static void configureRuntimeLogging() {
        Level level = parseLevel(System.getProperty(PROPERTY_JAVA_LOG_LEVEL, "SEVERE"));
        Logger root = LogManager.getLogManager().getLogger("");
        if (root != null) {
            root.setLevel(level);
            for (Handler handler : root.getHandlers()) {
                handler.setLevel(level);
            }
        }
        Logger.getLogger("com.jme3").setLevel(level);
        Logger.getLogger("com.jme3.scene.plugins.gltf").setLevel(level);
    }

    private static Level parseLevel(String configured) {
        if (configured == null || configured.trim().isEmpty()) {
            return Level.SEVERE;
        }
        try {
            return Level.parse(configured.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return Level.SEVERE;
        }
    }
}
