package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class ChangeAngularVelocityCommand extends ActionStatementBase {

    public SceneMaxParser.Logical_expressionContext angularVelocityExpr;

    @Override
    public boolean validate(ProgramDef prg) {
        this.varDef = prg.getVar(targetVar);
        return this.varDef != null;
    }
}
