package com.scenemax.designer.animation;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class AnimationImportWorker {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalArgumentException("Usage: inspect <source> <outputJson> OR import <source> <resources> <name> <outputJson> OR convert-model <source> <outputJ3o> OR convert-runtime-model <source> <outputDir> <name> <outputJson>");
        }

        String command = args[0];
        AnimationImportResult result = null;
        File outputFile = null;
        if ("inspect".equals(command)) {
            result = AnimationImporter.inspect(new File(args[1]));
            outputFile = new File(args[2]);
        } else if ("import".equals(command)) {
            if (args.length < 5) {
                throw new IllegalArgumentException("Usage: import <source> <resources> <name> <outputJson> [bevyRetargetProfile] [bevySkipTopAnimatedTargets] [bevyExcludeBonesCsv] [bevyVisualTranslationX] [bevyVisualTranslationY] [bevyVisualTranslationZ] [bevyVisualRotationX] [bevyVisualRotationY] [bevyVisualRotationZ] [bevyLockTranslationX] [bevyLockTranslationY] [bevyLockTranslationZ] [bevyRootBone] [bevyScaleBaseBone] [bevyRemoveUnimportantTranslationTracks] [bevyNormalizeMotionScale] [bevyRemoveMotionTranslationTracks] [bevyRemoveMotionRotationTracks]");
            }
            AnimationImportOptions options = new AnimationImportOptions();
            if (args.length >= 6) {
                options.setBevyRetargetProfile(args[5]);
            }
            if (args.length >= 7) {
                options.setBevySkipTopAnimatedTargets(Integer.parseInt(args[6]));
            }
            if (args.length >= 8) {
                options.setBevyExcludedBonesCsv(args[7]);
            }
            if (args.length >= 11) {
                options.setBevyVisualTranslationX(Float.parseFloat(args[8]));
                options.setBevyVisualTranslationY(Float.parseFloat(args[9]));
                options.setBevyVisualTranslationZ(Float.parseFloat(args[10]));
            }
            if (args.length >= 14) {
                options.setBevyVisualRotationXDegrees(Float.parseFloat(args[11]));
                options.setBevyVisualRotationYDegrees(Float.parseFloat(args[12]));
                options.setBevyVisualRotationZDegrees(Float.parseFloat(args[13]));
            }
            if (args.length >= 17) {
                options.setBevyLockTranslationX(Boolean.parseBoolean(args[14]));
                options.setBevyLockTranslationY(Boolean.parseBoolean(args[15]));
                options.setBevyLockTranslationZ(Boolean.parseBoolean(args[16]));
            }
            if (args.length >= 19) {
                options.setBevyRootBone(args[17]);
                options.setBevyScaleBaseBone(args[18]);
            }
            if (args.length >= 21) {
                options.setBevyRemoveUnimportantTranslationTracks(Boolean.parseBoolean(args[19]));
                options.setBevyNormalizeMotionScale(Boolean.parseBoolean(args[20]));
            }
            if (args.length >= 23) {
                options.setBevyRemoveMotionTranslationTracks(Boolean.parseBoolean(args[21]));
                options.setBevyRemoveMotionRotationTracks(Boolean.parseBoolean(args[22]));
            }
            result = AnimationImporter.importAnimation(new File(args[1]), new File(args[2]), args[3], options);
            outputFile = new File(args[4]);
        } else if ("convert-model".equals(command)) {
            AnimationImporter.convertModelToJ3o(new File(args[1]), new File(args[2]));
        } else if ("convert-runtime-model".equals(command)) {
            if (args.length < 5) {
                throw new IllegalArgumentException("Usage: convert-runtime-model <source> <outputDir> <name> <outputJson>");
            }
            File modelFile = AnimationImporter.convertModelForRuntime(new File(args[1]), new File(args[2]), args[3]);
            JSONObject json = new JSONObject();
            json.put("modelFile", modelFile.getAbsolutePath());
            Files.write(new File(args[4]).toPath(), json.toString(2).getBytes(StandardCharsets.UTF_8));
        } else {
            throw new IllegalArgumentException("Unknown animation import command: " + command);
        }

        if (outputFile != null) {
            JSONObject json = toJson(result);
            Files.write(outputFile.toPath(), json.toString(2).getBytes(StandardCharsets.UTF_8));
        }
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
