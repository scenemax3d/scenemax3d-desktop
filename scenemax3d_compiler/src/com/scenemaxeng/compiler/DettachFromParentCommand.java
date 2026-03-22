package com.scenemaxeng.compiler;

public class DettachFromParentCommand extends ActionStatementBase {


    @Override
    public boolean validate(ProgramDef prg) {

        this.varDef = prg.getVar(targetVar);
        return (this.varDef!=null);

    }

}
