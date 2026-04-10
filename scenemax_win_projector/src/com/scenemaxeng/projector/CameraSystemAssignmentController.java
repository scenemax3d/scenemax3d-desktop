package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.CameraSystemAssignmentCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class CameraSystemAssignmentController extends SceneMaxBaseController {

    public CameraSystemAssignmentController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, CameraSystemAssignmentCommand cmd) {
        super(app, prg, scope, cmd);
    }

    @Override
    public boolean run(float tpf) {
        if (forceStop) {
            return true;
        }

        CameraSystemAssignmentCommand cmd = (CameraSystemAssignmentCommand) this.cmd;
        if (cmd.resetToDefault) {
            app.turnOffCameraStates();
            return true;
        }

        Object value = new ActionLogicalExpressionVm(cmd.valueExpr, this.scope).evaluate();
        if (!(value instanceof RuntimeCameraSystemValue)) {
            app.handleRuntimeError("camera.system expects a camera system value or default");
            return true;
        }

        RuntimeCameraSystemValue systemValue = (RuntimeCameraSystemValue) value;
        if (RuntimeCameraSystemValue.TYPE_FIGHTING.equalsIgnoreCase(systemValue.systemType)) {
            app.setFightingCameraOn(this.scope, systemValue);
        } else if (RuntimeCameraSystemValue.TYPE_THIRD_PERSON.equalsIgnoreCase(systemValue.systemType)
                || RuntimeCameraSystemValue.TYPE_FIRST_PERSON.equalsIgnoreCase(systemValue.systemType)
                || RuntimeCameraSystemValue.TYPE_RACING.equalsIgnoreCase(systemValue.systemType)
                || RuntimeCameraSystemValue.TYPE_PLATFORMER.equalsIgnoreCase(systemValue.systemType)
                || RuntimeCameraSystemValue.TYPE_RTS.equalsIgnoreCase(systemValue.systemType)) {
            app.setGameplayCameraOn(this.scope, systemValue);
        } else {
            app.handleRuntimeError("Unsupported camera system '" + systemValue.systemType + "'");
        }

        return true;
    }
}
