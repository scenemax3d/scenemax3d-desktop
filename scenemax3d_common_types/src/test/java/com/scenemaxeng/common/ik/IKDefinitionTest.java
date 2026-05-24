package com.scenemaxeng.common.ik;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IKDefinitionTest {
    @Test
    public void serializesTwoBoneDefinitionWithChainAndTargets() {
        IKDefinition definition = IKPresetLibrary.twoBone(
                "Right Hand Grip",
                "mixamorig:RightArm",
                "mixamorig:RightForeArm",
                "mixamorig:RightHand",
                "WeaponGripTarget",
                "RightElbowPole");

        JSONObject json = definition.toJSON();
        IKDefinition loaded = IKDefinition.fromJSON(json);
        IKLayerDefinition layer = loaded.getLayers().get(0);

        assertEquals("SceneMaxIKDefinition", json.getString("type"));
        assertEquals(IKLayerDefinition.SOLVER_TWO_BONE, layer.getSolverType());
        assertEquals("mixamorig:RightArm", layer.getRootJoint());
        assertEquals("mixamorig:RightForeArm", layer.getMiddleJoint());
        assertEquals("mixamorig:RightHand", layer.getEndJoint());
        assertEquals("WeaponGripTarget", layer.getTarget());
        assertEquals("RightElbowPole", layer.getPoleTarget());
        assertTrue(loaded.validate().isValid());
    }

    @Test
    public void validationRequiresTargetForNonFootLayers() {
        IKDefinition definition = IKDefinition.createTemplate("Broken", IKLayerDefinition.SOLVER_TWO_BONE);
        IKLayerDefinition layer = definition.getLayers().get(0);
        layer.setRootJoint("UpperArm");
        layer.setMiddleJoint("Forearm");
        layer.setEndJoint("Hand");

        IKValidationResult result = definition.validate();

        assertFalse(result.isValid());
        assertTrue(result.toJSONArray().toString().contains("Target object is required"));
    }
}
