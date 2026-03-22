package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.CarSteerCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class CarSteerController extends SceneMaxBaseController {

    public CarSteerController(SceneMaxApp app, ProgramDef prg, SceneMaxScope thread, CarSteerCommand cmd) {
        super(app, prg, thread, cmd);

    }


    public boolean run(float tpf) {

        if (forceStop) return true;

        CarSteerCommand cmd = (CarSteerCommand) this.cmd;
        RunTimeVarDef entity = findTargetVar(cmd.targetVar);
        Double steer = (Double) new ActionLogicalExpression(cmd.steerExp, this.scope).evaluate();

        app.carSteer(entity, steer);
        return true;

    }

}
