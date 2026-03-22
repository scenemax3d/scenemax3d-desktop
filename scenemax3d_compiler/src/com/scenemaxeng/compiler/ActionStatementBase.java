package com.scenemaxeng.compiler;

import java.util.ArrayList;

public class ActionStatementBase extends StatementDef{

    public String lastError="";

    public String targetVar;
    public int varLineNum;
    public VariableDef varDef = null;

    public boolean isAsync = false;
    public ArrayList<ActionStatementBase> statements = new ArrayList<>();


    @Override
    public boolean validate(ProgramDef prg) {

        for(ActionStatementBase ac : statements) {
            if(!ac.validate(prg)) {
                return false;
            }
        }

        return true;
    }
}
