package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import com.scenemaxeng.compiler.ThrowMotionApplyCommand;
import com.scenemaxeng.compiler.VariableAssignmentCommand;
import com.scenemaxeng.compiler.VariableDef;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ThrowMotionRuntimeSyntaxTest {
    @Test
    public void parsesMotionCreationAndApplyWithObjectTarget() {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(
                "axe => dragon\n"
                        + "enemy => dragon\n"
                        + "m = system.motion(\"motion_axe\", target enemy)\n"
                        + "m.apply axe");

        assertTrue(String.join("\n", prg.syntaxErrors), prg.syntaxErrors.isEmpty());
        assertEquals(4, prg.actions.size());
        assertEquals(VariableDef.VAR_TYPE_THROW_MOTION, prg.getVar("m").varType);
        assertTrue(prg.actions.get(2) instanceof VariableAssignmentCommand);
        assertTrue(prg.actions.get(3) instanceof ThrowMotionApplyCommand);

        ThrowMotionApplyCommand apply = (ThrowMotionApplyCommand) prg.actions.get(3);
        assertEquals("m", apply.motionVarName);
        assertEquals("axe", apply.appliedObjectVarName);
    }

    @Test
    public void motionExpressionKeepsTargetVectorForRuntimeEvaluation() {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(
                "axe => dragon\n"
                        + "m = system.motion(\"motion_axe\", target (10, 2, 30))\n"
                        + "m.apply axe");

        assertTrue(String.join("\n", prg.syntaxErrors), prg.syntaxErrors.isEmpty());

        VariableAssignmentCommand assignment = (VariableAssignmentCommand) prg.actions.get(1);
        RuntimeThrowMotionValue value = (RuntimeThrowMotionValue)
                new ActionLogicalExpressionVm(assignment.values.get(0), new SceneMaxScope()).evaluate();

        assertEquals("motion_axe", value.motionAssetId);
        assertEquals(RuntimeThrowMotionValue.TargetKind.POSITION, value.targetKind);
        assertEquals(10.0, ActionLogicalExpressionVm.toDouble(
                new ActionLogicalExpressionVm(value.targetXExpr, new SceneMaxScope()).evaluate()), 0.001);
    }

    @Test
    public void declaredMotionVariableCanBeApplied() {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(
                "axe => dragon\n"
                        + "var m = system.motion(\"motion_axe\")\n"
                        + "m.apply axe");

        assertTrue(String.join("\n", prg.syntaxErrors), prg.syntaxErrors.isEmpty());
        assertEquals(VariableDef.VAR_TYPE_THROW_MOTION, prg.getVar("m").varType);
        assertTrue(prg.actions.get(2) instanceof ThrowMotionApplyCommand);
    }

    @Test
    public void motionApplySupportsAsyncLikeOtherActions() {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(
                "axe => dragon\n"
                        + "var m = system.motion(\"motion_axe\")\n"
                        + "m.apply axe async");

        assertTrue(String.join("\n", prg.syntaxErrors), prg.syntaxErrors.isEmpty());
        assertTrue(prg.actions.get(2) instanceof ThrowMotionApplyCommand);
        assertTrue(((ThrowMotionApplyCommand) prg.actions.get(2)).isAsync);
    }

    @Test
    public void motionApplySupportsEquippedWeaponTarget() {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(
                "player1 => dragon\n"
                        + "var m = system.motion(\"motion_axe\")\n"
                        + "m.apply player1.weapon async");

        assertTrue(String.join("\n", prg.syntaxErrors), prg.syntaxErrors.isEmpty());
        assertTrue(prg.actions.get(2) instanceof ThrowMotionApplyCommand);

        ThrowMotionApplyCommand apply = (ThrowMotionApplyCommand) prg.actions.get(2);
        assertEquals("m", apply.motionVarName);
        assertEquals("player1", apply.appliedObjectVarName);
        assertTrue(apply.appliedObjectIsEquippedWeapon);
        assertTrue(apply.isAsync);
    }
}
