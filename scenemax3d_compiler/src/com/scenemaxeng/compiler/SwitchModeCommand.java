package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class SwitchModeCommand extends VariableActionStatement {

    public static final int CHARACTER = 10;
    public static final int RIGID_BODY = 20;
    public static final int RAGDOLL = 30;
    public static final int KINEMATIC = 40 ;
    public static final int FLOATING = 50 ;

    //public static final int CAR = 30;

    public String varName;
    public int switchTo;
    public SceneMaxParser.Logical_expressionContext gravityExpr;
    public Double gravityVal = 9.8;

    @Override
    public boolean validate(ProgramDef prg) {

        this.varDef = prg.getVar(varName);
        checkVariableExistsError();
        return (this.varDef!=null);

    }


}
