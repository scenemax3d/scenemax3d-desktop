package com.scenemaxeng.compiler;

public class ObjectPoolAcquireCommand extends ActionStatementBase {
    public String poolVarName;
    public VariableDef poolVarDef;
    public String resultVarName;
    public VariableDef resultVarDef;

    @Override
    public boolean validate(ProgramDef prg) {
        if (poolVarDef == null || poolVarDef.varType != VariableDef.VAR_TYPE_OBJECT_POOL) {
            lastError = "Object pool '" + poolVarName + "' is not defined";
            return false;
        }
        if (resultVarDef == null) {
            lastError = "Variable '" + resultVarName + "' is not defined";
            return false;
        }
        return true;
    }
}
