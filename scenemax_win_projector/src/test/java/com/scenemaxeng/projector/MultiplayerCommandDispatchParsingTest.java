package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class MultiplayerCommandDispatchParsingTest {

    @Test
    public void parsesGeneratedMoveAndRotateDispatchCommands() {
        assertParses("mp_remote_1 => sinbad\nmp_remote_1.move right 1 in 0.1 seconds");
        assertParses("mp_remote_1 => sinbad\nmp_remote_1.move (x + 1) in 0.1 seconds");
        assertParses("mp_remote_1 => sinbad\nmp_remote_1.move forward 1 for 0.1 seconds");
        assertParses("mp_remote_1 => sinbad\nmp_remote_1.move to (1,0,0) in 0.1 seconds");
        assertParses("mp_remote_1 => sinbad\nmp_remote_1.rotate (y - 45) in 0.2 seconds");
        assertParses("mp_remote_1 => sinbad\nmp_remote_1.rotate to (y 90) in 0.2 seconds");
        assertParses("mp_remote_1 => sinbad\nmp_remote_1.rotate (0,90,0)");
    }

    private void assertParses(String code) {
        ProgramDef program = new SceneMaxLanguageParser(null, "").parse(code);
        assertTrue("Expected command to parse without syntax errors:\n" + code,
                program.syntaxErrors == null || program.syntaxErrors.isEmpty());
    }
}
