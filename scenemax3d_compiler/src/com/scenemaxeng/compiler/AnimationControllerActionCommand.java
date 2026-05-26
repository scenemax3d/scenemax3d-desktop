package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

import java.util.Collections;
import java.util.List;

public class AnimationControllerActionCommand extends VariableActionStatement {

    public static final int RUN = 1;
    public static final int STOP = 2;
    public static final int REWIND = 3;

    public int action;
    public SceneMaxParser.Logical_expressionContext rewindPercentExpr;
    public SceneMaxParser.Logical_expressionContext rewindDurationExpr;
    public int motionEaseType = MotionEaseType.LINEAR;
    public String motionEaseFunction;
    public List<SceneMaxParser.Logical_expressionContext> motionEaseParamExprs = Collections.emptyList();

    @Override
    public boolean validate(ProgramDef prg) {
        if (varDef == null || varDef.varType != VariableDef.VAR_TYPE_ANIMATION_CONTROLLER) {
            lastError = "Animation controller '" + targetVar + "' doesn't exist ";
            return false;
        }

        return true;
    }
}
