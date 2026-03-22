package com.scenemaxeng.compiler;

public class StatementDef {

    public boolean requireResource = false; // indicate run-time that this needs attention in resource loading phase
    public boolean validate(ProgramDef prg) {
        return true;
    }
}
