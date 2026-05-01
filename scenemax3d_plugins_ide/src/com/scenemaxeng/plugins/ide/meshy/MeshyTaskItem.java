package com.scenemaxeng.plugins.ide.meshy;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

final class MeshyTaskItem {
    final JSONObject task;

    MeshyTaskItem(JSONObject task) {
        this.task = task;
    }

    String id() {
        return task.optString("id", "");
    }

    String prompt() {
        return task.optString("prompt", "");
    }

    String status() {
        return task.optString("status", "");
    }

    int progress() {
        return task.optInt("progress", 0);
    }

    boolean isPreview() {
        return task.optString("type", "").toLowerCase(Locale.ROOT).contains("preview");
    }

    boolean isRefine() {
        return task.optString("type", "").toLowerCase(Locale.ROOT).contains("refine");
    }

    boolean isTextured() {
        return isRefine() || (!isPreview() && !glbUrl().isEmpty()) || hasTextureUrls();
    }

    boolean hasTextureUrls() {
        JSONArray textures = task.optJSONArray("texture_urls");
        return textures != null && textures.length() > 0;
    }

    String glbUrl() {
        JSONObject urls = task.optJSONObject("model_urls");
        return urls == null ? "" : urls.optString("glb", "");
    }

    @Override
    public String toString() {
        String prompt = prompt();
        if (prompt.length() > 80) {
            prompt = prompt.substring(0, 77) + "...";
        }
        return status() + " " + progress() + "% - " + prompt;
    }
}
