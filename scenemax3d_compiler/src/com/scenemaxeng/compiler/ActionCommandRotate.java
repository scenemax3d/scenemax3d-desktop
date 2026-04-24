package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;
import org.antlr.v4.runtime.ParserRuleContext;

public class ActionCommandRotate extends TimedVariableMotionCommand {
    //public String targetVar;
    public String axis;
    public String numSign;
    public String num;
    public String speed;
    public ParserRuleContext numExpr;
    public ParserRuleContext speedExp;
    public SceneMaxParser.Logical_expressionContext loopExpr;

    @Override
    public boolean validate(ProgramDef prg) {

        try {
            if (targetVar == null) {
                for (ActionStatementBase ar : statements) {
                    if (!ar.validate(prg)) {
                        this.lastError+=ar.lastError;
                        return false;
                    }
                }

                return true;
            }


            axis = axis.toLowerCase();
            if (axis == null || !(axis.equals("x") || axis.equals("y") || axis.equals("z"))) {
                this.lastError="Expecting 'X' or 'Y' or 'Z' axis name ";
                return false;
            }

            checkVariableExistsError();
            return (this.varDef != null);

        }catch(Exception e) {
            return false;
        }

    }
}
