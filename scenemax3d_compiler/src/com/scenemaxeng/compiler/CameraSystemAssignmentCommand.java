package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class CameraSystemAssignmentCommand extends ActionStatementBase {
    public boolean resetToDefault;
    public SceneMaxParser.Logical_expressionContext valueExpr;

    @Override
    public boolean validate(ProgramDef prg) {
        this.varDef = prg.getVar("camera");
        if (this.varDef == null) {
            this.lastError = "Camera is not available";
            return false;
        }
        return true;
    }
}
