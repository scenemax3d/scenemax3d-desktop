package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class CharacterJumpCommand extends ActionStatementBase {

    public SceneMaxParser.Logical_expressionContext speedExpr;

    @Override
    public boolean validate(ProgramDef prg) {

        this.varDef = prg.getVar(targetVar);
        return (this.varDef!=null);

    }



}
