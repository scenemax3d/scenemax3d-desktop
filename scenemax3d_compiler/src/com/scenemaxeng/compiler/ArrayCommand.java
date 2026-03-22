package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class ArrayCommand extends ActionStatementBase {

    public enum ArrayAction {
        Push,
        Pop,
        Clear
    }

    public ArrayAction action;
    public String varName;
    public SceneMaxParser.Logical_expressionContext expr;

    public boolean validate(ProgramDef prg) {

        this.varDef = prg.getVar(varName);
        if(this.varDef==null) {
            this.lastError="Cannot find array: "+varName;
        }
        return (this.varDef!=null);

    }

}
