package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ActionCommandMove;
import com.scenemaxeng.compiler.ActionCommandRotate;
import com.scenemaxeng.compiler.ActionCommandRotateTo;
import com.scenemaxeng.compiler.DirectionalMoveCommand;
import com.scenemaxeng.compiler.MotionEaseType;
import com.scenemaxeng.compiler.MoveToCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MovementEaseParsingTest {

    @Test
    public void parsesEaseModesAcrossMovementAndRotationCommands() {
        String code = "d=>dragon\n"
                + "d.move left 3 in 2 seconds ease in out\n"
                + "d.move to (1,2,3) in 2 seconds ease out\n"
                + "d.move forward 4 for 2 seconds ease in\n"
                + "d.turn left 90 in 1 second ease out\n"
                + "d.rotate to (y 180) in 2 seconds ease in";

        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(code);

        assertTrue(prg.syntaxErrors.isEmpty());

        ActionCommandMove moveVerbal = (ActionCommandMove) prg.actions.get(1);
        assertEquals(MotionEaseType.EASE_IN_OUT, ((ActionCommandMove) moveVerbal.statements.get(0)).motionEaseType);

        MoveToCommand moveTo = (MoveToCommand) prg.actions.get(2);
        assertEquals(MotionEaseType.EASE_OUT, moveTo.motionEaseType);

        DirectionalMoveCommand directionalMove = (DirectionalMoveCommand) prg.actions.get(3);
        assertEquals(MotionEaseType.EASE_IN, directionalMove.motionEaseType);

        ActionCommandRotate turn = (ActionCommandRotate) prg.actions.get(4);
        assertEquals(MotionEaseType.EASE_OUT, ((ActionCommandRotate) turn.statements.get(0)).motionEaseType);

        ActionCommandRotateTo rotateTo = (ActionCommandRotateTo) prg.actions.get(5);
        assertEquals(MotionEaseType.EASE_IN, rotateTo.motionEaseType);
    }

    @Test
    public void parsesNamedEaseFunctionsAndParamsAcrossMovementCommands() {
        String code = "d=>dragon\n"
                + "d.move to (1,2,3) in 3 seconds ease in out \"Cubic\" (0.25)\n"
                + "d.move to (1,2,3) in 3 seconds ease out \"CubicBezier\" (0.25,0.1,0.25,1)\n"
                + "d.move to (1,2,3) in 3 seconds ease out \"Elastic\" (1.2, 0.4)\n"
                + "d.turn left 90 in 1 second ease out \"Back\" (2.0)\n"
                + "d.rotate to (y 180) in 2 seconds ease in \"Sine\"";

        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(code);

        assertTrue(prg.syntaxErrors.isEmpty());

        MoveToCommand cubic = (MoveToCommand) prg.actions.get(1);
        assertEquals(MotionEaseType.EASE_IN_OUT, cubic.motionEaseType);
        assertEquals("Cubic", cubic.motionEaseFunction);
        assertEquals(1, cubic.motionEaseParamExprs.size());

        MoveToCommand bezier = (MoveToCommand) prg.actions.get(2);
        assertEquals(MotionEaseType.EASE_OUT, bezier.motionEaseType);
        assertEquals("CubicBezier", bezier.motionEaseFunction);
        assertEquals(4, bezier.motionEaseParamExprs.size());

        MoveToCommand elastic = (MoveToCommand) prg.actions.get(3);
        assertEquals("Elastic", elastic.motionEaseFunction);
        assertEquals(2, elastic.motionEaseParamExprs.size());

        ActionCommandRotate turn = (ActionCommandRotate) prg.actions.get(4);
        ActionCommandRotate turnAxis = (ActionCommandRotate) turn.statements.get(0);
        assertEquals("Back", turnAxis.motionEaseFunction);
        assertEquals(1, turnAxis.motionEaseParamExprs.size());

        ActionCommandRotateTo rotateTo = (ActionCommandRotateTo) prg.actions.get(5);
        assertNotNull(rotateTo.motionEaseFunction);
        assertEquals("Sine", rotateTo.motionEaseFunction);
    }
}
