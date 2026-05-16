package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.ThrowMotionEventCommand;

public class ThrowMotionEventController extends SceneMaxBaseController {

    public ThrowMotionEventController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, ThrowMotionEventCommand cmd) {
        super(app, prg, scope, cmd);
    }

    @Override
    public boolean run(float tpf) {
        ThrowMotionEventCommand event = (ThrowMotionEventCommand) cmd;
        VarInst var = scope.getVar(event.targetVar);
        if (var == null || !(var.value instanceof RuntimeThrowMotionValue)) {
            app.handleRuntimeError("Motion '" + event.targetVar + "' is not defined");
            return true;
        }

        double indexPercent = 100.0;
        if (ThrowMotionEventCommand.EVENT_ON_INDEX.equals(event.eventName)) {
            Object indexValue = new ActionLogicalExpressionVm(event.indexPercentExpr, scope).evaluate();
            indexPercent = ActionLogicalExpressionVm.toDouble(indexValue);
        }

        ((RuntimeThrowMotionValue) var.value).addEvent(event.eventName, indexPercent, event.doBlock);
        return true;
    }
}
