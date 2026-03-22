package com.scenemaxeng.compiler;

public class KillEntityCommand extends ActionStatementBase {


    @Override
    public boolean validate(ProgramDef prg) {

        this.varDef = prg.getVar(targetVar);
        return (this.varDef!=null);

    }

}
