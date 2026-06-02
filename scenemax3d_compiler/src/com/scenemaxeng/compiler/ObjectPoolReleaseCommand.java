package com.scenemaxeng.compiler;

public class ObjectPoolReleaseCommand extends ActionStatementBase {
    public String poolVarName;
    public VariableDef poolVarDef;
    public String objectVarName;
    public VariableDef objectVarDef;

    @Override
    public boolean validate(ProgramDef prg) {
        if (poolVarDef == null || poolVarDef.varType != VariableDef.VAR_TYPE_OBJECT_POOL) {
            lastError = "Object pool '" + poolVarName + "' is not defined";
            return false;
        }
        if (objectVarDef == null) {
            lastError = "Object '" + objectVarName + "' is not defined";
            return false;
        }
        return true;
    }
}
