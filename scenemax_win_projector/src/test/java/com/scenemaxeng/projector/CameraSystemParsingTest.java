package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.CameraSystemAssignmentCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import com.scenemaxeng.compiler.VariableAssignmentCommand;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CameraSystemParsingTest {

    @Test
    public void parsesReusableFightingCameraSystemAssignment() {
        String code = "hero=>fighter\n"
                + "villain=>fighter\n"
                + "fight_cam = camera.system.fighting(hero, villain, depth 18, height 5, side 1.5, min distance 12, max distance 28, damping 8)\n"
                + "camera.system = fight_cam";

        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(code);

        assertTrue(prg.syntaxErrors.isEmpty());
        assertNotNull(prg.getVar("fight_cam"));
        assertTrue(prg.actions.get(2) instanceof VariableAssignmentCommand);
        assertTrue(prg.actions.get(3) instanceof CameraSystemAssignmentCommand);
    }

    @Test
    public void parsesCameraSystemDefaultReset() {
        String code = "camera.system = default";

        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(code);

        assertTrue(prg.syntaxErrors.isEmpty());
        assertTrue(prg.actions.get(0) instanceof CameraSystemAssignmentCommand);
        CameraSystemAssignmentCommand cmd = (CameraSystemAssignmentCommand) prg.actions.get(0);
        assertTrue(cmd.resetToDefault);
    }
}
