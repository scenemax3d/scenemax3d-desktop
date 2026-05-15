package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.AnimationControllerActionCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class AnimationControllerActionController extends SceneMaxBaseController {
    private boolean started;

    public AnimationControllerActionController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope,
                                               AnimationControllerActionCommand cmd) {
        super(app, prg, scope, cmd);
    }

    @Override
    public boolean run(float tpf) {
        AnimationRuntimeController controller = getRuntimeController();
        if (controller == null) {
            return true;
        }

        AnimationControllerActionCommand action = (AnimationControllerActionCommand) cmd;
        if (action.action == AnimationControllerActionCommand.RUN) {
            if (!started) {
                controller.run();
                started = true;
            }
            boolean finished = controller.update(tpf);
            if (finished) {
                started = false;
            }
            return finished;
        } else if (action.action == AnimationControllerActionCommand.STOP) {
            controller.stop();
            started = false;
        }
        return true;
    }

    private AnimationRuntimeController getRuntimeController() {
        VarInst var = scope.getVar(cmd.targetVar);
        if (var == null || !(var.value instanceof AnimationRuntimeController)) {
            app.handleRuntimeError("Animation controller '" + cmd.targetVar + "' is not defined");
            return null;
        }
        return (AnimationRuntimeController) var.value;
    }
}
