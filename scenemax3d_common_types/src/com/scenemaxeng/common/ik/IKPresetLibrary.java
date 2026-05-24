package com.scenemaxeng.common.ik;

import java.util.ArrayList;
import java.util.List;

public final class IKPresetLibrary {
    private IKPresetLibrary() {
    }

    public static List<IKDefinition> humanoidPresets() {
        List<IKDefinition> presets = new ArrayList<>();
        presets.add(twoBone("Right Hand Target", "mixamorig:RightArm", "mixamorig:RightForeArm", "mixamorig:RightHand", "RightHandTarget", "RightElbowPole"));
        presets.add(twoBone("Left Hand Target", "mixamorig:LeftArm", "mixamorig:LeftForeArm", "mixamorig:LeftHand", "LeftHandTarget", "LeftElbowPole"));
        presets.add(twoBone("Right Foot IK", "mixamorig:RightUpLeg", "mixamorig:RightLeg", "mixamorig:RightFoot", "RightFootTarget", "RightKneePole"));
        presets.add(twoBone("Left Foot IK", "mixamorig:LeftUpLeg", "mixamorig:LeftLeg", "mixamorig:LeftFoot", "LeftFootTarget", "LeftKneePole"));

        IKDefinition head = IKDefinition.createTemplate("Head Look At", IKLayerDefinition.SOLVER_LOOK_AT);
        IKLayerDefinition headLayer = head.getLayers().get(0);
        headLayer.getAffectedJoints().add("mixamorig:Spine2");
        headLayer.getAffectedJoints().add("mixamorig:Neck");
        headLayer.getAffectedJoints().add("mixamorig:Head");
        headLayer.setTarget("LookAtTarget");
        headLayer.setWeight(0.8f);
        headLayer.setMaxAngle(70f);
        presets.add(head);
        return presets;
    }

    public static IKDefinition twoBone(String name, String root, String middle, String end, String target, String pole) {
        IKDefinition definition = IKDefinition.createTemplate(name, IKLayerDefinition.SOLVER_TWO_BONE);
        IKLayerDefinition layer = definition.getLayers().get(0);
        layer.setRootJoint(root);
        layer.setMiddleJoint(middle);
        layer.setEndJoint(end);
        layer.setTarget(target);
        layer.setPoleTarget(pole);
        return definition;
    }
}
