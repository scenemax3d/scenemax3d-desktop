package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.CameraModifierApplyCommand;
import com.scenemaxeng.compiler.ProgramDef;

import java.util.LinkedHashMap;
import java.util.Map;

public class CameraModifierApplyController extends SceneMaxBaseController {

    public CameraModifierApplyController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, CameraModifierApplyCommand cmd) {
        super(app, prg, scope, cmd);
    }

    @Override
    public boolean run(float tpf) {
        if (forceStop) {
            return true;
        }

        CameraModifierApplyCommand cmd = (CameraModifierApplyCommand) this.cmd;
        VarInst systemVar = scope.getVar(cmd.targetVar);
        if (systemVar == null || !(systemVar.value instanceof RuntimeCameraSystemValue)) {
            app.handleRuntimeError("Camera system '" + cmd.targetVar + "' is not available for modifiers");
            return true;
        }

        VarInst modifierVar = scope.getVar(cmd.modifierVar);
        if (modifierVar == null || !(modifierVar.value instanceof RuntimeCameraModifierValue)) {
            app.handleRuntimeError("Camera modifier '" + cmd.modifierVar + "' is not defined");
            return true;
        }

        Map<String, Float> overrides = new LinkedHashMap<>();
        for (Map.Entry<String, com.abware.scenemaxlang.parser.SceneMaxParser.Logical_expressionContext> entry : cmd.overrideExprs.entrySet()) {
            overrides.put(entry.getKey(),
                    (float) ActionLogicalExpressionVm.toDouble(new ActionLogicalExpressionVm(entry.getValue(), this.scope).evaluate()));
        }

        app.applyCameraModifier((RuntimeCameraSystemValue) systemVar.value,
                (RuntimeCameraModifierValue) modifierVar.value,
                overrides,
                cmd.targetVar,
                cmd.targetVarLine);

        return true;
    }
}
