package com.scenemaxeng.compiler;

public class VideoPlayCommand extends VariableActionStatement {

    public String targetObjectVar;
    public String startTimestamp;
    public String endTimestamp;
    public boolean reverse;
    public boolean loop;

    @Override
    public boolean validate(ProgramDef prg) {
        this.varDef = prg.getVar(targetVar);
        checkVariableExistsError();
        if (this.varDef == null) {
            return false;
        }
        if (this.varDef.varType != VariableDef.VAR_TYPE_VIDEO) {
            this.lastError = "Object '" + targetVar + "' is not a video resource";
            return false;
        }
        if (targetObjectVar == null || prg.getVar(targetObjectVar) == null) {
            this.lastError = "Target object '" + targetObjectVar + "' doesn't exist";
            return false;
        }
        return true;
    }
}
