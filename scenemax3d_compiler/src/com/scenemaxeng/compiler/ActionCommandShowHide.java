package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class ActionCommandShowHide extends ActionStatementBase {

    public String varName;
    public boolean show;
    public boolean info;
    public String infoDumpFile;
    public boolean wireframe;
    public boolean axisX;
    public boolean axisY;
    public boolean axisZ;
    public boolean speedo;
    public boolean tacho;
    public boolean joints;
    public SceneMaxParser.Logical_expressionContext showJointsSizeExpr;
    public Double showJointsSizeVal;
    public boolean outline;


    @Override
    public boolean validate(ProgramDef prg) {

        this.varDef = prg.getVar(varName);
        if(this.varDef==null) {
            this.lastError="Cannot find variable: "+varName;
        }
        return (this.varDef!=null);

    }


}
