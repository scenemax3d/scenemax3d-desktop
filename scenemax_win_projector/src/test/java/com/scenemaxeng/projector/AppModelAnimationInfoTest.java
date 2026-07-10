package com.scenemaxeng.projector;

import com.jme3.anim.AnimClip;
import com.jme3.anim.AnimComposer;
import com.jme3.anim.Armature;
import com.jme3.anim.Joint;
import com.jme3.anim.SkinningControl;
import com.jme3.animation.AnimControl;
import com.jme3.animation.Animation;
import com.jme3.scene.Node;
import com.scenemaxeng.common.types.ResourceSetup;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AppModelAnimationInfoTest {
    @Test
    public void j3oModelListsModernAnimComposerClips() {
        Node model = new Node("model");
        Node child = new Node("child");
        AnimComposer composer = new AnimComposer();
        composer.addAnimClip(new AnimClip("Gallop"));
        composer.addAnimClip(new AnimClip("Walk"));
        child.addControl(composer);
        model.attachChild(child);

        AppModel appModel = new AppModel(model);
        appModel.resource = new ResourceSetup("horse1_j3o", "Models/horse/horse_j3o.j3o",
                1f, 1f, 1f, 0f, 0f, 0f, 0f);

        String animations = appModel.getAnimationsList();
        assertTrue(animations.contains("Gallop"));
        assertTrue(animations.contains("Walk"));
    }

    @Test
    public void animationListCombinesModernAndLegacyNamesWithoutDuplicates() {
        Node model = new Node("model");
        Node child = new Node("child");
        AnimComposer composer = new AnimComposer();
        composer.addAnimClip(new AnimClip("Walk"));
        child.addControl(composer);

        AnimControl legacy = new AnimControl();
        legacy.addAnim(new Animation("Walk", 1f));
        legacy.addAnim(new Animation("Idle", 1f));
        child.addControl(legacy);
        model.attachChild(child);

        AppModel appModel = new AppModel(model);
        appModel.resource = new ResourceSetup("fighter", "Models/fighter/fighter.glb",
                1f, 1f, 1f, 0f, 0f, 0f, 0f);

        String animations = appModel.getAnimationsList();
        assertTrue(animations.contains("Walk"));
        assertTrue(animations.contains("Idle"));
        assertEquals(animations.indexOf("Walk"), animations.lastIndexOf("Walk"));
    }

    @Test
    public void j3oModelFindsModernSkinningAttachmentNodesByJointAlias() {
        Node model = new Node("model");
        Node child = new Node("child");
        Joint head = new Joint("mixamorig:Head");
        SkinningControl skinningControl = new SkinningControl(new Armature(new Joint[]{head}));
        child.addControl(skinningControl);
        model.attachChild(child);

        AppModel appModel = new AppModel(model);
        appModel.resource = new ResourceSetup("fighter1_j3o", "Models/fighter1/fighter1_j3o.j3o",
                1f, 1f, 1f, 0f, 0f, 0f, 0f);

        assertNotNull(appModel.getJointAttachementNode("mixamorig:Head"));
        assertNotNull(appModel.getJointAttachementNode("Head"));
        assertTrue(appModel.getJointsList().contains("mixamorig:Head"));
    }
}
