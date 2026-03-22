package com.scenemaxeng.compiler;

import org.antlr.v4.runtime.ParserRuleContext;

public class ActionCommandPlay extends VariableActionStatement {

    public String loopTimes;

    public int durationStrategy = 0;//0=once 1=time 2=loop
    public ParserRuleContext speedExpr;
    public ParserRuleContext forTimeExpr;
    public ParserRuleContext fromFrameExpr;
    public ParserRuleContext toFrameExpr;

    @Override
    public boolean validate(ProgramDef prg) {

        try {

            this.varDef = prg.getVar(targetVar);
            checkVariableExistsError();
            return (this.varDef != null);
        }catch(Exception e) {
            return false;
        }

    }

}
