package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class AnimationControllerEventCommand extends VariableActionStatement {

    public String animationName;
    public SceneMaxParser.Logical_expressionContext percentExpr;
    public DoBlockCommand doBlock;

    @Override
    public boolean validate(ProgramDef prg) {
        if (varDef == null || varDef.varType != VariableDef.VAR_TYPE_ANIMATION_CONTROLLER) {
            lastError = "Animation controller '" + targetVar + "' doesn't exist ";
            return false;
        }

        if (percentExpr == null) {
            lastError = "Animation controller event '" + animationName + "' requires an animation percent";
            return false;
        }

        return true;
    }
}
