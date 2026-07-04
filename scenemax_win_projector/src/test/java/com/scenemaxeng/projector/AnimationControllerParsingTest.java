package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ActionCommandAnimate;
import com.scenemaxeng.compiler.AnimationControllerActionCommand;
import com.scenemaxeng.compiler.AnimationControllerAssignmentCommand;
import com.scenemaxeng.compiler.AnimationControllerEventCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import com.scenemaxeng.compiler.VariableDef;
import com.jme3.scene.Node;
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
    public void parsesAnimationControllerRewindWithOptionalEasing() {
        String code = "player1=>fighter\n"
                + "my_anim = animation player1.kick\n"
                + "my_anim.rewind 5 in 0.5 seconds ease out \"sine\"";

        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(code);

        assertTrue(prg.syntaxErrors.toString(), prg.syntaxErrors.isEmpty());
        assertTrue(prg.actions.get(2) instanceof AnimationControllerActionCommand);

        AnimationControllerActionCommand rewind = (AnimationControllerActionCommand) prg.actions.get(2);
        assertEquals(AnimationControllerActionCommand.REWIND, rewind.action);
        assertEquals("5", rewind.rewindPercentExpr.getText());
        assertEquals("0.5", rewind.rewindDurationExpr.getText());
        assertEquals("sine", rewind.motionEaseFunction);
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
    public void parsesInverseKinematicsCollisionRewindProgramShape() {
        String code = "m1=>fighter1: pos (-5,-3,0)\n"
                + "m2=>fighter1: pos(0,-3,0)\n"
                + "m2.weapon = \"weapon_player_weapon\"\n"
                + "m2.weapon.posture = \"fight\"\n"
                + "anim = animation m2.zombie_punch1 at speed of 0.1\n"
                + "col=>sphere : radius 0.5\n"
                + "col.attach to m1.\"mixamorig:LeftHand\"\n"
                + "var ik=0\n"
                + "[ik==1]\n"
                + "when col collides with m2.weapon.colliders[\"weapon_sphere_collider_1\"] do\n"
                + "    anim.rewind 1 in 0.1 seconds ease out \"sine\"\n"
                + "end do\n"
                + "when key space is pressed once do\n"
                + "    anim.run\n"
                + "    m1.idle2 loop\n"
                + "end do\n"
                + "when key Q is pressed once do\n"
                + "    ik=1\n"
                + "    m1.ik = \"ik_test_ik\"\n"
                + "    m1.ik.layer2.play : target m2.weapon.colliders[\"weapon_sphere_collider_1\"], blend 0.2, weight 1\n"
                + "    wait 1 Seconds\n"
                + "    m1.ik = empty\n"
                + "    ik=0\n"
                + "end do";

        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(code);

        assertTrue(prg.syntaxErrors.toString(), prg.syntaxErrors.isEmpty());
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
    public void parsesProtectedAsyncShortAnimation() {
        String code = "player=>phoenix\n"
                + "player.fly at speed of 0.1 : protected Async\n"
                + "wait 0.5 second\n"
                + "player.fly";

        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(code);

        assertTrue(prg.syntaxErrors.toString(), prg.syntaxErrors.isEmpty());
        assertTrue(prg.actions.get(1) instanceof ActionCommandAnimate);

        ActionCommandAnimate animate = (ActionCommandAnimate) prg.actions.get(1);
        assertTrue(animate.isAsync);
        assertTrue(animate.isProtected);
        assertEquals(1, animate.statements.size());

        ActionCommandAnimate fly = (ActionCommandAnimate) animate.statements.get(0);
        assertEquals("fly", fly.animationName);
        assertTrue(fly.isProtected);
        assertNotNull(fly.speedExpr);
    }

    @Test
    public void parsesShortAnimationFrameRange() {
        String code = "horse=>fighter\n"
                + "horse.\"Take 001\"[0-50] at speed of 2 loop";

        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(code);

        assertTrue(prg.syntaxErrors.toString(), prg.syntaxErrors.isEmpty());
        assertTrue(prg.actions.get(1) instanceof ActionCommandAnimate);

        ActionCommandAnimate animate = (ActionCommandAnimate) prg.actions.get(1);
        ActionCommandAnimate take = (ActionCommandAnimate) animate.statements.get(0);
        assertEquals("Take 001", take.animationName);
        assertEquals("0", take.frameRangeStart);
        assertFalse(take.frameRangeStartPercent);
        assertEquals("50", take.frameRangeEnd);
        assertFalse(take.frameRangeEndPercent);
        assertNotNull(take.speedExpr);
        assertTrue(animate.loop);
    }

    @Test
    public void parsesAnimationControllerPercentFrameRange() {
        String code = "horse=>fighter\n"
                + "anim = animation horse.long_animation[0%-50%] then \"Take 001\"[25-75%]";

        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(code);

        assertTrue(prg.syntaxErrors.toString(), prg.syntaxErrors.isEmpty());
        AnimationControllerAssignmentCommand assignment =
                (AnimationControllerAssignmentCommand) prg.actions.get(1);
        assertEquals("long_animation", assignment.animationName);
        assertEquals(2, assignment.statements.size());

        ActionCommandAnimate first = (ActionCommandAnimate) assignment.statements.get(0);
        assertEquals("0", first.frameRangeStart);
        assertTrue(first.frameRangeStartPercent);
        assertEquals("50", first.frameRangeEnd);
        assertTrue(first.frameRangeEndPercent);

        ActionCommandAnimate second = (ActionCommandAnimate) assignment.statements.get(1);
        assertEquals("Take 001", second.animationName);
        assertEquals("25", second.frameRangeStart);
        assertFalse(second.frameRangeStartPercent);
        assertEquals("75", second.frameRangeEnd);
        assertTrue(second.frameRangeEndPercent);
    }

    @Test
    public void protectedLegacyAnimationBlocksIncomingControllerUntilFinished() {
        AppModel model = new AppModel(new Node("model"));
        AppModelAnimationController protectedController =
                new AppModelAnimationController(new SceneMaxBaseController());
        protectedController.isProtected = true;
        protectedController.animationFinished = false;
        model.currentAnimationController = protectedController;

        AppModelAnimationController incomingController =
                new AppModelAnimationController(new SceneMaxBaseController());

        assertTrue(model.hasProtectedAnimationInProgress(incomingController));

        protectedController.finishControllerAnimation();

        assertFalse(model.hasProtectedAnimationInProgress(incomingController));
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
    public void animationControllerRewindBlocksUntilRuntimeRewindFinishes() {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse("player1=>fighter\n"
                + "anim = animation player1.kick\n"
                + "anim.rewind 5 in 0.5 seconds");
        AnimationControllerActionCommand command = (AnimationControllerActionCommand) prg.actions.get(2);

        SceneMaxScope scope = new SceneMaxScope();
        VariableDef varDef = new VariableDef();
        varDef.varName = "anim";
        VarInst var = new VarInst(varDef, scope);
        FakeAnimationRuntimeController runtimeController = new FakeAnimationRuntimeController();
        var.value = runtimeController;
        scope.vars_index.put("anim", var);

        AnimationControllerActionController controller =
                new AnimationControllerActionController(null, prg, scope, command);

        assertFalse(controller.run(0.016f));
        assertTrue(controller.run(0.016f));
        assertEquals(1, runtimeController.rewindStartCount);
        assertEquals(2, runtimeController.rewindUpdateCount);
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
        int rewindStartCount;
        int rewindUpdateCount;

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

        @Override
        public void startRewind(double percent, double durationSeconds, MotionEase.MotionEaseSpec easeSpec) {
            rewindStartCount++;
        }

        @Override
        public boolean updateRewind(float tpf) {
            rewindUpdateCount++;
            return rewindUpdateCount >= 2;
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
