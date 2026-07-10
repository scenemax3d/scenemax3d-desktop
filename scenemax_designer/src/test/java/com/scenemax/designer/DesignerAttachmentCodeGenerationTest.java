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
}
