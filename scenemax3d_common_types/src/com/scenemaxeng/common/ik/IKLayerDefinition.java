package com.scenemaxeng.common.ik;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class IKLayerDefinition {
    public static final String SOLVER_TWO_BONE = "TwoBoneIK";
    public static final String SOLVER_LOOK_AT = "LookAtIK";
    public static final String SOLVER_FABRIK = "FABRIK";
    public static final String SOLVER_FOOT = "FootIK";
    public static final String SOLVER_AIM = "AimIK";

    private String id = "";
    private String name = "IK Layer";
    private String solverType = SOLVER_TWO_BONE;
    private boolean enabled = true;
    private float weight = 1.0f;
    private int priority = 0;
    private float blendTime = 0.0f;
    private boolean debug = false;

    private String rootJoint = "";
    private String middleJoint = "";
    private String endJoint = "";
    private String startJoint = "";
    private final List<String> affectedJoints = new ArrayList<>();
    private String target = "";
    private String poleTarget = "";
    private boolean allowStretch = false;
    private float maxStretch = 1.05f;
    private float rotationSpeed = 20.0f;
    private float maxAngle = 90.0f;
    private float smoothing = 0.15f;
    private int iterations = 8;
    private float tolerance = 0.01f;
    private float groundDistance = 1.0f;
    private float slopeAlignmentStrength = 0.75f;
    private float pelvisCompensation = 0.5f;
    private float blendSpeed = 10.0f;
    private final float[] positionOffset = new float[] {0f, 0f, 0f};
    private final float[] rotationOffset = new float[] {0f, 0f, 0f};
    private final float[] aimAxis = new float[] {0f, 0f, 1f};

    public JSONObject toJSON() {
        JSONObject chain = new JSONObject()
                .put("root", rootJoint)
                .put("middle", middleJoint)
                .put("end", endJoint)
                .put("start", startJoint);

        JSONArray affected = new JSONArray();
        for (String joint : affectedJoints) {
            affected.put(joint);
        }

        return new JSONObject()
                .put("id", id)
                .put("name", name)
                .put("solverType", solverType)
                .put("enabled", enabled)
                .put("weight", weight)
                .put("priority", priority)
                .put("blendTime", blendTime)
                .put("debug", debug)
                .put("chain", chain)
                .put("affectedJoints", affected)
                .put("target", target)
                .put("poleTarget", poleTarget)
                .put("allowStretch", allowStretch)
                .put("maxStretch", maxStretch)
                .put("rotationSpeed", rotationSpeed)
                .put("maxAngle", maxAngle)
                .put("smoothing", smoothing)
                .put("iterations", iterations)
                .put("tolerance", tolerance)
                .put("groundDistance", groundDistance)
                .put("slopeAlignmentStrength", slopeAlignmentStrength)
                .put("pelvisCompensation", pelvisCompensation)
                .put("blendSpeed", blendSpeed)
                .put("positionOffset", floatArray(positionOffset))
                .put("rotationOffset", floatArray(rotationOffset))
                .put("aimAxis", floatArray(aimAxis));
    }

    public static IKLayerDefinition fromJSON(JSONObject json) {
        IKLayerDefinition layer = new IKLayerDefinition();
        if (json == null) {
            return layer;
        }
        layer.id = json.optString("id", layer.id);
        layer.name = json.optString("name", layer.name);
        layer.solverType = json.optString("solverType", layer.solverType);
        layer.enabled = json.optBoolean("enabled", layer.enabled);
        layer.weight = (float) json.optDouble("weight", layer.weight);
        layer.priority = json.optInt("priority", layer.priority);
        layer.blendTime = (float) json.optDouble("blendTime", layer.blendTime);
        layer.debug = json.optBoolean("debug", layer.debug);
        layer.target = json.optString("target", layer.target);
        layer.poleTarget = json.optString("poleTarget", layer.poleTarget);
        layer.allowStretch = json.optBoolean("allowStretch", layer.allowStretch);
        layer.maxStretch = (float) json.optDouble("maxStretch", layer.maxStretch);
        layer.rotationSpeed = (float) json.optDouble("rotationSpeed", layer.rotationSpeed);
        layer.maxAngle = (float) json.optDouble("maxAngle", layer.maxAngle);
        layer.smoothing = (float) json.optDouble("smoothing", layer.smoothing);
        layer.iterations = json.optInt("iterations", layer.iterations);
        layer.tolerance = (float) json.optDouble("tolerance", layer.tolerance);
        layer.groundDistance = (float) json.optDouble("groundDistance", layer.groundDistance);
        layer.slopeAlignmentStrength = (float) json.optDouble("slopeAlignmentStrength", layer.slopeAlignmentStrength);
        layer.pelvisCompensation = (float) json.optDouble("pelvisCompensation", layer.pelvisCompensation);
        layer.blendSpeed = (float) json.optDouble("blendSpeed", layer.blendSpeed);

        JSONObject chain = json.optJSONObject("chain");
        if (chain != null) {
            layer.rootJoint = chain.optString("root", layer.rootJoint);
            layer.middleJoint = chain.optString("middle", layer.middleJoint);
            layer.endJoint = chain.optString("end", layer.endJoint);
            layer.startJoint = chain.optString("start", layer.startJoint);
        } else {
            layer.rootJoint = json.optString("rootJoint", layer.rootJoint);
            layer.middleJoint = json.optString("middleJoint", layer.middleJoint);
            layer.endJoint = json.optString("endJoint", layer.endJoint);
            layer.startJoint = json.optString("startJoint", layer.startJoint);
        }

        layer.affectedJoints.clear();
        JSONArray affected = json.optJSONArray("affectedJoints");
        if (affected != null) {
            for (int i = 0; i < affected.length(); i++) {
                String value = affected.optString(i, "").trim();
                if (!value.isEmpty()) {
                    layer.affectedJoints.add(value);
                }
            }
        }
        readFloatArray(json.optJSONArray("positionOffset"), layer.positionOffset);
        readFloatArray(json.optJSONArray("rotationOffset"), layer.rotationOffset);
        readFloatArray(json.optJSONArray("aimAxis"), layer.aimAxis);
        return layer;
    }

    public void validate(IKValidationResult result, String prefix) {
        String fieldPrefix = prefix == null ? "layers" : prefix;
        if (solverType == null || solverType.trim().isEmpty()) {
            result.addError(fieldPrefix + ".solverType", "Solver type is required.");
        }
        if (weight < 0f || weight > 1f) {
            result.addWarning(fieldPrefix + ".weight", "Weight should be between 0 and 1.");
        }
        if (SOLVER_TWO_BONE.equalsIgnoreCase(solverType) || SOLVER_FOOT.equalsIgnoreCase(solverType)) {
            require(result, fieldPrefix + ".chain.root", rootJoint, "Root joint is required.");
            require(result, fieldPrefix + ".chain.middle", middleJoint, "Middle joint is required.");
            require(result, fieldPrefix + ".chain.end", endJoint, "End joint is required.");
        }
        if (SOLVER_LOOK_AT.equalsIgnoreCase(solverType) || SOLVER_AIM.equalsIgnoreCase(solverType)) {
            if (affectedJoints.isEmpty() && (endJoint == null || endJoint.trim().isEmpty())) {
                result.addError(fieldPrefix + ".affectedJoints", "At least one affected joint is required.");
            }
        }
        if (SOLVER_FABRIK.equalsIgnoreCase(solverType)) {
            require(result, fieldPrefix + ".chain.start", startJoint, "Start joint is required.");
            require(result, fieldPrefix + ".chain.end", endJoint, "End joint is required.");
        }
        if (!SOLVER_FOOT.equalsIgnoreCase(solverType)) {
            require(result, fieldPrefix + ".target", target, "Target object is required.");
        }
    }

    private static void require(IKValidationResult result, String field, String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            result.addError(field, message);
        }
    }

    private static JSONArray floatArray(float[] values) {
        JSONArray array = new JSONArray();
        for (float value : values) {
            array.put(value);
        }
        return array;
    }

    private static void readFloatArray(JSONArray array, float[] target) {
        if (array == null) {
            return;
        }
        for (int i = 0; i < target.length && i < array.length(); i++) {
            target[i] = (float) array.optDouble(i, target[i]);
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSolverType() { return solverType; }
    public void setSolverType(String solverType) { this.solverType = solverType; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public float getWeight() { return weight; }
    public void setWeight(float weight) { this.weight = weight; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public float getBlendTime() { return blendTime; }
    public void setBlendTime(float blendTime) { this.blendTime = blendTime; }
    public boolean isDebug() { return debug; }
    public void setDebug(boolean debug) { this.debug = debug; }
    public String getRootJoint() { return rootJoint; }
    public void setRootJoint(String rootJoint) { this.rootJoint = rootJoint; }
    public String getMiddleJoint() { return middleJoint; }
    public void setMiddleJoint(String middleJoint) { this.middleJoint = middleJoint; }
    public String getEndJoint() { return endJoint; }
    public void setEndJoint(String endJoint) { this.endJoint = endJoint; }
    public String getStartJoint() { return startJoint; }
    public void setStartJoint(String startJoint) { this.startJoint = startJoint; }
    public List<String> getAffectedJoints() { return affectedJoints; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getPoleTarget() { return poleTarget; }
    public void setPoleTarget(String poleTarget) { this.poleTarget = poleTarget; }
    public boolean isAllowStretch() { return allowStretch; }
    public void setAllowStretch(boolean allowStretch) { this.allowStretch = allowStretch; }
    public float getMaxStretch() { return maxStretch; }
    public void setMaxStretch(float maxStretch) { this.maxStretch = maxStretch; }
    public float getRotationSpeed() { return rotationSpeed; }
    public void setRotationSpeed(float rotationSpeed) { this.rotationSpeed = rotationSpeed; }
    public float getMaxAngle() { return maxAngle; }
    public void setMaxAngle(float maxAngle) { this.maxAngle = maxAngle; }
    public float getSmoothing() { return smoothing; }
    public void setSmoothing(float smoothing) { this.smoothing = smoothing; }
    public int getIterations() { return iterations; }
    public void setIterations(int iterations) { this.iterations = iterations; }
    public float getTolerance() { return tolerance; }
    public void setTolerance(float tolerance) { this.tolerance = tolerance; }
    public float getGroundDistance() { return groundDistance; }
    public void setGroundDistance(float groundDistance) { this.groundDistance = groundDistance; }
    public float getSlopeAlignmentStrength() { return slopeAlignmentStrength; }
    public void setSlopeAlignmentStrength(float slopeAlignmentStrength) { this.slopeAlignmentStrength = slopeAlignmentStrength; }
    public float getPelvisCompensation() { return pelvisCompensation; }
    public void setPelvisCompensation(float pelvisCompensation) { this.pelvisCompensation = pelvisCompensation; }
    public float getBlendSpeed() { return blendSpeed; }
    public void setBlendSpeed(float blendSpeed) { this.blendSpeed = blendSpeed; }
    public float[] getPositionOffset() { return positionOffset; }
    public float[] getRotationOffset() { return rotationOffset; }
    public float[] getAimAxis() { return aimAxis; }
}
