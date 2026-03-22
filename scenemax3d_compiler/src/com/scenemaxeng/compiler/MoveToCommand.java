package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class MoveToCommand extends ActionStatementBase {

    public String moveToTarget;
    public SceneMaxParser.Logical_expressionContext extraDistanceExpr;
    public SceneMaxParser.Logical_expressionContext speedExpr;
    public SceneMaxParser.Logical_expressionContext moveToTargetXExpr;
    public SceneMaxParser.Logical_expressionContext moveToTargetYExpr;
    public SceneMaxParser.Logical_expressionContext moveToTargetZExpr;
    public PositionStatement posStatement;
    public PositionStatement lookingAtStatement;


    @Override
    public boolean validate(ProgramDef prg) {

        this.varDef = prg.getVar(targetVar);
        return (this.varDef!=null);

    }

}
