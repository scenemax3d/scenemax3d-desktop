package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class ThrowMotionEventCommand extends VariableActionStatement {
    public static final String EVENT_ON_END = "on_end";
    public static final String EVENT_ON_INDEX = "on_index";

    public String eventName;
    public SceneMaxParser.Logical_expressionContext indexPercentExpr;
    public DoBlockCommand doBlock;

    @Override
    public boolean validate(ProgramDef prg) {
        if (varDef == null || varDef.varType != VariableDef.VAR_TYPE_THROW_MOTION) {
            lastError = "Motion '" + targetVar + "' doesn't exist ";
            return false;
        }

        if (!EVENT_ON_END.equals(eventName) && !EVENT_ON_INDEX.equals(eventName)) {
            lastError = "Unsupported motion event '" + eventName + "'";
            return false;
        }

        if (EVENT_ON_INDEX.equals(eventName) && indexPercentExpr == null) {
            lastError = "Motion event 'on_index' requires an index percent";
            return false;
        }

        if (EVENT_ON_END.equals(eventName) && indexPercentExpr != null) {
            lastError = "Motion event 'on_end' does not accept an index percent";
            return false;
        }

        return true;
    }
}
