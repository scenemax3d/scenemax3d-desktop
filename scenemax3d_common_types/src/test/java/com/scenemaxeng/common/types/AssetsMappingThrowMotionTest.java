package com.scenemaxeng.common.types;

import com.scenemaxeng.common.motion.ThrowMotionDefinition;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AssetsMappingThrowMotionTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void projectResourceMotionIsRuntimeSourceWhenScriptDesignerDocHasSameId() throws Exception {
        File project = temporaryFolder.newFolder("project");
        File resourcesThrowMotions = new File(project, "resources/throw_motions");
        File scriptLevel = new File(project, "scripts/Fighting Game/game_level1");
        assertTrue(resourcesThrowMotions.mkdirs());
        assertTrue(scriptLevel.mkdirs());

        ThrowMotionDefinition resourcesMotion = ThrowMotionDefinition.createTemplate(
                "Axe Throw", ThrowMotionDefinition.TYPE_TARGET_ARC);
        resourcesMotion.setId("motion_axe_throw");
        resourcesMotion.getParameters().duration = 1.2;
        resourcesMotion.getParameters().arcHeight = 3.0;
        resourcesMotion.save(new File(resourcesThrowMotions, "motion_axe_throw.smmotion"));

        ThrowMotionDefinition scriptMotion = ThrowMotionDefinition.createTemplate(
                "Axe Throw", ThrowMotionDefinition.TYPE_TARGET_ARC);
        scriptMotion.setId("motion_axe_throw");
        scriptMotion.getParameters().duration = 0.8;
        scriptMotion.getParameters().arcHeight = 2.0;
        scriptMotion.save(new File(scriptLevel, "motion_axe_throw.smmotion"));

        AssetsMapping mapping = new AssetsMapping(new File(project, "resources").getAbsolutePath());
        mapping.loadWeaponsFromProject(project.getAbsolutePath());

        ThrowMotionDefinition resolved = mapping.getThrowMotionDefinition("motion_axe_throw");

        assertEquals(1.2, resolved.getParameters().duration, 0.0001);
        assertEquals(3.0, resolved.getParameters().arcHeight, 0.0001);
    }

    @Test
    public void packagedThrowMotionCanLoadFromClasspathResource() {
        AssetsMapping mapping = new AssetsMapping(new File(temporaryFolder.getRoot(), "missing_resources").getAbsolutePath());

        ThrowMotionDefinition resolved = mapping.getThrowMotionDefinition("motion_packaged_throw");

        assertEquals(1.6, resolved.getParameters().duration, 0.0001);
        assertEquals(4.5, resolved.getParameters().arcHeight, 0.0001);
    }
}
