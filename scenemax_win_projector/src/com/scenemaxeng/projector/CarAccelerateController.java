package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.AccelerateCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class CarAccelerateController extends SceneMaxBaseController {

    public CarAccelerateController(SceneMaxApp app, ProgramDef prg, SceneMaxScope thread, AccelerateCommand cmd) {
        super(app, prg, thread, cmd);

    }


    public boolean run(float tpf) {

        if (forceStop) return true;

        AccelerateCommand cmd = (AccelerateCommand) this.cmd;
        RunTimeVarDef entity = findTargetVar(cmd.targetVar);
        Double accelerate = (Double) new ActionLogicalExpression(cmd.accelerateExp, this.scope).evaluate();

        app.carAccelerate(entity, accelerate);
        return true;

    }

}
