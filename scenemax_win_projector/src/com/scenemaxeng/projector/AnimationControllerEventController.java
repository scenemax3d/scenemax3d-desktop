package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.AnimationControllerEventCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class AnimationControllerEventController extends SceneMaxBaseController {

    public AnimationControllerEventController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope,
                                              AnimationControllerEventCommand cmd) {
        super(app, prg, scope, cmd);
    }

    @Override
    public boolean run(float tpf) {
        AnimationControllerEventCommand event = (AnimationControllerEventCommand) cmd;
        VarInst var = scope.getVar(event.targetVar);
        if (var == null || !(var.value instanceof AnimationRuntimeController)) {
            app.handleRuntimeError("Animation controller '" + event.targetVar + "' is not defined");
            return true;
        }

        Object percentValue = new ActionLogicalExpressionVm(event.percentExpr, scope).evaluate();
        double percent = ActionLogicalExpressionVm.toDouble(percentValue);
        ((AnimationRuntimeController) var.value).addEvent(event.animationName, percent, event.doBlock);
        return true;
    }
}
