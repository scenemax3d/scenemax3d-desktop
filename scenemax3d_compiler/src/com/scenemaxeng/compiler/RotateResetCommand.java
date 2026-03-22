package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class RotateResetCommand extends ActionStatementBase {
    public SceneMaxParser.Logical_expressionContext xExpr;
    public SceneMaxParser.Logical_expressionContext yExpr;
    public SceneMaxParser.Logical_expressionContext zExpr;

    @Override
    public boolean validate(ProgramDef prg) {

        this.varDef = prg.getVar(targetVar);
        return (this.varDef!=null);

    }
}
