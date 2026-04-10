package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

import java.util.LinkedHashMap;
import java.util.Map;

public class CameraModifierApplyCommand extends VariableActionStatement {
    public String modifierVar;
    public VariableDef modifierVarDef;
    public int targetVarLine;
    public final Map<String, SceneMaxParser.Logical_expressionContext> overrideExprs = new LinkedHashMap<>();

    @Override
    public boolean validate(ProgramDef prg) {
        checkVariableExistsError();
        if (modifierVarDef == null) {
            this.lastError = "Modifier '" + modifierVar + "' doesn't exist";
            return false;
        }
        return true;
    }
}
