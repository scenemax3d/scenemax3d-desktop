package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class SetUserDataCommand extends ActionStatementBase {
    public String varName;
    public String fieldName;
    public SceneMaxParser.Logical_expressionContext dataExpr;

    @Override
    public boolean validate(ProgramDef prg) {

        this.varDef = prg.getVar(varName);
        return (this.varDef!=null);

    }
}
