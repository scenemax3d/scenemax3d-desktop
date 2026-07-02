package com.scenemaxeng.projector;

import com.jme3.app.state.AppState;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

final class JavaExtensionRuntimeLoader {
    private static final String EXTENSIONS_FOLDER = "java-extensions";
    private static final String EXTENSIONS_INDEX = "extensions.json";
    private static final String RESOURCE_INDEX = EXTENSIONS_FOLDER + "/" + EXTENSIONS_INDEX;

    private JavaExtensionRuntimeLoader() {
    }

    static boolean attachExtension(SceneMaxApp app, SceneMaxScope scope, String appStateName, Logger logger) {
        try {
            ExtensionIndex index = loadIndex(app);
            if (index == null || index.extensions.isEmpty()) {
                log(logger, Level.WARNING, "No Java extensions are available.");
                return false;
            }

            ExtensionDef extension = findExtension(index, appStateName);
            if (extension == null) {
                log(logger, Level.WARNING, "Java extension app state was not found: " + appStateName);
                return false;
            }
            ClassLoader loader = createClassLoader(index, app.getClass().getClassLoader());
            return attachExtension(app, scope, loader, extension, logger);
        } catch (Exception e) {
            if (logger != null) {
                logger.log(Level.SEVERE, "Failed to attach Java extension " + appStateName, e);
            } else {
                e.printStackTrace();
            }
            return false;
        }
    }

    private static ExtensionDef findExtension(ExtensionIndex index, String appStateName) {
        if (appStateName == null || appStateName.trim().isEmpty()) {
            return null;
        }
        String requested = appStateName.trim();
        for (ExtensionDef extension : index.extensions) {
            if (requested.equals(extension.name) || requested.equals(extension.className)) {
                return extension;
            }
        }
        return null;
    }

    private static boolean attachExtension(SceneMaxApp app, SceneMaxScope scope, ClassLoader loader,
                                           ExtensionDef extension, Logger logger) {
        try {
            Class<?> extensionClass = Class.forName(extension.className, true, loader);
            Object instance = extensionClass.getDeclaredConstructor().newInstance();
            if (!(instance instanceof AppState)) {
                log(logger, Level.WARNING, "Java extension " + extension.className + " does not implement AppState.");
                return false;
            }
            if (!(instance instanceof SceneMaxBaseAppState)) {
                log(logger, Level.WARNING, "Java extension " + extension.className
                        + " should extend SceneMaxBaseAppState to receive the current SceneMax scope.");
            } else {
                ((SceneMaxBaseAppState) instance).setSceneMaxScope(scope);
            }
            app.getStateManager().attach((AppState) instance);
            log(logger, Level.INFO, "Attached Java extension app state: " + extension.className);
            return true;
        } catch (Exception e) {
            if (logger != null) {
                logger.log(Level.SEVERE, "Failed to attach Java extension " + extension.className, e);
            } else {
                e.printStackTrace();
            }
            return false;
        }
    }

    private static ExtensionIndex loadIndex(SceneMaxApp app) throws IOException {
        File workingIndex = app.getWorkingFolder() == null ? null
                : new File(new File(app.getWorkingFolder(), EXTENSIONS_FOLDER), EXTENSIONS_INDEX);
        if (workingIndex != null && workingIndex.isFile()) {
            return ExtensionIndex.fromJson(org.apache.commons.io.FileUtils.readFileToString(workingIndex, StandardCharsets.UTF_8), workingIndex.getParentFile());
        }

        File runningIndex = new File(new File("running", EXTENSIONS_FOLDER), EXTENSIONS_INDEX);
        if (runningIndex.isFile()) {
            return ExtensionIndex.fromJson(org.apache.commons.io.FileUtils.readFileToString(runningIndex, StandardCharsets.UTF_8), runningIndex.getParentFile());
        }

        InputStream in = JavaExtensionRuntimeLoader.class.getClassLoader().getResourceAsStream(RESOURCE_INDEX);
        if (in != null) {
            return ExtensionIndex.fromJson(readToString(in), null);
        }
        return null;
    }

    private static ClassLoader createClassLoader(ExtensionIndex index, ClassLoader parent) throws IOException {
        if (index.baseFolder == null) {
            return parent;
        }

        List<URL> urls = new ArrayList<>();
        for (ExtensionDef extension : index.extensions) {
            if (extension.jar == null || extension.jar.isBlank()) {
                continue;
            }
            File jar = new File(index.baseFolder, extension.jar);
            if (jar.isFile()) {
                urls.add(jar.toURI().toURL());
            }
        }
        if (urls.isEmpty()) {
            return parent;
        }
        return new URLClassLoader(urls.toArray(new URL[0]), parent);
    }

    private static String readToString(InputStream in) throws IOException {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void log(Logger logger, Level level, String message) {
        if (logger != null) {
            logger.log(level, message);
        } else {
            System.out.println(message);
        }
    }

    private static final class ExtensionIndex {
        final File baseFolder;
        final List<ExtensionDef> extensions = new ArrayList<>();

        private ExtensionIndex(File baseFolder) {
            this.baseFolder = baseFolder;
        }

        static ExtensionIndex fromJson(String json, File baseFolder) {
            ExtensionIndex index = new ExtensionIndex(baseFolder);
            JSONObject root = new JSONObject(json);
            JSONArray array = root.optJSONArray("extensions");
            if (array == null) {
                return index;
            }
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String className = item.optString("className", "").trim();
                if (className.isEmpty()) {
                    continue;
                }
                ExtensionDef extension = new ExtensionDef();
                extension.name = item.optString("name", className);
                extension.className = className;
                extension.jar = item.optString("jar", "");
                index.extensions.add(extension);
            }
            return index;
        }
    }

    private static final class ExtensionDef {
        String name;
        String className;
        String jar;
    }
}
