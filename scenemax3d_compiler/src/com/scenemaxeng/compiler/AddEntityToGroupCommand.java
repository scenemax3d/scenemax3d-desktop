package com.scenemaxeng.compiler;

public class AddEntityToGroupCommand extends ActionStatementBase {

    public String targetGroup;

    @Override
    public boolean validate(ProgramDef prg) {

        this.varDef = prg.getVar(this.targetVar);
        return (this.varDef!=null);

    }

}
