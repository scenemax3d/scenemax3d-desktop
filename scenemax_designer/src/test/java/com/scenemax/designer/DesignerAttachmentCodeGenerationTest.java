package com.scenemax.designer;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DesignerAttachmentCodeGenerationTest {

    @Test
    public void emitsAttachCommandAfterEntityCreation() throws Exception {
        Path tempDir = Files.createTempDirectory("designer-attachment-code");
        File smdesign = tempDir.resolve("scene.smdesign").toFile();

        DesignerEntity parent = new DesignerEntity("player1", DesignerEntityType.MODEL);
        parent.setResourcePath("fighter1_native");
        parent.setJointMapping("mixamorig:Head");
        parent.setSceneNode(new Node("player1"));

        DesignerEntity child = new DesignerEntity("player1_head_collider", DesignerEntityType.SPHERE);
        Node childNode = new Node("player1_head_collider");
        childNode.setLocalTranslation(0f, 0.5f, 0f);
        child.setSceneNode(childNode);
        child.setAttachTo("player1.\"mixamorig:Head\"");

        DesignerDocument.saveCodeFile(
                smdesign,
                Arrays.asList(parent, child),
                new Vector3f(0, 2, 10),
                new Quaternion(0, 1, 0, 0),
                "");

        String code = Files.readString(DesignerDocument.getCodeFile(smdesign).toPath(), StandardCharsets.UTF_8);
        int createIndex = code.indexOf("player1_head_collider => sphere");
        int attachIndex = code.indexOf("player1_head_collider.attach to player1.\"mixamorig:Head\": pos (0.0,0.5,0.0)");
        assertTrue("child creation command should be present", createIndex >= 0);
        assertTrue("attach command should be emitted after creation", attachIndex > createIndex);
    }

    @Test
    public void emitsMultiplayerAttributeForModelEntities() throws Exception {
        Path tempDir = Files.createTempDirectory("designer-multiplayer-code");
        File smdesign = tempDir.resolve("scene.smdesign").toFile();

        DesignerEntity horse = new DesignerEntity("horse", DesignerEntityType.MODEL);
        horse.setResourcePath("horse1_native");
        horse.setMultiplayerEntity(true);
        horse.setModelCollisionShape("none");
        Node horseNode = new Node("horse");
        horseNode.setLocalTranslation(0.48224258f, 0f, 1.1645527f);
        horseNode.setLocalScale(3.7f);
        horse.setSceneNode(horseNode);

        DesignerDocument.saveCodeFile(
                smdesign,
                Arrays.asList(horse),
                new Vector3f(0, 2, 10),
                new Quaternion(0, 1, 0, 0),
                "");

        String code = Files.readString(DesignerDocument.getCodeFile(smdesign).toPath(), StandardCharsets.UTF_8);
        assertTrue(code.contains("horse => horse1_native: multiplayer, pos (0.48224258,0.0,1.1645527), scale 3.7, collision shape none async"));
    }

    @Test
    public void emitsMultiplayerAttributeForPrimitiveEntities() throws Exception {
        Path tempDir = Files.createTempDirectory("designer-primitive-multiplayer-code");
        File smdesign = tempDir.resolve("scene.smdesign").toFile();

        DesignerEntity box = new DesignerEntity("crate", DesignerEntityType.BOX);
        box.setSizeX(1f);
        box.setSizeY(1.5f);
        box.setSizeZ(2f);
        box.setMultiplayerEntity(true);
        Node boxNode = new Node("crate");
        boxNode.setLocalTranslation(1f, 2f, 3f);
        box.setSceneNode(boxNode);

        DesignerEntity sphere = new DesignerEntity("orb", DesignerEntityType.SPHERE);
        sphere.setRadius(0.75f);
        sphere.setMultiplayerEntity(true);
        Node sphereNode = new Node("orb");
        sphereNode.setLocalTranslation(4f, 5f, 6f);
        sphere.setSceneNode(sphereNode);

        DesignerDocument.saveCodeFile(
                smdesign,
                Arrays.asList(box, sphere),
                new Vector3f(0, 2, 10),
                new Quaternion(0, 1, 0, 0),
                "");

        String code = Files.readString(DesignerDocument.getCodeFile(smdesign).toPath(), StandardCharsets.UTF_8);
        assertTrue(code.contains("crate => box : multiplayer, size (2.0,3.0,4.0), pos (1.0,2.0,3.0)"));
        assertTrue(code.contains("orb => sphere : multiplayer, pos (4.0,5.0,6.0), radius 0.75"));
    }

    @Test
    public void designerAttachmentPreviewCompensatesForParentScaleLikeRuntime() {
        DesignerApp app = new DesignerApp();

        DesignerEntity parent = new DesignerEntity("model_1", DesignerEntityType.MODEL);
        Node parentNode = new Node("model_1");
        parentNode.setLocalTranslation(0.48224258f, 0f, 1.1645527f);
        parentNode.setLocalScale(3.7f);
        parent.setSceneNode(parentNode);

        DesignerEntity child = new DesignerEntity("right_sit_collider", DesignerEntityType.SPHERE);
        Node childNode = new Node("right_sit_collider");
        childNode.setLocalTranslation(-0.34946012f, 0.8668041f, 0.28968692f);
        childNode.setLocalScale(0.3f);
        child.setSceneNode(childNode);

        app.getEntities().add(parent);
        app.getEntities().add(child);

        assertTrue(app.applyEntityAttachment(child, "model_1"));
        parentNode.updateLogicalState(0f);
        parentNode.updateGeometricState();

        Vector3f worldDelta = childNode.getWorldTranslation().subtract(parentNode.getWorldTranslation());
        assertEquals(-0.34946012f, worldDelta.x, 0.0001f);
        assertEquals(0.8668041f, worldDelta.y, 0.0001f);
        assertEquals(0.28968692f, worldDelta.z, 0.0001f);
        assertEquals(0.3f, childNode.getWorldScale().x, 0.0001f);
    }

    @Test
    public void emitsIKAttachmentAndEnabledLayerPlaybackCommands() throws Exception {
        Path tempDir = Files.createTempDirectory("designer-ik-code");
        File smdesign = tempDir.resolve("scene.smdesign").toFile();

        DesignerEntity player = new DesignerEntity("player1", DesignerEntityType.MODEL);
        player.setResourcePath("fighter1_native");
        player.setSceneNode(new Node("player1"));
        player.setIkAsset("ik_sit_on_horse");

        DesignerEntity.IKLayerPlayback leftFoot = player.getOrCreateIKLayerPlayback(
                "horse_sit_left_foot", "horse_sit_left_foot");
        leftFoot.setEnabled(true);
        leftFoot.setTarget("left_sit_collider");
        leftFoot.setBlend(0.2f);
        leftFoot.setWeight(1f);

        DesignerEntity.IKLayerPlayback rightFoot = player.getOrCreateIKLayerPlayback(
                "horse_sit_right_foot", "horse_sit_right_foot");
        rightFoot.setEnabled(true);
        rightFoot.setTarget("right_sit_collider");
        rightFoot.setBlend(0.2f);
        rightFoot.setWeight(1f);

        DesignerEntity leftTarget = new DesignerEntity("left_sit_collider", DesignerEntityType.SPHERE);
        leftTarget.setSceneNode(new Node("left_sit_collider"));
        DesignerEntity rightTarget = new DesignerEntity("right_sit_collider", DesignerEntityType.SPHERE);
        rightTarget.setSceneNode(new Node("right_sit_collider"));

        DesignerDocument.saveCodeFile(
                smdesign,
                Arrays.asList(player, leftTarget, rightTarget),
                new Vector3f(0, 2, 10),
                new Quaternion(0, 1, 0, 0),
                "");

        String code = Files.readString(DesignerDocument.getCodeFile(smdesign).toPath(), StandardCharsets.UTF_8);
        int attachIndex = code.indexOf("player1.ik = \"ik_sit_on_horse\"");
        int leftPlayIndex = code.indexOf(
                "player1.ik.horse_sit_left_foot.play : target left_sit_collider, blend 0.2, weight 1.0");
        int rightPlayIndex = code.indexOf(
                "player1.ik.horse_sit_right_foot.play : target right_sit_collider, blend 0.2, weight 1.0");

        assertTrue("IK assignment should be emitted", attachIndex >= 0);
        assertTrue("left foot layer play should be emitted after IK assignment", leftPlayIndex > attachIndex);
        assertTrue("right foot layer play should be emitted after IK assignment", rightPlayIndex > attachIndex);
    }
}
