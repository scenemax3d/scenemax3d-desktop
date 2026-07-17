package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.DoBlockCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import com.scenemaxeng.compiler.SphereVariableDef;
import com.scenemaxeng.compiler.StatementDef;
import com.scenemaxeng.compiler.VariableDef;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
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

    @Test
    public void keepsMultiplayerFlagOnSphereCreatedInsideDoBlock() {
        ProgramDef program = new SceneMaxLanguageParser(null, "").parse(
                "do async\n"
                        + "  s=>sphere : pos (0,0,0), material=\"pond\", multiplayer\n"
                        + "end do");

        assertTrue(program.syntaxErrors == null || program.syntaxErrors.isEmpty());
        assertFalse(program.actions.isEmpty());
        DoBlockCommand block = (DoBlockCommand) program.actions.get(0);

        VariableDef sphere = null;
        for (StatementDef statement : block.prg.actions) {
            if (statement instanceof com.scenemaxeng.compiler.GraphicEntityCreationCommand) {
                VariableDef var = ((com.scenemaxeng.compiler.GraphicEntityCreationCommand) statement).varDef;
                if (var instanceof SphereVariableDef) {
                    sphere = var;
                    break;
                }
            }
        }

        assertTrue("Expected inner sphere to keep the multiplayer flag", sphere != null && sphere.isMultiplayer);
    }

    private void assertParses(String code) {
        ProgramDef program = new SceneMaxLanguageParser(null, "").parse(code);
        assertTrue("Expected command to parse without syntax errors:\n" + code,
                program.syntaxErrors == null || program.syntaxErrors.isEmpty());
    }
}
