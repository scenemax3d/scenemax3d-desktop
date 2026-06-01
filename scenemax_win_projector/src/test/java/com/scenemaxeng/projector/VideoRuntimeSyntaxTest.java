package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProgramDef;
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
}
