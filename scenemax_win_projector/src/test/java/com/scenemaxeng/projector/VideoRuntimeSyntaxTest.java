package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.QuadVariableDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import com.scenemaxeng.compiler.VariableDef;
import com.scenemaxeng.compiler.VideoPlayCommand;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VideoRuntimeSyntaxTest {

    @Test
    public void parsesVideoResourceDeclarationAndPlayCommand() {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(
                "some_object_target => cube\n"
                        + "my_video => videos.some_video_id\n"
                        + "my_video.play : target some_object_target, start \"00:01:00\", end \"00:02:59\", reverse, loop");

        assertTrue(String.join("\n", prg.syntaxErrors), prg.syntaxErrors.isEmpty());
        assertEquals(VariableDef.VAR_TYPE_VIDEO, prg.getVar("my_video").varType);
        assertEquals("videos.some_video_id", prg.getVar("my_video").resName);
        assertTrue(SceneMaxLanguageParser.videoUsed.contains("videos.some_video_id"));
        assertEquals(2, prg.actions.size());
        assertTrue(prg.actions.get(1) instanceof VideoPlayCommand);

        VideoPlayCommand cmd = (VideoPlayCommand) prg.actions.get(1);
        assertEquals("my_video", cmd.targetVar);
        assertEquals("some_object_target", cmd.targetObjectVar);
        assertEquals("00:01:00", cmd.startTimestamp);
        assertEquals("00:02:59", cmd.endTimestamp);
        assertTrue(cmd.reverse);
        assertTrue(cmd.loop);
    }

    @Test
    public void overlappingTargetOnlyPlaySyntaxUsesVideoVariableType() {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(
                "b => box\n"
                        + "vid => videos.intro_clip\n"
                        + "vid.play: target b");

        assertTrue(String.join("\n", prg.syntaxErrors), prg.syntaxErrors.isEmpty());
        assertEquals(2, prg.actions.size());
        assertTrue(prg.actions.get(1) instanceof VideoPlayCommand);

        VideoPlayCommand cmd = (VideoPlayCommand) prg.actions.get(1);
        assertEquals("vid", cmd.targetVar);
        assertEquals("b", cmd.targetObjectVar);
    }

    @Test
    public void parsesQuadScaleAttribute() {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(
                "quad_1 => quad : size (1,1), pos (13,-88,169), scale 20.1");

        assertTrue(String.join("\n", prg.syntaxErrors), prg.syntaxErrors.isEmpty());
        assertEquals(VariableDef.VAR_TYPE_QUAD, prg.getVar("quad_1").varType);
        QuadVariableDef quad = (QuadVariableDef) prg.getVar("quad_1");
        assertTrue(quad.scaleExpr != null);
    }

    @Test
    public void parsesVectorScaleForModelsAndAllPrimitiveEntities() {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(
                "model_1 => cube: pos (0,0,0), scale (2,3,4)\n"
                        + "box_1 => box : size (1,1,1), scale (2,3,4)\n"
                        + "sphere_1 => sphere : radius 1, scale (2,3,4)\n"
                        + "cylinder_1 => cylinder : radius (1,1), height 2, scale (2,3,4)\n"
                        + "hollow_1 => hollow cylinder : radius (1,1), inner radius (0.5,0.5), height 2, scale (2,3,4)\n"
                        + "quad_1 => quad : size (1,1), scale (2,3,4)\n"
                        + "wedge_1 => wedge : size (1,1,1), scale (2,3,4)\n"
                        + "cone_1 => cone : radius (0,1), height 2, scale (2,3,4)\n"
                        + "stairs_1 => stairs : size (2,0.25,0.4), steps 6, scale (2,3,4)\n"
                        + "arch_1 => arch : size (2,2.5,0.5), thickness 0.35, segments 12, scale (2,3,4)");

        assertTrue(String.join("\n", prg.syntaxErrors), prg.syntaxErrors.isEmpty());
        assertVectorScale(prg.getVar("model_1"));
        assertVectorScale(prg.getVar("box_1"));
        assertVectorScale(prg.getVar("sphere_1"));
        assertVectorScale(prg.getVar("cylinder_1"));
        assertVectorScale(prg.getVar("hollow_1"));
        assertVectorScale(prg.getVar("quad_1"));
        assertVectorScale(prg.getVar("wedge_1"));
        assertVectorScale(prg.getVar("cone_1"));
        assertVectorScale(prg.getVar("stairs_1"));
        assertVectorScale(prg.getVar("arch_1"));
    }

    private static void assertVectorScale(VariableDef varDef) {
        assertTrue(varDef.scaleExpr == null);
        assertTrue(varDef.scaleXExpr != null);
        assertTrue(varDef.scaleYExpr != null);
        assertTrue(varDef.scaleZExpr != null);
    }
}
