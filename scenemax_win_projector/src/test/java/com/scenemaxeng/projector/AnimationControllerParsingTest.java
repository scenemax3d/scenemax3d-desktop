package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ActionCommandAnimate;
import com.scenemaxeng.compiler.AnimationControllerActionCommand;
import com.scenemaxeng.compiler.AnimationControllerAssignmentCommand;
import com.scenemaxeng.compiler.AnimationControllerEventCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import com.scenemaxeng.compiler.VariableDef;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AnimationControllerParsingTest {

    @Test
    public void parsesAnimationControllerAssignmentActionsAndEvent() {
        String code = "player1=>fighter\n"
                + "var score = 0\n"
                + "my_anim = animation player1.kick\n"
                + "my_anim.event(\"kick\", 45) = { score = 10 }\n"
                + "my_anim.run\n"
                + "my_anim.stop";

        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(code);

        assertTrue(prg.syntaxErrors.isEmpty());
        assertNotNull(prg.getVar("my_anim"));
        assertEquals(VariableDef.VAR_TYPE_ANIMATION_CONTROLLER, prg.getVar("my_anim").varType);
        assertTrue(prg.actions.get(2) instanceof AnimationControllerAssignmentCommand);
        assertTrue(prg.actions.get(3) instanceof AnimationControllerEventCommand);
        assertTrue(prg.actions.get(4) instanceof AnimationControllerActionCommand);
        assertTrue(prg.actions.get(5) instanceof AnimationControllerActionCommand);

        AnimationControllerAssignmentCommand assignment = (AnimationControllerAssignmentCommand) prg.actions.get(2);
        assertEquals("player1", assignment.sourceVar);
        assertEquals("kick", assignment.animationName);

        AnimationControllerActionCommand run = (AnimationControllerActionCommand) prg.actions.get(4);
        assertEquals(AnimationControllerActionCommand.RUN, run.action);

        AnimationControllerActionCommand stop = (AnimationControllerActionCommand) prg.actions.get(5);
        assertEquals(AnimationControllerActionCommand.STOP, stop.action);
    }

    @Test
    public void allowsAnimationEventToMoveRuntimeWeaponCollider() {
        String code = "m2=>fighter1 : pos (4,0,0)\n"
                + "m2.weapon = \"weapon_player_weapon\"\n"
                + "when key space is pressed once do\n"
                + "    anim = animation m2.zombie_punch1\n"
                + "    anim.event(\"zombie_punch1\", 30) = {\n"
                + "        Logger.info \"throw event\"\n"
                + "        weapon_player_weapon.move forward 5 in 5 seconds\n"
                + "    }\n"
                + "    Logger.info \"running throw animation\"\n"
                + "    anim.run\n"
                + "end do";

        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(code);

        assertTrue(prg.syntaxErrors.isEmpty());
    }

    @Test
    public void parsesAnimationControllerAssignmentSequenceAndSpeed() {
        String code = "player1=>fighter\n"
                + "anim = animation player1.zombie_punch1 at speed of 0.1 then Idle2";

        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(code);

        assertTrue(prg.syntaxErrors.toString(), prg.syntaxErrors.isEmpty());
        assertTrue(prg.actions.get(1) instanceof AnimationControllerAssignmentCommand);

        AnimationControllerAssignmentCommand assignment =
                (AnimationControllerAssignmentCommand) prg.actions.get(1);
        assertEquals("zombie_punch1", assignment.animationName);
        assertEquals(2, assignment.statements.size());

        ActionCommandAnimate punch = (ActionCommandAnimate) assignment.statements.get(0);
        ActionCommandAnimate idle = (ActionCommandAnimate) assignment.statements.get(1);
        assertEquals("player1", punch.targetVar);
        assertEquals("zombie_punch1", punch.animationName);
        assertNotNull(punch.speedExpr);
        assertEquals("Idle2", idle.animationName);
    }

    @Test
    public void animationControllerRunBlocksUntilRuntimeAnimationFinishes() {
        SceneMaxScope scope = new SceneMaxScope();
        VariableDef varDef = new VariableDef();
        varDef.varName = "anim";
        VarInst var = new VarInst(varDef, scope);
        FakeAnimationRuntimeController runtimeController = new FakeAnimationRuntimeController();
        var.value = runtimeController;
        scope.vars_index.put("anim", var);

        AnimationControllerActionCommand command = new AnimationControllerActionCommand();
        command.action = AnimationControllerActionCommand.RUN;
        command.targetVar = "anim";

        AnimationControllerActionController controller =
                new AnimationControllerActionController(null, new ProgramDef(), scope, command);

        assertFalse(controller.run(0.016f));
        assertTrue(controller.run(0.016f));
        assertEquals(1, runtimeController.runCount);
        assertEquals(2, runtimeController.updateCount);
    }

    @Test
    public void animationControllerRuntimeUsesRegularAnimationComposite() throws Exception {
        AnimationRuntimeController runtimeController = new AnimationRuntimeController(
                null,
                new ProgramDef(),
                new SceneMaxScope(),
                "player1",
                null,
                "zombie_punch1",
                1);

        runtimeController.run();

        java.lang.reflect.Field field = AnimationRuntimeController.class.getDeclaredField("runningController");
        field.setAccessible(true);
        assertTrue(field.get(runtimeController) instanceof AnimateCompositeController);
    }

    @Test
    public void animationControllerRuntimeUsesSequenceCommands() throws Exception {
        String code = "player1=>fighter\n"
                + "anim = animation player1.zombie_punch1 at speed of 0.1 then Idle2";

        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(code);
        AnimationControllerAssignmentCommand assignment =
                (AnimationControllerAssignmentCommand) prg.actions.get(1);
        AnimationRuntimeController runtimeController = new AnimationRuntimeController(
                null,
                prg,
                new SceneMaxScope(),
                assignment.sourceVar,
                assignment.sourceVarDef,
                assignment.animationName,
                assignment.varLineNum,
                assignment.statements);

        runtimeController.run();

        java.lang.reflect.Field runningField =
                AnimationRuntimeController.class.getDeclaredField("runningController");
        runningField.setAccessible(true);
        AnimateCompositeController composite =
                (AnimateCompositeController) runningField.get(runtimeController);

        java.lang.reflect.Field controllersField =
                CompositeController.class.getDeclaredField("_controllers");
        controllersField.setAccessible(true);
        java.util.ArrayList<?> controllers = (java.util.ArrayList<?>) controllersField.get(composite);

        assertEquals(2, controllers.size());
        assertTrue(controllers.get(0) instanceof ModelAnimateController);
        assertTrue(controllers.get(1) instanceof ModelAnimateController);
    }

    @Test
    public void parsesStandaloneAnimRunAxeReproProgram() throws Exception {
        File repro = resolveRepoFile("projects/fighting_game_project/scripts/Fighting Game/side_tests/anim_run_axe_repro");
        String code = FileUtils.readFileToString(repro, StandardCharsets.UTF_8);

        ProgramDef prg = new SceneMaxLanguageParser(null, repro.getAbsolutePath()).parse(code);

        assertTrue(prg.syntaxErrors.toString(), prg.syntaxErrors.isEmpty());
    }

    private static class FakeAnimationRuntimeController extends AnimationRuntimeController {
        int runCount;
        int updateCount;

        FakeAnimationRuntimeController() {
            super(null, new ProgramDef(), new SceneMaxScope(), "player1", null, "zombie_punch1", 1);
        }

        @Override
        public void run() {
            runCount++;
        }

        @Override
        public boolean update(float tpf) {
            updateCount++;
            return updateCount >= 2;
        }
    }

    private File resolveRepoFile(String path) {
        File file = new File(path);
        if (file.exists()) {
            return file;
        }
        return new File("..", path);
    }
}
