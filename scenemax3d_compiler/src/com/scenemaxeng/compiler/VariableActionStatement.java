package com.scenemaxeng.compiler;

public class VariableActionStatement extends ActionStatementBase {


    @Override
    public boolean validate(ProgramDef prg) {

        checkVariableExistsError();
        return true;

    }

    protected void checkVariableExistsError() {
        if(targetVar!=null && this.varDef==null) {
            this.lastError="Object '"+targetVar+"' doesn't exist ";
        }
    }

}
