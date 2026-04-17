package com.scenemax.designer;

import com.jme3.asset.AssetManager;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingSphere;
import com.jme3.bounding.BoundingVolume;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.jme3.system.JmeSystem;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Designer-side helper that uses JME to inspect imported 3D model bounds and
 * recommend a scale without exposing JME types to automation callers.
 */
public final class ModelAutoFitAdvisor {

    private ModelAutoFitAdvisor() {
    }

    public static Recommendation recommend(File modelFile, String modelName, String title) {
        if (modelFile == null || !modelFile.exists() || !modelFile.isFile()) {
            return Recommendation.fallback("missing-model-file");
        }

        try {
            Spatial spatial = loadSpatial(modelFile);
            if (spatial == null) {
                return Recommendation.fallback("load-returned-null");
            }

            spatial.updateLogicalState(0f);
            spatial.updateGeometricState();

            BoundingVolume bound = spatial.getWorldBound();
            if (bound == null) {
                return Recommendation.fallback("missing-world-bound");
            }

            float sourceHeight = 1.0f;
            float sourceMaxDimension = 1.0f;
            if (bound instanceof BoundingBox) {
                Vector3f extent = ((BoundingBox) bound).getExtent(null);
                sourceHeight = extent.y * 2.0f;
                sourceMaxDimension = Math.max(extent.x, Math.max(extent.y, extent.z)) * 2.0f;
            } else if (bound instanceof BoundingSphere) {
                float diameter = ((BoundingSphere) bound).getRadius() * 2.0f;
                sourceHeight = diameter;
                sourceMaxDimension = diameter;
            } else {
                float approx = (float) Math.cbrt(Math.max(bound.getVolume(), 0.0001f));
                sourceHeight = approx;
                sourceMaxDimension = approx;
            }

            sourceHeight = sanitizePositive(sourceHeight, 1.0f);
            sourceMaxDimension = sanitizePositive(sourceMaxDimension, sourceHeight);

            ScaleProfile profile = chooseProfile(modelName, title);
            float observedSize = profile.useHeight ? sourceHeight : sourceMaxDimension;
            observedSize = sanitizePositive(observedSize, 1.0f);

            float recommendedScale = clamp(profile.targetSize / observedSize, 0.02f, 20.0f);
            return new Recommendation(
                    true,
                    recommendedScale,
                    recommendedScale,
                    recommendedScale,
                    sourceHeight,
                    sourceMaxDimension,
                    profile.targetSize,
                    observedSize,
                    profile.name
            );
        } catch (Exception ex) {
            return Recommendation.fallback("exception:" + ex.getClass().getSimpleName());
        }
    }

    private static Spatial loadSpatial(File modelFile) throws IOException {
        AssetManager assetManager = JmeSystem.newAssetManager();
        File parent = modelFile.getParentFile();
        if (parent != null) {
            assetManager.registerLocator(parent.getCanonicalPath(), FileLocator.class);
        }
        return assetManager.loadModel(modelFile.getName());
    }

    private static ScaleProfile chooseProfile(String modelName, String title) {
        String text = ((modelName == null ? "" : modelName) + " " + (title == null ? "" : title))
                .toLowerCase(Locale.ROOT);

        if (containsAny(text, "tree", "cypress", "olive", "palm", "bonsai", "cherry")) {
            return new ScaleProfile("tree-height", 5.5f, true);
        }
        if (containsAny(text, "column", "pillar", "obelisk")) {
            return new ScaleProfile("column-height", 4.2f, true);
        }
        if (containsAny(text, "arch", "gate")) {
            return new ScaleProfile("arch-height", 4.8f, true);
        }
        if (containsAny(text, "bust")) {
            return new ScaleProfile("bust-height", 1.4f, true);
        }
        if (containsAny(text, "statue")) {
            return new ScaleProfile("statue-height", 2.4f, true);
        }
        if (containsAny(text, "shield", "scutum", "buckler")) {
            return new ScaleProfile("shield-height", 1.4f, true);
        }
        if (containsAny(text, "sword", "gladius", "gladio", "blade", "spear")) {
            return new ScaleProfile("weapon-height", 1.1f, true);
        }
        if (containsAny(text, "vase", "amphora", "pottery", "urn")) {
            return new ScaleProfile("vessel-height", 1.0f, true);
        }
        if (containsAny(text, "barrel", "crate")) {
            return new ScaleProfile("storage-height", 1.3f, true);
        }
        if (containsAny(text, "torch", "lamp")) {
            return new ScaleProfile("torch-height", 1.9f, true);
        }
        if (containsAny(text, "car", "cart", "vehicle")) {
            return new ScaleProfile("vehicle-height", 3.5f, true);
        }
        if (containsAny(text, "building", "house", "market", "street", "arena", "pack", "wall")) {
            return new ScaleProfile("environment-span", 8.0f, false);
        }

        return new ScaleProfile("generic-height", 2.2f, true);
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static float sanitizePositive(float value, float fallback) {
        if (Float.isNaN(value) || Float.isInfinite(value) || value < 0.0001f) {
            return fallback;
        }
        return value;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class ScaleProfile {
        final String name;
        final float targetSize;
        final boolean useHeight;

        ScaleProfile(String name, float targetSize, boolean useHeight) {
            this.name = name;
            this.targetSize = targetSize;
            this.useHeight = useHeight;
        }
    }

    public static final class Recommendation {
        public final boolean success;
        public final float scaleX;
        public final float scaleY;
        public final float scaleZ;
        public final float sourceHeight;
        public final float sourceMaxDimension;
        public final float targetSize;
        public final float observedSize;
        public final String strategy;

        public Recommendation(boolean success,
                              float scaleX,
                              float scaleY,
                              float scaleZ,
                              float sourceHeight,
                              float sourceMaxDimension,
                              float targetSize,
                              float observedSize,
                              String strategy) {
            this.success = success;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.scaleZ = scaleZ;
            this.sourceHeight = sourceHeight;
            this.sourceMaxDimension = sourceMaxDimension;
            this.targetSize = targetSize;
            this.observedSize = observedSize;
            this.strategy = strategy;
        }

        public static Recommendation fallback(String strategy) {
            return new Recommendation(false, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, strategy);
        }
    }
}
