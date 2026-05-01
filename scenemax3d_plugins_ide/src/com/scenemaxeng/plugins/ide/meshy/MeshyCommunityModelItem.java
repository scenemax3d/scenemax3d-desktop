package com.scenemaxeng.plugins.ide.meshy;

import org.json.JSONObject;

import java.util.Locale;

final class MeshyCommunityModelItem {
    final JSONObject model;

    MeshyCommunityModelItem(JSONObject model) {
        this.model = model;
    }

    String id() {
        return model.optString("id", "");
    }

    String resultId() {
        return model.optString("resultId", "");
    }

    String animationId() {
        return model.optString("animationId", "").trim();
    }

    String downloadTaskId() {
        return resultId();
    }

    String title() {
        String name = model.optString("name", "").trim();
        if (!name.isEmpty()) {
            return name;
        }
        String prompt = prompt();
        return prompt.isEmpty() ? "Meshy community model" : prompt;
    }

    String prompt() {
        String prompt = model.optString("objectPrompt", "").trim();
        if (!prompt.isEmpty()) {
            return prompt;
        }
        JSONObject args = model.optJSONObject("args");
        JSONObject draft = args == null ? null : args.optJSONObject("draft");
        return draft == null ? "" : draft.optString("prompt", "").trim();
    }

    String author() {
        return model.optString("author", "").trim();
    }

    String thumbnailUrl() {
        String url = model.optString("thumbnailUrl", "").trim();
        if (!url.isEmpty()) {
            return url;
        }
        return model.optString("solidThumbnailUrl", "").trim();
    }

    String modelUrl() {
        return model.optString("modelUrl", "").trim();
    }

    String license() {
        return model.optString("license", "").trim();
    }

    int downloads() {
        return model.optInt("downloads", 0);
    }

    int views() {
        return model.optInt("views", 0);
    }

    boolean hasAnimation() {
        if (!animationId().isEmpty()) {
            return true;
        }
        JSONObject args = model.optJSONObject("args");
        if (args != null && args.has("animate") && args.opt("animate") != JSONObject.NULL) {
            return true;
        }
        return containsMotionWord(model.optString("phase", ""))
                || containsMotionWord(model.optString("mode", ""))
                || containsMotionWord(model.optString("type", ""));
    }

    boolean hasRigHint() {
        if (hasAnimation()) {
            return true;
        }
        if (containsRigWord(model.optString("phase", ""))
                || containsRigWord(model.optString("mode", ""))
                || containsRigWord(model.optString("type", ""))
                || containsRigWord(model.optString("objectPrompt", ""))) {
            return true;
        }
        Object tags = model.opt("tags");
        return tags != null && containsRigWord(String.valueOf(tags));
    }

    String motionLabel() {
        if (hasAnimation()) {
            return "Animated";
        }
        if (hasRigHint()) {
            return "Rig/animation";
        }
        return "";
    }

    String pageUrl() {
        String modelId = animationId().isEmpty() ? resultId() : animationId();
        if (modelId.isEmpty()) {
            modelId = id();
        }
        if (modelId.isEmpty()) {
            return "https://www.meshy.ai/discover";
        }
        String slug = title().replaceAll("[^a-zA-Z0-9 ]", "")
                .trim()
                .replaceAll("\\s+", "-");
        String url = "https://www.meshy.ai/3d-models/" + (slug.isEmpty() ? "" : slug + "-") + "v2-" + modelId;
        return animationId().isEmpty() ? url : url + "?animation=true";
    }

    String suggestedAssetName() {
        String base = title();
        if (base == null || base.trim().isEmpty()) {
            base = "community_" + shortId(resultId().isEmpty() ? id() : resultId());
        }
        String sanitized = base.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
        sanitized = sanitized.replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        if (sanitized.isEmpty()) {
            sanitized = "community_" + shortId(resultId().isEmpty() ? id() : resultId());
        }
        if (sanitized.length() > 42) {
            sanitized = sanitized.substring(0, 42).replaceAll("_+$", "");
        }
        return "meshy_" + sanitized;
    }

    private String shortId(String id) {
        return id == null ? "model" : id.substring(0, Math.min(8, id.length()));
    }

    private static boolean containsMotionWord(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return normalized.contains("anim");
    }

    private static boolean containsRigWord(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return normalized.contains("rig")
                || normalized.contains("anim")
                || normalized.contains("humanoid")
                || normalized.contains("character");
    }
}
