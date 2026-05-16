package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.PlayStopSoundCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AudioPlayParsingTest {

    @Test
    public void quotedAudioPlayParsesAsResourceId() {
        ProgramDef prg = parse("audio.play \"punch1\"");

        assertTrue(String.join("\n", prg.syntaxErrors), prg.syntaxErrors.isEmpty());
        assertEquals(1, prg.actions.size());
        assertTrue(prg.actions.get(0) instanceof PlayStopSoundCommand);

        PlayStopSoundCommand cmd = (PlayStopSoundCommand) prg.actions.get(0);
        assertEquals("punch1", cmd.sound);
        assertNull(cmd.soundExpr);
    }

    @Test
    public void unquotedAudioPlayParsesAsExpression() {
        ProgramDef prg = parse("audio.play punch1");

        assertTrue(String.join("\n", prg.syntaxErrors), prg.syntaxErrors.isEmpty());
        assertEquals(1, prg.actions.size());
        assertTrue(prg.actions.get(0) instanceof PlayStopSoundCommand);

        PlayStopSoundCommand cmd = (PlayStopSoundCommand) prg.actions.get(0);
        assertNull(cmd.sound);
        assertNotNull(cmd.soundExpr);
    }

    private ProgramDef parse(String code) {
        return new SceneMaxLanguageParser(null, "").parse(code);
    }
}
