package com.scenemaxeng.projector;

import com.scenemaxeng.common.types.AssetsMapping;
import com.scenemaxeng.compiler.CinematicCameraVariableDef;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CinematicCameraRuntimeResolutionTest {

    @Test
    public void prefersCompilerResolvedSourceFileWhenProjectHasDuplicateRuntimeIds() throws Exception {
        File projectDir = Files.createTempDirectory("cinematic-runtime-resolution").toFile();
        projectDir.deleteOnExit();
        File resourcesDir = new File(projectDir, "resources");
        assertTrue(resourcesDir.mkdirs() || resourcesDir.exists());

        File scriptsRoot = new File(projectDir, "scripts");
        File introDir = new File(scriptsRoot, "game_intro");
        assertTrue(introDir.mkdirs() || introDir.exists());

        File conflictingRig = new File(scriptsRoot, "test_designer.smdesign");
        Files.writeString(conflictingRig.toPath(),
                "{ \"entities\": [ { \"type\": \"CINEMATIC_RIG\", \"cinematicRuntimeId\": \"cinematic_rig_1\", "
                        + "\"cinematicTargetEntityName\": \"wrong_target\", \"children\": [], "
                        + "\"cinematicSegments\": [ { \"trackId\": \"wrong_track\", \"startAnchor\": 260, \"endAnchor\": 340, \"speed\": 28 }, "
                        + "{ \"trackId\": \"wrong_track_2\", \"startAnchor\": 240, \"endAnchor\": 320, \"speed\": 22 } ] } ] }",
                StandardCharsets.UTF_8);

        File introRig = new File(introDir, "game_intro.smdesign");
        Files.writeString(introRig.toPath(),
                "{ \"entities\": [ { \"type\": \"CINEMATIC_RIG\", \"cinematicRuntimeId\": \"cinematic_rig_1\", "
                        + "\"cinematicTargetEntityName\": \"model_1\", \"cinematicTargetOffset\": [0,29.2,0], "
                        + "\"children\": [ { \"type\": \"CINEMATIC_TRACK\", \"id\": \"track_1\", "
                        + "\"position\": [0,30.222626,-36.82337], \"rotation\": [0,0,0,1], \"scale\": [1,1,1], "
                        + "\"cinematicTrackData\": { \"anchorCount\": 360, \"radiusX\": 30, \"radiusZ\": 20 } } ], "
                        + "\"cinematicSegments\": [ { \"trackId\": \"track_1\", \"startAnchor\": 268, \"endAnchor\": 266, \"speed\": 30 } ] } ] }",
                StandardCharsets.UTF_8);

        SceneMaxApp app = new SceneMaxApp(null);
        app.setWorkingFolder(new File(introDir, "main").getAbsolutePath());
        SceneMaxApp.assetsMapping = new AssetsMapping(resourcesDir.getAbsolutePath());
        SceneMaxApp.assetsMapping.loadCinematicsFromProject(projectDir.getAbsolutePath());

        CinematicCameraVariableDef varDef = new CinematicCameraVariableDef();
        varDef.cinematicCameraId = "cinematic_rig_1";
        varDef.cinematicSourceFile = introRig.getAbsolutePath();

        RuntimeCinematicRig rig = app.resolveCinematicRig(varDef);
        assertNotNull(rig);
        assertEquals("model_1", rig.targetEntityName);
        assertEquals(1, rig.segments.size());
        assertEquals(268, rig.segments.get(0).startAnchor);
        assertEquals(266, rig.segments.get(0).endAnchor);
    }
}
