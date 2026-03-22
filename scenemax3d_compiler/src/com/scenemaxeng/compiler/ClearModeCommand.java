package com.scenemaxeng.compiler;

public class ClearModeCommand extends VariableActionStatement {

    public String varName;
    public int modeToClear;

    @Override
    public boolean validate(ProgramDef prg) {

        this.varDef = prg.getVar(varName);
        checkVariableExistsError();
        return (this.varDef!=null);

    }

}
