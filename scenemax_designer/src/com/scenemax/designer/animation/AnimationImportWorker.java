package com.scenemax.designer.animation;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class AnimationImportWorker {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalArgumentException("Usage: inspect <source> <outputJson> OR import <source> <resources> <name> <outputJson>");
        }

        String command = args[0];
        AnimationImportResult result;
        File outputFile;
        if ("inspect".equals(command)) {
            result = AnimationImporter.inspect(new File(args[1]));
            outputFile = new File(args[2]);
        } else if ("import".equals(command)) {
            if (args.length < 5) {
                throw new IllegalArgumentException("Usage: import <source> <resources> <name> <outputJson>");
            }
            result = AnimationImporter.importAnimation(new File(args[1]), new File(args[2]), args[3]);
            outputFile = new File(args[4]);
        } else {
            throw new IllegalArgumentException("Unknown animation import command: " + command);
        }

        JSONObject json = toJson(result);
        Files.write(outputFile.toPath(), json.toString(2).getBytes(StandardCharsets.UTF_8));
    }

    private static JSONObject toJson(AnimationImportResult result) {
        JSONObject json = new JSONObject();
        if (result.getAssetFolder() != null) {
            json.put("assetFolder", result.getAssetFolder().getAbsolutePath());
        }
        if (result.getAnimationFile() != null) {
            json.put("animationFile", result.getAnimationFile().getAbsolutePath());
        }
        json.put("clipNames", new JSONArray(result.getClipNames()));
        json.put("clipSummaries", new JSONArray(result.getClipSummaries()));
        if (result.getSelectedClipName() != null) {
            json.put("selectedClipName", result.getSelectedClipName());
        }
        return json;
    }
}
