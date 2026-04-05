package com.scenemaxeng.compiler;

import org.antlr.v4.runtime.ParserRuleContext;

import java.util.LinkedHashMap;
import java.util.Map;

public class EffekseerPlayCommand extends VariableActionStatement {

    public EntityPos entityPos;
    public ParserRuleContext xExpr;
    public ParserRuleContext yExpr;
    public ParserRuleContext zExpr;
    public final Map<String, ParserRuleContext> attrExprs = new LinkedHashMap<>();

    @Override
    public boolean validate(ProgramDef prg) {
        this.varDef = prg.getVar(targetVar);
        checkVariableExistsError();
        return this.varDef != null;
    }
}
