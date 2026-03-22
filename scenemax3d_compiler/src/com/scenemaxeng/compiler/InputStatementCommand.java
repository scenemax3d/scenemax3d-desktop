package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class InputStatementCommand extends ActionStatementBase {

    public String inputType;
    public String inputKey;
    public DoBlockCommand doBlock;
    public boolean once;
    public SceneMaxParser.Logical_expressionContext goExpr;
    public boolean released = false;

    @Override
    public boolean validate(ProgramDef prg) {

        this.varDef = prg.getVar(targetVar);
        return true; // always return true since this specific command can have null variable def

    }

}
