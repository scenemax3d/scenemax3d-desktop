package com.scenemaxeng.common.types;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;

public class PluginsManager {

    public static JSONObject getSceneMax3dPlugin(String pluginName) {
        File pluginsDir = new File("plugins");
        File index = new File(pluginsDir, "index.json");
        JSONArray pluginsIndex;
        if (!index.exists()) {
            return null;
        }

        try {
            pluginsIndex = new JSONArray(FileUtils.readFileToString(index, StandardCharsets.UTF_8));
            for (Object it : pluginsIndex) {
                JSONObject item = (JSONObject)it;
                String name = item.getString("name");
                if (name.equals(pluginName)) {
                    return item;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public static ISceneMaxPlugin loadPlugin(String pluginName, ISceneMaxPlugin observer, boolean dualWayCommunication) {

        JSONObject pluginMd = PluginsManager.getSceneMax3dPlugin(pluginName);
        if (pluginMd == null) {
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
