package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class ObjectPoolCreateCommand extends ActionStatementBase {
    public String poolVarName;
    public VariableDef poolVarDef;
    public String sourceName;
    public VariableDef sourceVarDef;
    public FunctionBlockDef sourceFunctionDef;
    public boolean sourceIsFunction;
    public SceneMaxParser.Logical_expressionContext initialSizeExpr;

    @Override
    public boolean validate(ProgramDef prg) {
        if (poolVarDef == null || poolVarDef.varType != VariableDef.VAR_TYPE_OBJECT_POOL) {
            lastError = "Object pool '" + poolVarName + "' is not defined";
            return false;
        }
        sourceFunctionDef = prg.getFunc(sourceName);
        sourceIsFunction = sourceFunctionDef != null;
        if (sourceIsFunction) {
            return true;
        }
        if (sourceVarDef == null) {
            lastError = "Object pool source '" + sourceName + "' is not defined";
            return false;
        }
        return true;
    }
}
