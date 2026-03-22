package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class ActionCommandRotateTo extends VariableActionStatement {
    public String axis;
    public SceneMaxParser.Logical_expressionContext speedExpr;
    public SceneMaxParser.Logical_expressionContext rotateValExpr;

    @Override
    public boolean validate(ProgramDef prg) {

        this.varDef = prg.getVar(targetVar);
        return (this.varDef!=null);

    }

}
