package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.ai.SceneMaxTool;
import org.json.JSONObject;

public abstract class AbstractSceneMaxTool implements SceneMaxTool {

    protected String requireString(JSONObject arguments, String name) {
        String value = arguments.optString(name, "").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Missing required argument: " + name);
        }
        return value;
    }

    protected String optionalString(JSONObject arguments, String name, String defaultValue) {
        String value = arguments.optString(name, defaultValue);
        return value == null ? defaultValue : value.trim();
    }

    protected boolean optionalBoolean(JSONObject arguments, String name, boolean defaultValue) {
        return arguments.has(name) ? arguments.optBoolean(name, defaultValue) : defaultValue;
    }

    protected int optionalInt(JSONObject arguments, String name, int defaultValue) {
        return arguments.has(name) ? arguments.optInt(name, defaultValue) : defaultValue;
    }
}
