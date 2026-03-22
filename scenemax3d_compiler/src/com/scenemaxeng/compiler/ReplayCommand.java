package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class ReplayCommand extends ActionStatementBase {

    public static final int SWITCH_TO = 10 ;
    public static final int STOP = 20;
    public static final int PAUSE = 30;
    public static final int RESUME = 40;
    public static final int CHANGE_SPEED = 50;

    public String dataArrayName;
    public SceneMaxParser.Logical_expressionContext startAtExpr;
    public SceneMaxParser.Logical_expressionContext speedExpr;
    public SceneMaxParser.Logical_expressionContext loopExpr;
    public int option = 0;

    public SceneMaxParser.Logical_expressionContext offsetXExpr;
    public SceneMaxParser.Logical_expressionContext offsetYExpr;
    public SceneMaxParser.Logical_expressionContext offsetZExpr;
    public SceneMaxParser.Logical_expressionContext offsetRXExpr;
    public SceneMaxParser.Logical_expressionContext offsetRYExpr;
    public SceneMaxParser.Logical_expressionContext offsetRZExpr;

    @Override
    public boolean validate(ProgramDef prg) {

        this.varDef = prg.getVar(targetVar);
        return (this.varDef!=null);

    }

}
