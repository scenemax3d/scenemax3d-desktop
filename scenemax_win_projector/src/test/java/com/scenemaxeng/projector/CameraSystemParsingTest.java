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
    public void parsesSingleTargetGameplayCameraSystems() {
        String code = "hero=>sinbad\n"
                + "follow_cam = camera.system.third_person(hero, distance 9, height 3, side 1, look ahead 2, fov 55)\n"
                + "fps_cam = camera.system.first_person(hero, height 1.7, depth 0.15, fov 72)\n"
                + "race_cam = camera.system.racing(hero, distance 14, height 4, look ahead 8, max fov 68)\n"
                + "platform_cam = camera.system.platformer(hero, distance 10, height 3, dead zone 2, vertical bias 2.5)";

        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(code);

        assertTrue(prg.syntaxErrors.isEmpty());
        assertNotNull(prg.getVar("follow_cam"));
        assertNotNull(prg.getVar("fps_cam"));
        assertNotNull(prg.getVar("race_cam"));
        assertNotNull(prg.getVar("platform_cam"));
    }

    @Test
    public void parsesZeroTargetRtsCameraSystem() {
        String code = "strategy_cam = camera.system.rts(distance 30, angle 60, min x -50, max x 50, min z -50, max z 50)\n"
                + "camera.system = strategy_cam";

        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(code);

        assertTrue(prg.syntaxErrors.isEmpty());
        assertNotNull(prg.getVar("strategy_cam"));
        assertTrue(prg.actions.get(0) instanceof VariableAssignmentCommand);
        assertTrue(prg.actions.get(1) instanceof CameraSystemAssignmentCommand);
    }

    @Test
    public void parsesCameraModifiersAndApplyCommand() {
        String code = "hero=>fighter\n"
                + "villain=>fighter\n"
                + "fight_cam = camera.system.fighting(hero, villain)\n"
                + "hit_fx = camera.system.modifiers.hit_modifier\n"
                + "quake_fx = camera.system.modifiers.earthquake_modifier\n"
                + "fight_cam.apply hit_fx : duration 0.35, amplitude 1.4, rx 2\n"
                + "fight_cam.apply quake_fx : duration 1.8, frequency 8";

        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(code);

        assertTrue(prg.syntaxErrors.isEmpty());
        assertNotNull(prg.getVar("hit_fx"));
        assertNotNull(prg.getVar("quake_fx"));
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
