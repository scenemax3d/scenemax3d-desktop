package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class SetMaterialCommand extends ActionStatementBase {


    public SceneMaxParser.Logical_expressionContext materialNameExpr;

    @Override
    public boolean validate(ProgramDef prg) {

        this.varDef = prg.getVar(targetVar);
        return (this.varDef!=null);

    }

}
