package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class ChangeMassCommand extends ActionStatementBase {

    public String varName;
    public SceneMaxParser.Logical_expressionContext massExpr;


    @Override
    public boolean validate(ProgramDef prg) {

        this.varDef = prg.getVar(varName);
        return (this.varDef!=null);

    }



}
