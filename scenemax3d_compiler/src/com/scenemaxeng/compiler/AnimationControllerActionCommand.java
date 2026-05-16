package com.scenemaxeng.compiler;

public class AnimationControllerActionCommand extends VariableActionStatement {

    public static final int RUN = 1;
    public static final int STOP = 2;

    public int action;

    @Override
    public boolean validate(ProgramDef prg) {
        if (varDef == null || varDef.varType != VariableDef.VAR_TYPE_ANIMATION_CONTROLLER) {
            lastError = "Animation controller '" + targetVar + "' doesn't exist ";
            return false;
        }

        return true;
    }
}
