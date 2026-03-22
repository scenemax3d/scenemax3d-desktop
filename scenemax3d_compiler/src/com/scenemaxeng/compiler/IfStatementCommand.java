package com.scenemaxeng.compiler;

import org.antlr.v4.runtime.ParserRuleContext;

import java.util.List;

public class IfStatementCommand extends ActionStatementBase {

    public List<IfStatementCommand> elseIfCommands = null;//new ArrayList<>();
    public DoBlockCommand elseCmd = null;
    public DoBlockCommand doBlock;
    public ParserRuleContext expression;


}
