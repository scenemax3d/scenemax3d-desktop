package com.scenemaxeng.compiler;

public class AnimationControllerAssignmentCommand extends ActionStatementBase {

    public String animationName;
    public String sourceVar;
    public VariableDef sourceVarDef;

    @Override
    public boolean validate(ProgramDef prg) {
        if (varDef == null) {
            lastError = "Animation controller variable '" + targetVar + "' doesn't exist ";
            return false;
        }

        if (sourceVarDef == null) {
            lastError = "Object '" + sourceVar + "' doesn't exist ";
            return false;
        }

        return true;
    }
}
