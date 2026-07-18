package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.DoBlockCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import com.scenemaxeng.compiler.SphereVariableDef;
import com.scenemaxeng.compiler.StatementDef;
import com.scenemaxeng.compiler.VariableDef;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
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
        assertParses("mp_remote_1 => sinbad\nmp_remote_1.\"Run\" at speed of 1");
        assertParses("mp_remote_1 => sinbad\nmp_remote_1.\"Take 001\"[0-50] at speed of 1.5");
    }

    @Test
    public void parsesGeneratedAttachAndIkDispatchCommands() {
        assertParses("mp_remote_1 => sinbad\n"
                + "mp_remote_2 => sinbad\n"
                + "mp_remote_1.attach to mp_remote_2.\"Bip01 Spine1_04\": pos (-0.12,-1.23,1.03)");
        assertParses("mp_remote_1 => sinbad\n"
                + "mp_remote_3 => sphere\n"
                + "mp_remote_1.ik = \"ik_sit_on_horse\"\n"
                + "mp_remote_1.ik.horse_sit_right_foot.play : target mp_remote_3, blend 0.2, weight 1");
    }

    @Test
    public void parsesGeneratedMultiplayerSpawnCommandsWithScaleAndColliders() {
        assertParses("mp_remote_1 => horse1_native: pos (0.482243,0,1.164553), scale 3.7, collision shape none");
        assertParses("mp_remote_2 => collider sphere: pos (-5.174184,1.964885,4.311819), radius 0.5, scale 0.3");
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

    @Test
    public void preparesSnapshotResumeCommands() throws Exception {
        MultiplayerNetworkComponent component = new MultiplayerNetworkComponent(null);
        Method method = MultiplayerNetworkComponent.class.getDeclaredMethod(
                "commandForSnapshotAction", String.class, int.class);
        method.setAccessible(true);

        assertEquals("{network_entity}.move right 4 in 10 seconds",
                method.invoke(component, "{network_entity}.move right 4 in 10 seconds", 5000));
        assertEquals("{network_entity}.move (x - 6) in 12 seconds",
                method.invoke(component, "{network_entity}.move (x - 6) in 12 seconds", 3000));
        assertEquals("{network_entity}.rotate (y + 90) in 10 seconds",
                method.invoke(component, "{network_entity}.rotate (y + 90) in 10 seconds", 5000));
        assertEquals("{network_entity}.move to (4,0,0) in 5 seconds",
                method.invoke(component, "{network_entity}.move to (4,0,0) in 10 seconds", 5000));
    }

    private void assertParses(String code) {
        ProgramDef program = new SceneMaxLanguageParser(null, "").parse(code);
        assertTrue("Expected command to parse without syntax errors:\n" + code,
                program.syntaxErrors == null || program.syntaxErrors.isEmpty());
    }
}
