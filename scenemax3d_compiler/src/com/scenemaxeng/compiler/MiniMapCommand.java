package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class MiniMapCommand extends ActionStatementBase {

    public boolean show;
    public SceneMaxParser.Logical_expressionContext heightExpr;
    public SceneMaxParser.Logical_expressionContext sizeExpr;

    public int sizeVal;
    public float heightVal;

    @Override
    public boolean validate(ProgramDef prg) {

        if(targetVar==null) {
            return true;
        }

        this.varDef = prg.getVar(targetVar);
        return (this.varDef!=null);

    }

}
