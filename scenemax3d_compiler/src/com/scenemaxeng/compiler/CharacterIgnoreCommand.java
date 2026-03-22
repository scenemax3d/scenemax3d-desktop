package com.scenemaxeng.compiler;

public class CharacterIgnoreCommand extends ActionStatementBase {
    public String ignoreVar;

    @Override
    public boolean validate(ProgramDef prg) {
        this.varDef = prg.getVar(targetVar);
        return (this.varDef!=null);
    }
}
