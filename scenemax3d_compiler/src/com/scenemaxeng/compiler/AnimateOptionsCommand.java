package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class AnimateOptionsCommand extends VariableActionStatement {
    public SceneMaxParser.Logical_expressionContext speedExpr;
    public Double speedVal;
    public SceneMaxParser.Logical_expressionContext forTimeExpr;
    public Double forTimeVal;
    public SceneMaxParser.Logical_expressionContext aboveFramesExpr;
    public Double aboveFramesVal;

    @Override
    public boolean validate(ProgramDef prg) {

        this.varDef = prg.getVar(targetVar);
        return (this.varDef!=null);

    }

}
