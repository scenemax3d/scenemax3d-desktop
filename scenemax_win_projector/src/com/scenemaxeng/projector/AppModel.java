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
import com.jme3.asset.AssetManager;
import com.jme3.asset.ModelKey;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.scene.plugins.bvh.SkeletonMapping;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import jme3utilities.wes.AnimationEdit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppModel {

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
            return false;
        }

        ResourceAnimation resourceAnimation = assetsMapping.getAnimationsIndex().get(animationName.toLowerCase());
        if (resourceAnimation == null) {
            System.out.println("External animation resource not found: " + animationName);
            return false;
        }

        try {
            ModelKey sourceKey = new ModelKey(resourceAnimation.path);
            assetManager.deleteFromCache(sourceKey);
            Spatial sourceSpatial = assetManager.loadModel(sourceKey);
            AnimComposer sourceComposer = findAnimationComposer(sourceSpatial);
            if (sourceComposer == null) {
                System.out.println("External animation has no AnimComposer: " + resourceAnimation.path);
                return false;
            }

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

            AnimClip retargeted = retargetClipByName(sourceClip, animationName);
            if (!hasAnimatedMotionTracks(retargeted) && sourceSkinning != null) {
                System.out.println("External animation name-based retarget produced no visible rotation tracks, trying Wes retarget: " + animationName);
                retargeted = retargetClip(sourceClip, sourceSkinning.getArmature(), targetSkinning.getArmature(), animationName);
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
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
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


}
