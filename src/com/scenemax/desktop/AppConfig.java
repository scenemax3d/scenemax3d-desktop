package com.scenemax.desktop;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Loads application configuration from config.properties.
 * Credentials and sensitive defaults are kept out of source code.
 */
public class AppConfig {

    private static final Properties props = new Properties();

    static {
        File configFile = new File("config.properties");
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
            } catch (IOException e) {
                System.err.println("Warning: could not load config.properties: " + e.getMessage());
            }
        } else {
            System.err.println("Warning: config.properties not found. Using empty defaults.");
        }
    }

    public static String get(String key) {
        return props.getProperty(key, "");
    }

    public static String get(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }
}
