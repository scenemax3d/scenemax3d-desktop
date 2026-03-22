package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;
import org.antlr.v4.runtime.ParserRuleContext;

public class ActionCommandMove extends VariableActionStatement {

    public static final int VERBAL_MOVE_LEFT = 10;
    public static final int VERBAL_MOVE_RIGHT = 20;
    public static final int VERBAL_MOVE_FORWARD = 30;
    public static final int VERBAL_MOVE_BACKWARD = 40;
    public static final int VERBAL_MOVE_UP = 50;
    public static final int VERBAL_MOVE_DOWN = 60;

    public int verbalCommand;

    public String axis;
    public String numSign;
    public String num;
    //public String speed;
    public ParserRuleContext numExpr;
    public ParserRuleContext speedExpr;
    public SceneMaxParser.Loop_exprContext loopExpr;

    @Override
    public boolean validate(ProgramDef prg) {

        if(targetVar==null) {
            for(ActionStatementBase ar:statements) {
                if(!ar.validate(prg)) {
                    this.lastError += ar.lastError;
                    return false;
                }
            }

            return true;
        }

        checkVariableExistsError();
        return (this.varDef!=null);

    }


}
