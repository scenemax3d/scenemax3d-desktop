package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ChangeAngularVelocityCommand;
import com.scenemaxeng.compiler.ChangeVelocityCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class PhysicsCommandParsingTest {

    @Test
    public void parsesVelocityAndAngularVelocityCommands() {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(
                "rock => meshy_rock1 : mass 8, collision shape box\n"
                        + "rock.velocity = 30\n"
                        + "rock.angular velocity = 12");

        assertTrue(String.join(System.lineSeparator(), prg.syntaxErrors), prg.syntaxErrors.isEmpty());
        assertTrue(prg.actions.get(1) instanceof ChangeVelocityCommand);
        assertTrue(prg.actions.get(2) instanceof ChangeAngularVelocityCommand);
    }
}
