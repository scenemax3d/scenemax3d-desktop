package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ChangeAngularVelocityCommand;
import com.scenemaxeng.compiler.ChangeVelocityCommand;
import com.scenemaxeng.compiler.PhysicsMotionCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
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

    @Test
    public void parsesThrowPhysicsCommands() {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(
                "rock => meshy_rock1 : mass 8, collision shape box\n"
                        + "enemy => meshy_enemy : pos (10, 0, 0)\n"
                        + "rock.throw toward enemy power 30\n"
                        + "rock.throw at (enemy up 1) power 24 arc high spin (0, 8, 0)\n"
                        + "rock.throw toward (enemy up 1) power 60 angle -12 spin (1.5, 3, 1)");

        assertTrue(String.join(System.lineSeparator(), prg.syntaxErrors), prg.syntaxErrors.isEmpty());
        assertTrue(prg.actions.get(2) instanceof PhysicsMotionCommand);
        assertTrue(prg.actions.get(3) instanceof PhysicsMotionCommand);
        assertTrue(prg.actions.get(4) instanceof PhysicsMotionCommand);

        PhysicsMotionCommand toward = (PhysicsMotionCommand) prg.actions.get(2);
        assertEquals(PhysicsMotionCommand.ACTION_THROW, toward.action);
        assertEquals(PhysicsMotionCommand.TARGET_TOWARD, toward.targetMode);
        assertEquals("enemy", toward.targetEntity);

        PhysicsMotionCommand at = (PhysicsMotionCommand) prg.actions.get(3);
        assertEquals(PhysicsMotionCommand.TARGET_AT, at.targetMode);
        assertEquals("high", at.arcMode);
        assertTrue(at.targetPositionStatement != null);
        assertTrue(at.spinYExpr != null);

        PhysicsMotionCommand angled = (PhysicsMotionCommand) prg.actions.get(4);
        assertEquals(PhysicsMotionCommand.TARGET_TOWARD, angled.targetMode);
        assertTrue(angled.targetPositionStatement != null);
        assertTrue(angled.angleExpr != null);
        assertTrue(angled.spinYExpr != null);
    }

    @Test
    public void parsesThrowAtTargetWithUnderscoreAndDigitVariableName() {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(
                "m1=>fighter1: pos (-5,-3,0)\n"
                        + "dragon_gate_rock_5 => meshy_rock1 : pos (0,0,0), collision shape box, mass 8.0\n"
                        + "dragon_gate_rock_5.throw at m1 power 100");

        assertTrue(String.join(System.lineSeparator(), prg.syntaxErrors), prg.syntaxErrors.isEmpty());
        assertTrue(prg.actions.get(2) instanceof PhysicsMotionCommand);

        PhysicsMotionCommand throwCommand = (PhysicsMotionCommand) prg.actions.get(2);
        assertEquals(PhysicsMotionCommand.ACTION_THROW, throwCommand.action);
        assertEquals(PhysicsMotionCommand.TARGET_AT, throwCommand.targetMode);
        assertEquals("dragon_gate_rock_5", throwCommand.targetVar);
        assertEquals("m1", throwCommand.targetEntity);
    }

    @Test
    public void parsesRawPhysicsCommands() {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(
                "rock => meshy_rock1 : mass 8, collision shape box\n"
                        + "enemy => meshy_enemy\n"
                        + "rock.physics impulse toward enemy power 30\n"
                        + "rock.physics force forward 10 for 0.5 seconds\n"
                        + "rock.physics velocity (0, 8, 20)\n"
                        + "rock.physics angular velocity (0, 12, 0)\n"
                        + "rock.physics torque (0, 20, 0) impulse\n"
                        + "rock.physics stop");

        assertTrue(String.join(System.lineSeparator(), prg.syntaxErrors), prg.syntaxErrors.isEmpty());
        assertEquals(PhysicsMotionCommand.ACTION_IMPULSE, ((PhysicsMotionCommand) prg.actions.get(2)).action);
        assertEquals(PhysicsMotionCommand.ACTION_FORCE, ((PhysicsMotionCommand) prg.actions.get(3)).action);
        assertEquals(PhysicsMotionCommand.ACTION_VELOCITY, ((PhysicsMotionCommand) prg.actions.get(4)).action);
        assertEquals(PhysicsMotionCommand.ACTION_ANGULAR_VELOCITY, ((PhysicsMotionCommand) prg.actions.get(5)).action);
        assertEquals(PhysicsMotionCommand.ACTION_TORQUE, ((PhysicsMotionCommand) prg.actions.get(6)).action);
        assertTrue(((PhysicsMotionCommand) prg.actions.get(6)).impulseMode);
        assertEquals(PhysicsMotionCommand.ACTION_STOP, ((PhysicsMotionCommand) prg.actions.get(7)).action);
    }

    @Test
    public void allowsNewPhysicsKeywordsAsVariableNames() {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(
                "var throw = 1\n"
                        + "var toward = throw + 1\n"
                        + "var arc = toward + 1\n"
                        + "var spin = arc + 1\n"
                        + "var physics = spin + 1\n"
                        + "var impulse = physics + 1\n"
                        + "var force = impulse + 1\n"
                        + "var torque = force + 1");

        assertTrue(String.join(System.lineSeparator(), prg.syntaxErrors), prg.syntaxErrors.isEmpty());
    }
}
