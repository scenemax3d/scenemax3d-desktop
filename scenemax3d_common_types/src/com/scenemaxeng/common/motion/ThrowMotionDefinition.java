package com.scenemaxeng.common.motion;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class ThrowMotionDefinition {
    public static final String FILE_EXTENSION = ".smmotion";
    public static final String SCHEMA_VERSION = "1.0";

    public static final String TYPE_PHYSICS = "physics";
    public static final String TYPE_BALLISTIC = "ballistic";
    public static final String TYPE_TARGET_ARC = "target_arc";
    public static final String TYPE_STRAIGHT = "straight";
    public static final String TYPE_HOMING = "homing";
    public static final String TYPE_RETURNING = "returning";
    public static final String TYPE_CUSTOM_CURVE = "custom_curve";

    private static final Set<String> KNOWN_TYPES = new HashSet<>(Arrays.asList(
            TYPE_PHYSICS,
            TYPE_BALLISTIC,
            TYPE_TARGET_ARC,
            TYPE_STRAIGHT,
            TYPE_HOMING,
            TYPE_RETURNING,
            TYPE_CUSTOM_CURVE
    ));

    private String id = "motion_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private String displayName = "Throw Motion";
    private String motionType = TYPE_TARGET_ARC;
    private MotionParameters parameters = MotionParameters.forType(TYPE_TARGET_ARC);
    private JSONObject designerMetadata = new JSONObject();

    public static ThrowMotionDefinition createTemplate(String displayName, String motionType) {
        ThrowMotionDefinition definition = new ThrowMotionDefinition();
        definition.displayName = displayName == null || displayName.trim().isEmpty()
                ? "Throw Motion"
                : displayName.trim();
        definition.id = slugId(definition.displayName);
        definition.setMotionType(motionType);
        return definition;
    }

    public JSONObject toJSON() {
        return new JSONObject()
                .put("type", "SceneMaxThrowMotionDefinition")
                .put("schemaVersion", SCHEMA_VERSION)
                .put("id", id)
                .put("displayName", displayName)
                .put("motionType", motionType)
                .put("parameters", parameters == null ? new JSONObject() : parameters.toJSON())
                .put("designerMetadata", designerMetadata == null ? new JSONObject() : designerMetadata);
    }

    public static ThrowMotionDefinition fromJSON(JSONObject json) {
        ThrowMotionDefinition definition = new ThrowMotionDefinition();
        if (json == null) {
            return definition;
        }
        definition.id = json.optString("id", definition.id);
        definition.displayName = json.optString("displayName", definition.displayName);
        definition.motionType = normalizeMotionType(json.optString("motionType", definition.motionType));
        definition.parameters = MotionParameters.fromJSON(json.optJSONObject("parameters"), definition.motionType);
        definition.designerMetadata = json.optJSONObject("designerMetadata");
        if (definition.designerMetadata == null) {
            definition.designerMetadata = new JSONObject();
        }
        return definition;
    }

    public static ThrowMotionDefinition load(File file) throws IOException {
        return fromJSON(new JSONObject(FileUtils.readFileToString(file, StandardCharsets.UTF_8)));
    }

    public void save(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        FileUtils.writeStringToFile(file, toJSON().toString(2), StandardCharsets.UTF_8);
    }

    public ThrowMotionValidationResult validate() {
        ThrowMotionValidationResult result = new ThrowMotionValidationResult();
        if (id == null || id.trim().isEmpty()) {
            result.addError("id", "Throw motion id is required.");
        }
        if (!KNOWN_TYPES.contains(normalizeMotionType(motionType))) {
            result.addError("motionType", "Unsupported throw motion type: " + motionType);
        }
        MotionParameters p = getParameters();
        if (requiresPositiveSpeed() && p.getSpeedForType(motionType) <= 0.0) {
            result.addError("parameters.speed", "Speed must be greater than zero.");
        }
        if ((TYPE_TARGET_ARC.equals(motionType) || TYPE_RETURNING.equals(motionType) || TYPE_CUSTOM_CURVE.equals(motionType))
                && p.duration <= 0.0) {
            result.addError("parameters.duration", "Duration must be greater than zero.");
        }
        if (p.maxLifetime <= 0.0) {
            result.addWarning("parameters.maxLifetime", "Max lifetime should be greater than zero for runtime cleanup.");
        }
        if (p.collisionRadius < 0.0) {
            result.addError("parameters.collisionRadius", "Collision radius cannot be negative.");
        }
        if (TYPE_CUSTOM_CURVE.equals(motionType)) {
            result.addWarning("motionType", "Custom curve authoring is reserved for the next editor pass.");
        }
        return result;
    }

    private boolean requiresPositiveSpeed() {
        String type = normalizeMotionType(motionType);
        return TYPE_PHYSICS.equals(type)
                || TYPE_BALLISTIC.equals(type)
                || TYPE_STRAIGHT.equals(type)
                || TYPE_HOMING.equals(type)
                || TYPE_RETURNING.equals(type);
    }

    private static String slugId(String value) {
        String slug = value == null ? "throw_motion" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        slug = slug.replaceAll("^_+|_+$", "");
        if (slug.isEmpty()) {
            slug = "throw_motion";
        }
        if (!slug.startsWith("motion_")) {
            slug = "motion_" + slug;
        }
        return slug;
    }

    public static String normalizeMotionType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return TYPE_TARGET_ARC;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return KNOWN_TYPES.contains(normalized) ? normalized : TYPE_TARGET_ARC;
    }

    public static String displayNameForType(String type) {
        String normalized = normalizeMotionType(type);
        if (TYPE_PHYSICS.equals(normalized)) return "Physics Throw";
        if (TYPE_BALLISTIC.equals(normalized)) return "Ballistic Throw";
        if (TYPE_TARGET_ARC.equals(normalized)) return "Target Arc Throw";
        if (TYPE_STRAIGHT.equals(normalized)) return "Straight Projectile";
        if (TYPE_HOMING.equals(normalized)) return "Homing Throw";
        if (TYPE_RETURNING.equals(normalized)) return "Returning Throw";
        if (TYPE_CUSTOM_CURVE.equals(normalized)) return "Custom Curve Throw";
        return "Target Arc Throw";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getMotionType() {
        return motionType;
    }

    public void setMotionType(String motionType) {
        String normalized = normalizeMotionType(motionType);
        this.motionType = normalized;
        if (parameters == null) {
            parameters = MotionParameters.forType(normalized);
        } else {
            parameters.applyDefaultsForType(normalized);
        }
    }

    public MotionParameters getParameters() {
        if (parameters == null) {
            parameters = MotionParameters.forType(motionType);
        }
        return parameters;
    }

    public void setParameters(MotionParameters parameters) {
        this.parameters = parameters == null ? MotionParameters.forType(motionType) : parameters;
    }

    public JSONObject getDesignerMetadata() {
        if (designerMetadata == null) {
            designerMetadata = new JSONObject();
        }
        return designerMetadata;
    }

    public static class MotionParameters {
        public double initialSpeed = 16.0;
        public double launchAngle = 35.0;
        public double gravityScale = 1.0;
        public double duration = 1.2;
        public double arcHeight = 3.0;
        public String arcMode = "relative_height";
        public String easingFunction = "ease_in_out";
        public double speed = 18.0;
        public double acceleration = 0.0;
        public double maxDistance = 30.0;
        public double maxLifetime = 4.0;
        public boolean alignToVelocity = true;
        public boolean alignToPath = true;
        public double spinSpeed = 720.0;
        public String collisionMode = "spherecast";
        public double collisionRadius = 0.25;
        public boolean stopOnImpact = true;
        public String targetMode = "point";
        public double turnRate = 180.0;
        public double maxTurnAngle = 90.0;
        public double homingDelay = 0.15;
        public double homingStrength = 1.0;
        public String loseTargetBehavior = "continue";
        public double outboundDuration = 0.75;
        public double outboundDistance = 12.0;
        public double outboundArcHeight = 1.0;
        public double returnSpeed = 18.0;
        public double returnDelay = 0.15;
        public boolean canHitOnReturn = true;
        public String ownerTarget = "thrower";
        public double catchRadius = 0.75;
        public String forceMode = "impulse";
        public double massOverride = 0.0;
        public double drag = 0.0;
        public String impactBehavior = "stop";
        public boolean switchToPhysicsOnImpact = false;

        public static MotionParameters forType(String type) {
            MotionParameters p = new MotionParameters();
            p.applyDefaultsForType(type);
            return p;
        }

        public void applyDefaultsForType(String type) {
            String normalized = normalizeMotionType(type);
            if (TYPE_PHYSICS.equals(normalized)) {
                initialSpeed = valueOr(initialSpeed, 14.0);
                launchAngle = valueOr(launchAngle, 35.0);
                switchToPhysicsOnImpact = true;
                forceMode = emptyTo(forceMode, "impulse");
            } else if (TYPE_BALLISTIC.equals(normalized)) {
                initialSpeed = valueOr(initialSpeed, 20.0);
                launchAngle = valueOr(launchAngle, 25.0);
                gravityScale = valueOr(gravityScale, 1.0);
            } else if (TYPE_TARGET_ARC.equals(normalized)) {
                duration = valueOr(duration, 1.2);
                arcHeight = valueOr(arcHeight, 3.0);
                targetMode = emptyTo(targetMode, "point");
                easingFunction = emptyTo(easingFunction, "ease_in_out");
            } else if (TYPE_STRAIGHT.equals(normalized)) {
                speed = valueOr(speed, 24.0);
                acceleration = valueOr(acceleration, 0.0);
                maxDistance = valueOr(maxDistance, 35.0);
            } else if (TYPE_HOMING.equals(normalized)) {
                speed = valueOr(speed, 15.0);
                turnRate = valueOr(turnRate, 180.0);
                homingDelay = valueOr(homingDelay, 0.15);
                targetMode = emptyTo(targetMode, "object");
            } else if (TYPE_RETURNING.equals(normalized)) {
                outboundDuration = valueOr(outboundDuration, 0.75);
                outboundDistance = valueOr(outboundDistance, 12.0);
                returnSpeed = valueOr(returnSpeed, 18.0);
                returnDelay = valueOr(returnDelay, 0.15);
                duration = Math.max(0.1, outboundDuration + returnDelay + outboundDistance / Math.max(1.0, returnSpeed));
            }
        }

        private double getSpeedForType(String type) {
            String normalized = normalizeMotionType(type);
            if (TYPE_PHYSICS.equals(normalized) || TYPE_BALLISTIC.equals(normalized)) {
                return initialSpeed;
            }
            if (TYPE_RETURNING.equals(normalized)) {
                return Math.max(outboundDistance / Math.max(0.1, outboundDuration), returnSpeed);
            }
            return speed;
        }

        public JSONObject toJSON() {
            return new JSONObject()
                    .put("initialSpeed", initialSpeed)
                    .put("launchAngle", launchAngle)
                    .put("gravityScale", gravityScale)
                    .put("duration", duration)
                    .put("arcHeight", arcHeight)
                    .put("arcMode", arcMode)
                    .put("easingFunction", easingFunction)
                    .put("speed", speed)
                    .put("acceleration", acceleration)
                    .put("maxDistance", maxDistance)
                    .put("maxLifetime", maxLifetime)
                    .put("alignToVelocity", alignToVelocity)
                    .put("alignToPath", alignToPath)
                    .put("spinSpeed", spinSpeed)
                    .put("collisionMode", collisionMode)
                    .put("collisionRadius", collisionRadius)
                    .put("stopOnImpact", stopOnImpact)
                    .put("targetMode", targetMode)
                    .put("turnRate", turnRate)
                    .put("maxTurnAngle", maxTurnAngle)
                    .put("homingDelay", homingDelay)
                    .put("homingStrength", homingStrength)
                    .put("loseTargetBehavior", loseTargetBehavior)
                    .put("outboundDuration", outboundDuration)
                    .put("outboundDistance", outboundDistance)
                    .put("outboundArcHeight", outboundArcHeight)
                    .put("returnSpeed", returnSpeed)
                    .put("returnDelay", returnDelay)
                    .put("canHitOnReturn", canHitOnReturn)
                    .put("ownerTarget", ownerTarget)
                    .put("catchRadius", catchRadius)
                    .put("forceMode", forceMode)
                    .put("massOverride", massOverride)
                    .put("drag", drag)
                    .put("impactBehavior", impactBehavior)
                    .put("switchToPhysicsOnImpact", switchToPhysicsOnImpact);
        }

        public static MotionParameters fromJSON(JSONObject json, String motionType) {
            MotionParameters p = forType(motionType);
            if (json == null) {
                return p;
            }
            p.initialSpeed = json.optDouble("initialSpeed", p.initialSpeed);
            p.launchAngle = json.optDouble("launchAngle", p.launchAngle);
            p.gravityScale = json.optDouble("gravityScale", p.gravityScale);
            p.duration = json.optDouble("duration", p.duration);
            p.arcHeight = json.optDouble("arcHeight", p.arcHeight);
            p.arcMode = json.optString("arcMode", p.arcMode);
            p.easingFunction = json.optString("easingFunction", p.easingFunction);
            p.speed = json.optDouble("speed", p.speed);
            p.acceleration = json.optDouble("acceleration", p.acceleration);
            p.maxDistance = json.optDouble("maxDistance", p.maxDistance);
            p.maxLifetime = json.optDouble("maxLifetime", p.maxLifetime);
            p.alignToVelocity = json.optBoolean("alignToVelocity", p.alignToVelocity);
            p.alignToPath = json.optBoolean("alignToPath", p.alignToPath);
            p.spinSpeed = json.optDouble("spinSpeed", p.spinSpeed);
            p.collisionMode = json.optString("collisionMode", p.collisionMode);
            p.collisionRadius = json.optDouble("collisionRadius", p.collisionRadius);
            p.stopOnImpact = json.optBoolean("stopOnImpact", p.stopOnImpact);
            p.targetMode = json.optString("targetMode", p.targetMode);
            p.turnRate = json.optDouble("turnRate", p.turnRate);
            p.maxTurnAngle = json.optDouble("maxTurnAngle", p.maxTurnAngle);
            p.homingDelay = json.optDouble("homingDelay", p.homingDelay);
            p.homingStrength = json.optDouble("homingStrength", p.homingStrength);
            p.loseTargetBehavior = json.optString("loseTargetBehavior", p.loseTargetBehavior);
            p.outboundDuration = json.optDouble("outboundDuration", p.outboundDuration);
            p.outboundDistance = json.optDouble("outboundDistance", p.outboundDistance);
            p.outboundArcHeight = json.optDouble("outboundArcHeight", p.outboundArcHeight);
            p.returnSpeed = json.optDouble("returnSpeed", p.returnSpeed);
            p.returnDelay = json.optDouble("returnDelay", p.returnDelay);
            p.canHitOnReturn = json.optBoolean("canHitOnReturn", p.canHitOnReturn);
            p.ownerTarget = json.optString("ownerTarget", p.ownerTarget);
            p.catchRadius = json.optDouble("catchRadius", p.catchRadius);
            p.forceMode = json.optString("forceMode", p.forceMode);
            p.massOverride = json.optDouble("massOverride", p.massOverride);
            p.drag = json.optDouble("drag", p.drag);
            p.impactBehavior = json.optString("impactBehavior", p.impactBehavior);
            p.switchToPhysicsOnImpact = json.optBoolean("switchToPhysicsOnImpact", p.switchToPhysicsOnImpact);
            return p;
        }

        private double valueOr(double value, double fallback) {
            return value == 0.0 ? fallback : value;
        }

        private String emptyTo(String value, String fallback) {
            return value == null || value.trim().isEmpty() ? fallback : value.trim();
        }
    }
}
