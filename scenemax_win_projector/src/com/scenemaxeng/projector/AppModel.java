package com.scenemaxeng.projector;

import com.scenemaxeng.common.types.*;

import com.jme3.anim.AnimClip;
import com.jme3.anim.AnimComposer;
import com.jme3.anim.Armature;
import com.jme3.anim.Joint;
import com.jme3.anim.SkinningControl;
import com.jme3.anim.TransformTrack;
import com.jme3.anim.AnimTrack;
import com.jme3.anim.util.HasLocalTransform;
import com.jme3.anim.tween.action.ClipAction;
import com.jme3.animation.*;
import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetManager;
import com.jme3.asset.ModelKey;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.scene.plugins.bvh.SkeletonMapping;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import jme3utilities.wes.AnimationEdit;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AppModel {

    private static final int GLB_MAGIC = 0x46546C67;
    private static final int GLB_JSON_CHUNK = 0x4E4F534A;
    private static final int GLB_BIN_CHUNK = 0x004E4942;

    public float accelerate=0;
    public float steer=0;
    public Node model;
    public Object physicalControl;
    public ResourceSetup resource;
    public Spatial skinningControlNode;
    public CharacterAction currentAction;
    public boolean isStatic;
    public EntityInstBase entityInst;
    private AnimChannel channel;
    private AnimControl control;

    private AnimComposer composer;
    private final Map<String, String> attachedExternalAnimationKeys = new HashMap<>();
    public Transform resetTransform;


    public AppModel(Node m) {
        model=m;
    }

    public AnimChannel getChannel() {
        if(channel==null) {
            channel = control.createChannel();
        }

        return channel;
    }

    public AnimControl getAnimControl() {
        if(control==null) {
            Spatial sp = model.getChild(0);
            control= findAnimationControl(sp);
        }

        return control;
    }

    private AnimControl findAnimationControl(Spatial sp) {

        AnimControl ctl = sp.getControl(AnimControl.class);
        if(ctl!=null) {
            return ctl;
        }

        if(sp instanceof Node) {
            Node nd=(Node)sp;
            for(Spatial spChild:nd.getChildren()) {
                AnimControl ctlChild = findAnimationControl(spChild);
                if(ctlChild!=null) {
                    return ctlChild;
                }
            }
        }

        return null;

    }

    public AnimComposer getAnimComposer() {
        if(composer==null) {
            Spatial sp = model.getChild(0);
            composer= findAnimationComposer(sp);
        }

        return composer;
    }

    public AnimComposer getOrCreateAnimComposerForSkinningControl() {
        AnimComposer existingComposer = getAnimComposer();
        if (existingComposer != null) {
            return existingComposer;
        }

        Spatial target = skinningControlNode;
        if (target == null || target.getControl(SkinningControl.class) == null) {
            target = findSkinningControlNode(model.getChild(0));
        }
        if (target == null) {
            return null;
        }

        skinningControlNode = target;
        composer = new AnimComposer();
        target.addControl(composer);
        return composer;
    }

    private AnimComposer findAnimationComposer(Spatial sp) {

        AnimComposer ctl = sp.getControl(AnimComposer.class);
        if(ctl!=null) {
            return ctl;
        }

        if(sp instanceof Node) {
            Node nd=(Node)sp;
            for(Spatial spChild:nd.getChildren()) {
                AnimComposer ctlChild = findAnimationComposer(spChild);
                if(ctlChild!=null) {
                    return ctlChild;
                }
            }
        }

        return null;

    }

    private Spatial findSkinningControlNode(Spatial sp) {
        if (sp == null) {
            return null;
        }

        SkinningControl ctl = sp.getControl(SkinningControl.class);
        if (ctl != null) {
            return sp;
        }

        if (sp instanceof Node) {
            Node nd = (Node) sp;
            for (Spatial spChild : nd.getChildren()) {
                Spatial ctlChild = findSkinningControlNode(spChild);
                if (ctlChild != null) {
                    return ctlChild;
                }
            }
        }

        return null;
    }

    public SkinningControl getSkinningControl() {
        if (this.skinningControlNode == null) {
            return null;
        }
        return this.skinningControlNode.getControl(SkinningControl.class);
    }

    public Node getJointAttachementNode(String jointName) {

        if (this.resource.isJ3O()) {
            SkeletonControl sc = findSkeletonControl(model);
            if(sc!=null) {
                Node n= sc.getAttachmentsNode(jointName);
                return n;
            }

        } else {

            SkinningControl skinningControl = this.skinningControlNode.getControl(SkinningControl.class);
            if (skinningControl != null) {
                Node n= skinningControl.getAttachmentsNode(jointName);
                return n;

            }

        }

        return null;
    }


    public Vector3f getJointPosition(String jointName) {

        if(this.resource.isJ3O()) {

            Skeleton sk = findSkeleton(model);
            if(sk!=null) {

                Bone b = sk.getBone(jointName);
                if(b!=null) {
                    return b.getLocalPosition();
                }
            }

        } else {

            Spatial sp = model.getChild(0);
            SkinningControl skinningControl = sp.getControl(SkinningControl.class);
            if(skinningControl!=null) {
                Joint j = skinningControl.getArmature().getJoint(jointName);
                if(j!=null) {
                    return j.getLocalTranslation();
                }
            }

        }

        return null;

    }

    public String getJointsList() {

        String joints = "";

        if(this.resource.isJ3O()) {

            Skeleton sk = findSkeleton(model);
            if(sk!=null) {
                int cnt = sk.getBoneCount();
                for(int i=0;i<cnt;++i) {
                    joints += sk.getBone(i).getName() +",";

                }
            }

        } else {

            if(this.skinningControlNode!=null) {
                SkinningControl skinningControl = this.skinningControlNode.getControl(SkinningControl.class);
                if (skinningControl != null) {
                    List<Joint> jts = skinningControl.getArmature().getJointList();
                    for (Joint j : jts) {
                        joints += j.getName() + ",";
                    }
                }
            }

        }

        return joints;

    }


    public String getAnimationsList() {

        String animations = "";

        if(this.resource.isJ3O()) {

            AnimControl ctl = this.getAnimControl();
            if(ctl!=null) {
                Collection<String> anims = ctl.getAnimationNames();
                for (String s : anims) {
                    animations += s + ", ";
                }
            }

        } else {

            AnimComposer ctl = this.getAnimComposer();
            if (ctl != null) {

                for (AnimClip ac : ctl.getAnimClips()) {
                    animations += ac.getName() + ", ";
                }

            }

        }

        if(animations.length()==0) {
            animations = "No animations found for this model";
        }

        return animations;

    }

    public boolean attachExternalAnimation(AssetManager assetManager, AssetsMapping assetsMapping, String animationName) {
        if (assetManager == null || assetsMapping == null || animationName == null || animationName.trim().length() == 0) {
            return false;
        }

        AnimComposer targetComposer = getAnimComposer();
        if (targetComposer == null) {
            targetComposer = getOrCreateAnimComposerForSkinningControl();
        }
        if (targetComposer == null) {
            return false;
        }

        ResourceAnimation resourceAnimation = assetsMapping.getAnimationsIndex().get(animationName.toLowerCase());
        if (resourceAnimation == null) {
            return false;
        }

        String normalizedName = animationName.toLowerCase();
        String animationKey = externalAnimationKey(resourceAnimation);
        if (animationKey.equals(attachedExternalAnimationKeys.get(normalizedName))
                && targetComposer.hasAction(animationName)
                && targetComposer.hasAnimClip(animationName)) {
            return true;
        }

        try {
            ModelKey sourceKey = new ModelKey(resourceAnimation.path);
            assetManager.deleteFromCache(sourceKey);
            Spatial sourceSpatial = null;
            AnimComposer sourceComposer = null;
            try {
                sourceSpatial = assetManager.loadModel(sourceKey);
                sourceComposer = findAnimationComposer(sourceSpatial);
            } catch (Exception loadException) {
                System.out.println("External animation model loader could not expose animation data, trying glTF JSON fallback: "
                        + resourceAnimation.path + " (" + loadException.getMessage() + ")");
            }

            AnimClip retargeted;
            if (sourceComposer == null) {
                retargeted = retargetGltfAnimationByName(assetManager, resourceAnimation.path, resourceAnimation.clipName, animationName);
            } else {
                AnimClip sourceClip = selectClip(sourceComposer, resourceAnimation.clipName, animationName);
                if (sourceClip == null) {
                    System.out.println("External animation clip not found: " + resourceAnimation.clipName + " in " + resourceAnimation.path);
                    return false;
                }
                System.out.println("External animation loaded " + resourceAnimation.path + " clip " + sourceClip.getName()
                        + " with " + sourceClip.getTracks().length + " source tracks");

                SkinningControl sourceSkinning = findSkinningControl(sourceSpatial);
                SkinningControl targetSkinning = getSkinningControl();
                if (targetSkinning == null) {
                    System.out.println("External animation retargeting requires a target armature: " + animationName);
                    return false;
                }

                retargeted = retargetClipByName(sourceClip, animationName);
                if (!hasAnimatedMotionTracks(retargeted) && sourceSkinning != null) {
                    System.out.println("External animation name-based retarget produced no visible rotation tracks, trying Wes retarget: " + animationName);
                    retargeted = retargetClip(sourceClip, sourceSkinning.getArmature(), targetSkinning.getArmature(), animationName);
                }
            }
            if (retargeted == null) {
                System.out.println("External animation could not be retargeted to model skeleton: " + animationName);
                return false;
            }

            if (targetComposer.hasAction(animationName)) {
                targetComposer.removeAction(animationName);
            }
            if (targetComposer.hasAnimClip(retargeted.getName())) {
                targetComposer.removeAnimClip(targetComposer.getAnimClip(retargeted.getName()));
            }
            targetComposer.addAnimClip(retargeted);
            targetComposer.addAction(animationName, new ClipAction(retargeted));
            attachedExternalAnimationKeys.put(normalizedName, animationKey);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private String externalAnimationKey(ResourceAnimation resourceAnimation) {
        String path = resourceAnimation.path == null ? "" : resourceAnimation.path;
        String clipName = resourceAnimation.clipName == null ? "" : resourceAnimation.clipName;
        return path + "|" + clipName;
    }

    private AnimClip retargetGltfAnimationByName(AssetManager assetManager, String assetPath, String clipName, String runtimeName) throws IOException {
        ParsedGltf gltf = readGltfAnimationAsset(assetManager, assetPath);
        if (gltf == null || gltf.animations == null || gltf.nodes == null) {
            System.out.println("External animation GLB has no readable glTF animation JSON: " + assetPath);
            return null;
        }

        JSONObject animation = selectGltfAnimation(gltf.animations, clipName, runtimeName);
        if (animation == null) {
            System.out.println("External animation GLB clip not found: " + clipName + " in " + assetPath);
            return null;
        }

        Map<String, HasLocalTransform> targetJoints = getTargetJointMap();
        if (targetJoints.isEmpty()) {
            System.out.println("External animation retargeting requires a target armature: " + runtimeName);
            return null;
        }

        JSONArray samplers = animation.optJSONArray("samplers");
        JSONArray channels = animation.optJSONArray("channels");
        if (samplers == null || channels == null) {
            return null;
        }

        Map<Integer, ParsedGltfNodeAnimation> nodeAnimations = new HashMap<>();
        for (int i = 0; i < channels.length(); i++) {
            JSONObject channel = channels.optJSONObject(i);
            JSONObject target = channel != null ? channel.optJSONObject("target") : null;
            if (target == null) {
                continue;
            }
            int nodeIndex = target.optInt("node", -1);
            String path = target.optString("path", "");
            JSONObject sampler = samplers.optJSONObject(channel.optInt("sampler", -1));
            if (nodeIndex < 0 || sampler == null) {
                continue;
            }

            ParsedGltfNodeAnimation nodeAnimation = nodeAnimations.computeIfAbsent(nodeIndex,
                    index -> new ParsedGltfNodeAnimation(index, gltf.nodes.optJSONObject(index)));
            float[] times = readFloatAccessor(gltf, sampler.optInt("input", -1));
            int outputAccessor = sampler.optInt("output", -1);
            if ("rotation".equals(path)) {
                nodeAnimation.rotationTimes = times;
                nodeAnimation.rotations = readQuaternionAccessor(gltf, outputAccessor);
            } else if ("translation".equals(path)) {
                nodeAnimation.translationTimes = times;
                nodeAnimation.translations = readVectorAccessor(gltf, outputAccessor);
            } else if ("scale".equals(path)) {
                nodeAnimation.scaleTimes = times;
                nodeAnimation.scales = readVectorAccessor(gltf, outputAccessor);
            }
        }

        int movingTracks = 0;
        for (ParsedGltfNodeAnimation nodeAnimation : nodeAnimations.values()) {
            if (hasRotationMotion(nodeAnimation.rotations) || hasTranslationMotion(nodeAnimation.translations)) {
                movingTracks++;
            }
        }
        boolean fullBodyClip = movingTracks > 12;

        List<AnimTrack> tracks = new ArrayList<>();
        List<String> movingMatches = new ArrayList<>();
        for (ParsedGltfNodeAnimation nodeAnimation : nodeAnimations.values()) {
            if (!hasRotationMotion(nodeAnimation.rotations) && !hasTranslationMotion(nodeAnimation.translations)) {
                continue;
            }

            HasLocalTransform matchingTarget = findMatchingTarget(targetJoints, nodeAnimation.name);
            if (matchingTarget == null) {
                continue;
            }

            float[] times = nodeAnimation.rotationTimes != null && nodeAnimation.rotationTimes.length > 0
                    ? cloneTimes(nodeAnimation.rotationTimes)
                    : cloneTimes(nodeAnimation.translationTimes);
            int frameCount = times == null ? 0 : times.length;
            if (frameCount == 0) {
                continue;
            }

            Quaternion[] rotations = nodeAnimation.rotations == null
                    ? constantRotations(matchingTarget, frameCount)
                    : retargetGltfRotations(nodeAnimation, matchingTarget, frameCount, fullBodyClip);
            Vector3f[] translations = retargetGltfTranslations(nodeAnimation, matchingTarget, frameCount);
            tracks.add(new TransformTrack(matchingTarget, times, translations, rotations, constantScales(matchingTarget, frameCount)));
            movingMatches.add(nodeAnimation.name + "->" + targetName(matchingTarget));
        }

        if (tracks.isEmpty()) {
            System.out.println("External glTF animation retarget matched 0 moving tracks for " + runtimeName);
            return null;
        }

        AnimClip retargeted = new AnimClip(runtimeName);
        retargeted.setTracks(tracks.toArray(new AnimTrack[0]));
        System.out.println("External glTF animation retarget matched " + tracks.size() + " tracks for " + runtimeName
                + " " + summarizeMatches(movingMatches));
        return retargeted;
    }

    private Quaternion[] retargetGltfRotations(ParsedGltfNodeAnimation nodeAnimation, HasLocalTransform target,
                                               int frameCount, boolean fullBodyClip) {
        Quaternion sourceRest = nodeAnimation.restRotation;
        Quaternion targetRest = restRotation(target);
        if (canUseAuthoredLocalRotations(nodeAnimation.name, sourceRest, target)) {
            return authoredLocalRotations(nodeAnimation.rotations, nodeAnimation.restRotation, frameCount);
        }

        Quaternion inverseSourceRest = sourceRest.inverse();
        Quaternion[] rotations = new Quaternion[frameCount];
        for (int i = 0; i < rotations.length; i++) {
            Quaternion sourceRotation = i < nodeAnimation.rotations.length && nodeAnimation.rotations[i] != null
                    ? nodeAnimation.rotations[i]
                    : sourceRest;
            Quaternion sourceDelta = inverseSourceRest.mult(sourceRotation);
            rotations[i] = applyRetargetedRotation(sourceDelta, targetRest, nodeAnimation.name, fullBodyClip);
        }
        return rotations;
    }

    private Vector3f[] retargetGltfTranslations(ParsedGltfNodeAnimation source, HasLocalTransform target, int frameCount) {
        if (!hasTranslationMotion(source.translations) || isRootMotionJoint(source.name)) {
            return constantTranslations(target, frameCount);
        }

        Vector3f targetRest = target.getLocalTransform().getTranslation().clone();
        Vector3f[] translations = new Vector3f[frameCount];
        for (int i = 0; i < translations.length; i++) {
            Vector3f sourceTranslation = i < source.translations.length && source.translations[i] != null
                    ? source.translations[i]
                    : source.restTranslation;
            translations[i] = targetRest.add(sourceTranslation.subtract(source.restTranslation));
        }
        return translations;
    }

    private Quaternion[] constantRotations(HasLocalTransform target, int frameCount) {
        Quaternion rotation = target.getLocalTransform().getRotation().clone();
        Quaternion[] rotations = new Quaternion[frameCount];
        for (int i = 0; i < rotations.length; i++) {
            rotations[i] = rotation.clone();
        }
        return rotations;
    }

    private JSONObject selectGltfAnimation(JSONArray animations, String clipName, String runtimeName) {
        JSONObject fallback = null;
        for (int i = 0; i < animations.length(); i++) {
            JSONObject animation = animations.optJSONObject(i);
            if (animation == null) {
                continue;
            }
            String name = animation.optString("name", "");
            if ((clipName != null && clipName.equals(name)) || (runtimeName != null && runtimeName.equals(name))) {
                return animation;
            }
            if (fallback == null) {
                fallback = animation;
            }
        }
        return fallback;
    }

    private ParsedGltf readGltfAnimationAsset(AssetManager assetManager, String assetPath) throws IOException {
        AssetInfo info = assetManager.locateAsset(new ModelKey(assetPath));
        if (info == null) {
            return null;
        }

        byte[] bytes;
        try (InputStream input = info.openStream()) {
            bytes = input.readAllBytes();
        }
        return parseGlb(bytes);
    }

    private ParsedGltf parseGlb(byte[] bytes) {
        if (bytes == null || bytes.length < 20) {
            return null;
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int magic = buffer.getInt();
        buffer.getInt();
        int length = buffer.getInt();
        if (magic != GLB_MAGIC || length != bytes.length) {
            return null;
        }

        byte[] jsonData = null;
        byte[] binData = null;
        while (buffer.remaining() >= 8) {
            int chunkLength = buffer.getInt();
            int chunkType = buffer.getInt();
            if (chunkLength < 0 || chunkLength > buffer.remaining()) {
                return null;
            }
            byte[] chunk = new byte[chunkLength];
            buffer.get(chunk);
            if (chunkType == GLB_JSON_CHUNK && jsonData == null) {
                jsonData = chunk;
            } else if (chunkType == GLB_BIN_CHUNK && binData == null) {
                binData = chunk;
            }
        }
        if (jsonData == null) {
            return null;
        }

        String jsonText = new String(trimJsonPadding(jsonData), StandardCharsets.UTF_8);
        JSONObject root = new JSONObject(jsonText);
        return new ParsedGltf(root, binData);
    }

    private float[] readFloatAccessor(ParsedGltf gltf, int accessorIndex) {
        float[][] values = readFloatTuples(gltf, accessorIndex, 1);
        float[] result = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = values[i].length == 0 ? 0f : values[i][0];
        }
        return result;
    }

    private Quaternion[] readQuaternionAccessor(ParsedGltf gltf, int accessorIndex) {
        float[][] values = readFloatTuples(gltf, accessorIndex, 4);
        Quaternion[] result = new Quaternion[values.length];
        for (int i = 0; i < values.length; i++) {
            float[] value = values[i];
            result[i] = new Quaternion(value[0], value[1], value[2], value[3]);
        }
        return result;
    }

    private Vector3f[] readVectorAccessor(ParsedGltf gltf, int accessorIndex) {
        float[][] values = readFloatTuples(gltf, accessorIndex, 3);
        Vector3f[] result = new Vector3f[values.length];
        for (int i = 0; i < values.length; i++) {
            float[] value = values[i];
            result[i] = new Vector3f(value[0], value[1], value[2]);
        }
        return result;
    }

    private float[][] readFloatTuples(ParsedGltf gltf, int accessorIndex, int expectedComponents) {
        if (gltf == null || gltf.bin == null || gltf.accessors == null || gltf.bufferViews == null
                || accessorIndex < 0 || accessorIndex >= gltf.accessors.length()) {
            return new float[0][];
        }

        JSONObject accessor = gltf.accessors.optJSONObject(accessorIndex);
        if (accessor == null || accessor.optInt("componentType", -1) != 5126) {
            return new float[0][];
        }
        JSONObject bufferView = gltf.bufferViews.optJSONObject(accessor.optInt("bufferView", -1));
        if (bufferView == null) {
            return new float[0][];
        }

        int componentCount = Math.max(expectedComponents, componentCount(accessor.optString("type", "")));
        int count = accessor.optInt("count", 0);
        int byteOffset = bufferView.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0);
        int stride = bufferView.optInt("byteStride", componentCount * Float.BYTES);
        ByteBuffer buffer = ByteBuffer.wrap(gltf.bin).order(ByteOrder.LITTLE_ENDIAN);
        float[][] result = new float[count][expectedComponents];
        for (int i = 0; i < count; i++) {
            int tupleOffset = byteOffset + i * stride;
            if (tupleOffset < 0 || tupleOffset + componentCount * Float.BYTES > gltf.bin.length) {
                return new float[0][];
            }
            for (int j = 0; j < expectedComponents; j++) {
                result[i][j] = buffer.getFloat(tupleOffset + j * Float.BYTES);
            }
        }
        return result;
    }

    private int componentCount(String type) {
        if ("SCALAR".equals(type)) {
            return 1;
        }
        if ("VEC2".equals(type)) {
            return 2;
        }
        if ("VEC3".equals(type)) {
            return 3;
        }
        if ("VEC4".equals(type)) {
            return 4;
        }
        return 1;
    }

    private byte[] trimJsonPadding(byte[] jsonData) {
        int end = jsonData.length;
        while (end > 0) {
            byte b = jsonData[end - 1];
            if (b == 0 || b == 0x20 || b == '\n' || b == '\r' || b == '\t') {
                end--;
            } else {
                break;
            }
        }
        byte[] trimmed = new byte[end];
        System.arraycopy(jsonData, 0, trimmed, 0, end);
        return trimmed;
    }

    private AnimClip selectClip(AnimComposer sourceComposer, String clipName, String fallbackName) {
        if (clipName != null && sourceComposer.hasAnimClip(clipName)) {
            return sourceComposer.getAnimClip(clipName);
        }
        if (fallbackName != null && sourceComposer.hasAnimClip(fallbackName)) {
            return sourceComposer.getAnimClip(fallbackName);
        }
        Collection<AnimClip> clips = sourceComposer.getAnimClips();
        return clips.isEmpty() ? null : clips.iterator().next();
    }

    private AnimClip retargetClip(AnimClip sourceClip, Armature sourceArmature, Armature targetArmature, String runtimeName) {
        SkeletonMapping mapping = buildSkeletonMapping(sourceArmature, targetArmature);
        if (mapping.countMappings() == 0) {
            return null;
        }

        AnimClip retargeted = AnimationEdit.retargetAnimation(sourceClip, sourceArmature, targetArmature, mapping, runtimeName);
        return retargeted == null ? null : preserveTargetJointProportions(retargeted);
    }

    private AnimClip retargetClipByName(AnimClip sourceClip, String runtimeName) {
        Map<String, HasLocalTransform> targetJoints = getTargetJointMap();
        if (targetJoints.isEmpty()) {
            return null;
        }

        List<AnimTrack> tracks = new ArrayList<>();
        List<String> movingMatches = new ArrayList<>();
        List<String> staticMatches = new ArrayList<>();
        int totalMovingTracks = countMovingTransformTracks(sourceClip);
        boolean fullBodyClip = totalMovingTracks > 12;
        for (AnimTrack track : sourceClip.getTracks()) {
            if (track instanceof TransformTrack) {
                TransformTrack transformTrack = (TransformTrack) track;
                HasLocalTransform sourceTarget = transformTrack.getTarget();
                String sourceName = targetName(sourceTarget);
                if (isAssimpFbxHelper(sourceName) && !isAssimpFbxRuntimeHelper(sourceName)) {
                    continue;
                }
                HasLocalTransform matchingTarget = findMatchingTarget(targetJoints, sourceName);
                if (matchingTarget != null) {
                    String targetName = targetName(matchingTarget);
                    boolean movingTrack = hasRotationMotion(transformTrack.getRotations())
                            || hasTranslationMotion(transformTrack.getTranslations());
                    if (movingTrack) {
                        movingMatches.add(sourceName + "->" + targetName);
                    } else {
                        staticMatches.add(sourceName + "->" + targetName);
                    }
                    if (!movingTrack) {
                        continue;
                    }
                    Quaternion[] rotations = retargetRotations(transformTrack.getRotations(), sourceTarget, matchingTarget, sourceName, fullBodyClip);
                    if (rotations == null || rotations.length == 0) {
                        continue;
                    }
                    float[] times = cloneTimes(transformTrack.getTimes());
                    int frameCount = times != null ? times.length : rotations.length;
                    tracks.add(new TransformTrack(matchingTarget,
                            times,
                            retargetTranslations(transformTrack.getTranslations(), sourceTarget, matchingTarget, frameCount),
                            rotations,
                            constantScales(matchingTarget, frameCount)));
                }
            }
        }

        if (tracks.isEmpty()) {
            System.out.println("External animation name-based retarget matched 0 tracks for " + runtimeName);
            return null;
        }

        AnimClip retargeted = new AnimClip(runtimeName);
        retargeted.setTracks(tracks.toArray(new AnimTrack[0]));
        System.out.println("External animation name-based retarget matched " + tracks.size() + " tracks for " + runtimeName);
        System.out.println("External animation static source tracks were ignored for " + runtimeName);
        System.out.println("External animation moving tracks: " + movingMatches.size() + " " + summarizeMatches(movingMatches));
        System.out.println("External animation static tracks: " + staticMatches.size() + " " + summarizeMatches(staticMatches));
        return retargeted;
    }

    private Quaternion[] retargetRotations(Quaternion[] sourceRotations, HasLocalTransform sourceTarget, HasLocalTransform target,
                                           String sourceName, boolean fullBodyClip) {
        if (sourceRotations == null) {
            return null;
        }

        Quaternion sourceRest = restRotation(sourceTarget);
        if (canUseAuthoredLocalRotations(sourceName, sourceRest, target)) {
            return authoredLocalRotations(sourceRotations, sourceRest, sourceRotations.length);
        }

        Quaternion inverseSourceRest = sourceRest.inverse();
        Quaternion targetRest = restRotation(target);
        Quaternion[] rotations = new Quaternion[sourceRotations.length];
        for (int i = 0; i < sourceRotations.length; i++) {
            Quaternion sourceRotation = sourceRotations[i] == null ? sourceRest : sourceRotations[i];
            Quaternion sourceDelta;
            if (isAssimpFbxRotationHelper(sourceName)) {
                sourceDelta = assimpFbxBoneDelta(sourceTarget, sourceRotation);
            } else {
                sourceDelta = inverseSourceRest.mult(sourceRotation);
            }
            rotations[i] = applyRetargetedRotation(sourceDelta, targetRest, sourceName, fullBodyClip);
        }
        return rotations;
    }

    private Quaternion applyRetargetedRotation(Quaternion sourceDelta, Quaternion targetRest, String sourceName, boolean fullBodyClip) {
        if (isRootMotionJoint(sourceName) && !fullBodyClip) {
            return targetRest.mult(yawOnly(sourceDelta));
        }

        return targetRest.mult(sourceDelta);
    }

    private boolean canUseAuthoredLocalRotations(String sourceName, Quaternion sourceRest, HasLocalTransform target) {
        String targetName = targetName(target);
        if (sourceName == null || targetName == null || isAssimpFbxHelper(sourceName)) {
            return false;
        }
        return isMixamoJoint(sourceName)
                && (sourceName.equals(targetName)
                || sourceName.equalsIgnoreCase(targetName)
                || normalizeJointName(sourceName).equals(normalizeJointName(targetName)))
                && hasCompatibleRestRotation(sourceRest, restRotation(target));
    }

    private boolean hasCompatibleRestRotation(Quaternion sourceRest, Quaternion targetRest) {
        if (sourceRest == null || targetRest == null) {
            return false;
        }
        return Math.abs(sourceRest.dot(targetRest)) > 0.9995f;
    }

    private boolean isMixamoJoint(String jointName) {
        return jointName != null && jointName.toLowerCase(Locale.ROOT).contains("mixamorig");
    }

    private Quaternion[] authoredLocalRotations(Quaternion[] sourceRotations, Quaternion fallbackRotation, int frameCount) {
        Quaternion[] rotations = new Quaternion[frameCount];
        for (int i = 0; i < rotations.length; i++) {
            Quaternion rotation = sourceRotations != null && i < sourceRotations.length && sourceRotations[i] != null
                    ? sourceRotations[i]
                    : fallbackRotation;
            rotations[i] = rotation.clone();
        }
        return rotations;
    }

    private Quaternion assimpFbxBoneDelta(HasLocalTransform sourceTarget, Quaternion sourceRotation) {
        if (!(sourceTarget instanceof Joint)) {
            return sourceRotation.clone();
        }

        Joint rotationHelper = (Joint) sourceTarget;
        Joint actualBone = findAssimpFbxActualBone(rotationHelper);
        Quaternion animatedBoneRotation = assimpFbxChainRotation(rotationHelper, sourceRotation, actualBone);
        Quaternion restBoneRotation = assimpFbxChainRotation(rotationHelper, rotationHelper.getLocalTransform().getRotation(), actualBone);
        return restBoneRotation.inverse().mult(animatedBoneRotation);
    }

    private Quaternion assimpFbxChainRotation(Joint rotationHelper, Quaternion rotationHelperRotation, Joint actualBone) {
        Quaternion chainRotation = rotationHelperRotation.clone();
        Joint parent = rotationHelper.getParent();
        while (parent != null && isAssimpFbxHelper(parent.getName())) {
            chainRotation = parent.getLocalTransform().getRotation().mult(chainRotation);
            parent = parent.getParent();
        }
        if (actualBone != null) {
            chainRotation = chainRotation.mult(actualBone.getLocalTransform().getRotation());
        }
        return chainRotation;
    }

    private Joint findAssimpFbxActualBone(Joint rotationHelper) {
        if (rotationHelper == null) {
            return null;
        }
        for (Joint child : rotationHelper.getChildren()) {
            if (!isAssimpFbxHelper(child.getName())) {
                return child;
            }
        }
        return null;
    }

    private Quaternion assimpFbxHelperBasis(HasLocalTransform sourceTarget, String sourceName) {
        if (!isAssimpFbxRotationHelper(sourceName) || !(sourceTarget instanceof Joint)) {
            return Quaternion.IDENTITY.clone();
        }

        Quaternion basis = Quaternion.IDENTITY.clone();
        Joint parent = ((Joint) sourceTarget).getParent();
        while (parent != null && isAssimpFbxHelper(parent.getName())) {
            basis = parent.getLocalTransform().getRotation().mult(basis);
            parent = parent.getParent();
        }
        return basis;
    }

    private Quaternion yawOnly(Quaternion rotation) {
        Vector3f forward = rotation.mult(Vector3f.UNIT_Z);
        forward.y = 0f;
        if (forward.lengthSquared() < 0.000001f) {
            return Quaternion.IDENTITY.clone();
        }
        forward.normalizeLocal();
        Quaternion yaw = new Quaternion();
        yaw.lookAt(forward, Vector3f.UNIT_Y);
        return yaw;
    }

    private Quaternion restRotation(HasLocalTransform target) {
        if (target instanceof Joint) {
            return ((Joint) target).getInitialTransform().getRotation().clone();
        }
        return target.getLocalTransform().getRotation().clone();
    }

    private AnimClip preserveTargetJointProportions(AnimClip retargeted) {
        List<AnimTrack> tracks = new ArrayList<>();
        for (AnimTrack track : retargeted.getTracks()) {
            if (track instanceof TransformTrack) {
                TransformTrack transformTrack = (TransformTrack) track;
                HasLocalTransform target = transformTrack.getTarget();
                float[] times = cloneTimes(transformTrack.getTimes());
                int frameCount = times != null ? times.length : transformTrack.getRotations().length;
                tracks.add(new TransformTrack(target,
                        times,
                        constantTranslations(target, frameCount),
                        transformTrack.getRotations(),
                        constantScales(target, frameCount)));
            } else {
                tracks.add(track);
            }
        }

        AnimClip stableClip = new AnimClip(retargeted.getName());
        stableClip.setTracks(tracks.toArray(new AnimTrack[0]));
        return stableClip;
    }

    private float[] cloneTimes(float[] times) {
        return times == null ? null : times.clone();
    }

    private boolean hasAnimatedMotionTracks(AnimClip clip) {
        if (clip == null) {
            return false;
        }

        for (AnimTrack track : clip.getTracks()) {
            if (track instanceof TransformTrack) {
                TransformTrack transformTrack = (TransformTrack) track;
                if (hasRotationMotion(transformTrack.getRotations())
                        || hasTranslationMotion(transformTrack.getTranslations())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasRotationMotion(Quaternion[] rotations) {
        if (rotations == null || rotations.length < 2 || rotations[0] == null) {
            return false;
        }

        Quaternion first = rotations[0];
        for (int i = 1; i < rotations.length; i++) {
            Quaternion current = rotations[i];
            if (current != null && Math.abs(first.dot(current)) < 0.9995f) {
                return true;
            }
        }
        return false;
    }

    private boolean hasTranslationMotion(Vector3f[] translations) {
        if (translations == null || translations.length < 2 || translations[0] == null) {
            return false;
        }

        Vector3f first = translations[0];
        for (int i = 1; i < translations.length; i++) {
            Vector3f current = translations[i];
            if (current != null && first.distanceSquared(current) > 0.000001f) {
                return true;
            }
        }
        return false;
    }

    private int countMovingTransformTracks(AnimClip clip) {
        if (clip == null || clip.getTracks() == null) {
            return 0;
        }

        int count = 0;
        for (AnimTrack track : clip.getTracks()) {
            if (track instanceof TransformTrack) {
                TransformTrack transformTrack = (TransformTrack) track;
                if (hasRotationMotion(transformTrack.getRotations())
                        || hasTranslationMotion(transformTrack.getTranslations())) {
                    count++;
                }
            }
        }
        return count;
    }

    private boolean isRootMotionJoint(String jointName) {
        String normalized = normalizeJointName(jointName);
        return normalized.equals("root")
                || normalized.equals("armature")
                || normalized.equals("scene")
                || normalized.equals("hips")
                || normalized.equals("hip")
                || normalized.equals("pelvis");
    }

    private String summarizeMatches(List<String> matches) {
        if (matches.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder("[");
        int count = Math.min(matches.size(), 18);
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(matches.get(i));
        }
        if (matches.size() > count) {
            builder.append(", ...");
        }
        builder.append("]");
        return builder.toString();
    }

    private Vector3f[] constantTranslations(HasLocalTransform target, int frameCount) {
        Vector3f translation = target.getLocalTransform().getTranslation().clone();
        Vector3f[] translations = new Vector3f[frameCount];
        for (int i = 0; i < translations.length; i++) {
            translations[i] = translation.clone();
        }
        return translations;
    }

    private Vector3f[] retargetTranslations(Vector3f[] sourceTranslations, HasLocalTransform sourceTarget, HasLocalTransform target, int frameCount) {
        if (!hasTranslationMotion(sourceTranslations) || isRootMotionJoint(targetName(sourceTarget))) {
            return constantTranslations(target, frameCount);
        }

        Vector3f sourceRest = sourceTarget.getLocalTransform().getTranslation().clone();
        Vector3f targetRest = target.getLocalTransform().getTranslation().clone();
        Vector3f[] translations = new Vector3f[frameCount];
        for (int i = 0; i < translations.length; i++) {
            Vector3f sourceTranslation = i < sourceTranslations.length && sourceTranslations[i] != null
                    ? sourceTranslations[i]
                    : sourceRest;
            translations[i] = targetRest.add(sourceTranslation.subtract(sourceRest));
        }
        return translations;
    }

    private Vector3f[] constantScales(HasLocalTransform target, int frameCount) {
        Vector3f scale = target.getLocalTransform().getScale().clone();
        Vector3f[] scales = new Vector3f[frameCount];
        for (int i = 0; i < scales.length; i++) {
            scales[i] = scale.clone();
        }
        return scales;
    }

    private SkeletonMapping buildSkeletonMapping(Armature sourceArmature, Armature targetArmature) {
        Map<String, Joint> sourceJoints = buildJointMap(sourceArmature);
        SkeletonMapping mapping = new SkeletonMapping();
        int mappedCount = 0;
        for (Joint targetJoint : targetArmature.getJointList()) {
            Joint sourceJoint = findMatchingJoint(sourceJoints, targetJoint.getName());
            if (sourceJoint != null) {
                mapping.map(sourceJoint.getName(), targetJoint.getName());
                mappedCount++;
            }
        }
        System.out.println("External animation skeleton mappings: " + mappedCount + " of " + targetArmature.getJointCount());
        return mapping;
    }

    private Map<String, Joint> buildJointMap(Armature armature) {
        Map<String, Joint> jointsByName = new HashMap<>();
        for (Joint joint : armature.getJointList()) {
            addJointAlias(jointsByName, joint.getName(), joint);
        }
        return jointsByName;
    }

    private Joint findMatchingJoint(Map<String, Joint> jointsByName, String targetName) {
        if (targetName == null) {
            return null;
        }

        Joint joint = jointsByName.get(targetName);
        if (joint != null) {
            return joint;
        }

        joint = jointsByName.get(targetName.toLowerCase());
        if (joint != null) {
            return joint;
        }

        joint = jointsByName.get(stripJointNamespace(targetName));
        if (joint != null) {
            return joint;
        }

        return jointsByName.get(normalizeJointName(targetName));
    }

    private Map<String, HasLocalTransform> getTargetJointMap() {
        Map<String, HasLocalTransform> jointsByName = new HashMap<>();
        SkinningControl skinningControl = getSkinningControl();
        if (skinningControl == null || skinningControl.getArmature() == null) {
            return jointsByName;
        }

        for (Joint joint : skinningControl.getArmature().getJointList()) {
            if (joint.getName() != null) {
                addJointAlias(jointsByName, joint.getName(), joint);
            }
        }
        return jointsByName;
    }

    private <T extends HasLocalTransform> void addJointAlias(Map<String, T> jointsByName, String jointName, T joint) {
        if (jointName == null || joint == null) {
            return;
        }

        jointsByName.put(jointName, joint);
        jointsByName.put(jointName.toLowerCase(), joint);

        String withoutNamespace = stripJointNamespace(jointName);
        jointsByName.put(withoutNamespace, joint);
        jointsByName.put(withoutNamespace.toLowerCase(), joint);

        String normalized = normalizeJointName(jointName);
        if (!normalized.isEmpty()) {
            jointsByName.put(normalized, joint);
        }
    }

    private String targetName(HasLocalTransform target) {
        if (target instanceof Joint) {
            return ((Joint) target).getName();
        }
        return target == null ? null : target.toString();
    }

    private HasLocalTransform findMatchingTarget(Map<String, HasLocalTransform> targetJoints, String sourceName) {
        if (sourceName == null) {
            return null;
        }

        HasLocalTransform target = targetJoints.get(sourceName);
        if (target != null) {
            return target;
        }

        target = targetJoints.get(sourceName.toLowerCase());
        if (target != null) {
            return target;
        }

        target = targetJoints.get(stripJointNamespace(sourceName));
        if (target != null) {
            return target;
        }

        target = targetJoints.get(stripAssimpFbxHelper(stripJointNamespace(sourceName)));
        if (target != null) {
            return target;
        }

        return targetJoints.get(normalizeJointName(sourceName));
    }

    private String stripJointNamespace(String name) {
        if (name == null) {
            return "";
        }
        int colon = name.lastIndexOf(':');
        return colon >= 0 && colon + 1 < name.length() ? name.substring(colon + 1) : name;
    }

    private String normalizeJointName(String name) {
        return stripAssimpFbxHelper(stripJointNamespace(name)).toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private boolean isAssimpFbxHelper(String name) {
        return name != null && name.contains("_$AssimpFbx$_");
    }

    private boolean isAssimpFbxRotationHelper(String name) {
        return name != null && name.endsWith("_$AssimpFbx$_Rotation");
    }

    private boolean isAssimpFbxTranslationHelper(String name) {
        return name != null && name.endsWith("_$AssimpFbx$_Translation");
    }

    private boolean isAssimpFbxRuntimeHelper(String name) {
        return isAssimpFbxRotationHelper(name) || isAssimpFbxTranslationHelper(name);
    }

    private String stripAssimpFbxHelper(String name) {
        if (name == null) {
            return "";
        }
        int marker = name.indexOf("_$AssimpFbx$_");
        return marker >= 0 ? name.substring(0, marker) : name;
    }

    private SkinningControl findSkinningControl(Spatial spatial) {
        SkinningControl control = spatial.getControl(SkinningControl.class);
        if (control != null) {
            return control;
        }

        if (spatial instanceof Node) {
            Node node = (Node) spatial;
            for (Spatial child : node.getChildren()) {
                control = findSkinningControl(child);
                if (control != null) {
                    return control;
                }
            }
        }

        return null;
    }

    private SkeletonControl findSkeletonControl (Spatial spatial) {

        SkeletonControl control = spatial.getControl(SkeletonControl.class);
        if (control != null) {
            return control;
        }

        if (spatial instanceof Node) {
            Node node = (Node) spatial;
            for(Spatial s:node.getChildren()) {
                control = findSkeletonControl(s);
                if(control!=null) {
                    return control;
                }
            }

        }

        return null;
    }

    public Skeleton findSkeleton(Spatial spatial) {
        Skeleton r = null;
        final SkeletonControl control = spatial.getControl(SkeletonControl.class);
        if (control != null) {
            r = control.getSkeleton();
        }
        if (r == null && spatial instanceof Node) {
            Node node = (Node) spatial;
            for (int i = 0; r == null && i < node.getQuantity(); i++) {
                Spatial child = node.getChild(i);
                r = findSkeleton(child);
            }
        }
        return r;
    }

    private static final class ParsedGltf {
        final JSONObject root;
        final byte[] bin;
        final JSONArray nodes;
        final JSONArray accessors;
        final JSONArray bufferViews;
        final JSONArray animations;

        ParsedGltf(JSONObject root, byte[] bin) {
            this.root = root;
            this.bin = bin;
            this.nodes = root.optJSONArray("nodes");
            this.accessors = root.optJSONArray("accessors");
            this.bufferViews = root.optJSONArray("bufferViews");
            this.animations = root.optJSONArray("animations");
        }
    }

    private static final class ParsedGltfNodeAnimation {
        final int nodeIndex;
        final String name;
        final Quaternion restRotation;
        final Vector3f restTranslation;
        float[] rotationTimes;
        Quaternion[] rotations;
        float[] translationTimes;
        Vector3f[] translations;
        float[] scaleTimes;
        Vector3f[] scales;

        ParsedGltfNodeAnimation(int nodeIndex, JSONObject node) {
            this.nodeIndex = nodeIndex;
            this.name = node == null ? "" : node.optString("name", "");
            this.restRotation = readRotation(node);
            this.restTranslation = readVector(node == null ? null : node.optJSONArray("translation"), new Vector3f(0f, 0f, 0f));
        }

        private static Quaternion readRotation(JSONObject node) {
            JSONArray rotation = node == null ? null : node.optJSONArray("rotation");
            if (rotation == null || rotation.length() < 4) {
                return Quaternion.IDENTITY.clone();
            }
            return new Quaternion(
                    (float) rotation.optDouble(0, 0.0),
                    (float) rotation.optDouble(1, 0.0),
                    (float) rotation.optDouble(2, 0.0),
                    (float) rotation.optDouble(3, 1.0));
        }

        private static Vector3f readVector(JSONArray values, Vector3f defaults) {
            if (values == null || values.length() < 3) {
                return defaults.clone();
            }
            return new Vector3f(
                    (float) values.optDouble(0, defaults.x),
                    (float) values.optDouble(1, defaults.y),
                    (float) values.optDouble(2, defaults.z));
        }
    }

}
