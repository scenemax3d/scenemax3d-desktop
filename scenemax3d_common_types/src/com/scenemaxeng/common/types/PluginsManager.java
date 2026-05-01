package com.scenemaxeng.common.types;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class PluginsManager {

    public static JSONArray getPluginsIndex() {
        File pluginsDir = new File("plugins");
        File index = new File(pluginsDir, "index.json");
        if (!index.exists()) {
            return new JSONArray();
        }

        try {
            return new JSONArray(FileUtils.readFileToString(index, StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
            return new JSONArray();
        }
    }

    public static synchronized boolean setPluginActive(String pluginName, boolean active) {
        if (pluginName == null || pluginName.trim().isEmpty()) {
            return false;
        }

        File pluginsDir = new File("plugins");
        File index = new File(pluginsDir, "index.json");
        JSONArray pluginsIndex = getPluginsIndex();
        boolean changed = false;
        for (Object it : pluginsIndex) {
            JSONObject item = (JSONObject) it;
            if (pluginName.equals(item.optString("name", ""))) {
                if (item.optBoolean("active", true) != active) {
                    item.put("active", active);
                    changed = true;
                }
                break;
            }
        }

        if (!changed) {
            return false;
        }

        try {
            FileUtils.writeStringToFile(index, pluginsIndex.toString(2), StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static List<JSONObject> getActiveEnhancedPlugins() {
        List<JSONObject> plugins = new ArrayList<>();
        JSONArray pluginsIndex = getPluginsIndex();
        for (Object it : pluginsIndex) {
            JSONObject item = (JSONObject) it;
            if (item.optBoolean("active", true) && item.optBoolean("enhanced", false)) {
                plugins.add(item);
            }
        }
        return plugins;
    }

    public static JSONObject getSceneMax3dPlugin(String pluginName) {
        JSONArray pluginsIndex = getPluginsIndex();
        for (Object it : pluginsIndex) {
            JSONObject item = (JSONObject)it;
            String name = item.getString("name");
            if (name.equals(pluginName)) {
                return item;
            }
        }

        return null;
    }

    public static ISceneMaxPlugin loadPlugin(String pluginName, ISceneMaxPlugin observer, boolean dualWayCommunication) {

        JSONObject pluginMd = PluginsManager.getSceneMax3dPlugin(pluginName);
        if (pluginMd == null) {
            return null;
        }
        if (!pluginMd.optBoolean("active", true)) {
            return null;
        }

        // name, className, fileName
        File pluginsDir = new File("plugins");
        File pluginFile = new File(pluginsDir, pluginMd.getString("fileName"));

        try {
            // Create a URLClassLoader for the JAR file
            URL jarUrl = pluginFile.toURI().toURL();
            URLClassLoader loader = new URLClassLoader(new URL[]{jarUrl}, ISceneMaxPlugin.class.getClassLoader());

            // Load the class implementing the Plugin interface
            String fullPluginClassName = pluginMd.getString("className");
            Class<?> pluginClass = loader.loadClass(fullPluginClassName);

            // Check if the class implements the Plugin interface
            if (ISceneMaxPlugin.class.isAssignableFrom(pluginClass)) {
                // Create an instance of the plugin class
                ISceneMaxPlugin plugin = (ISceneMaxPlugin) pluginClass.getDeclaredConstructor().newInstance();
                plugin.registerObserver(observer); // this.app.pluginsCommunicationChannel allow sending messages to the scenemax engine
                if (dualWayCommunication) {
                    observer.registerObserver(plugin); // allow scenemax engine to send messages to the plugin
                }
                return plugin;
            }

        } catch(Exception ex) {
            ex.printStackTrace();
        }

        return null;

    }

}
