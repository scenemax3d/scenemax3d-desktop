package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.CarBrakeCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class CarBrakeController extends SceneMaxBaseController{

    public CarBrakeController(SceneMaxApp app, ProgramDef prg, SceneMaxScope thread, CarBrakeCommand cmd) {
        super(app, prg, thread, cmd);

    }


    public boolean run(float tpf) {

        if (forceStop) return true;

        CarBrakeCommand cmd = (CarBrakeCommand) this.cmd;
        RunTimeVarDef entity = findTargetVar(cmd.targetVar);
        Double brake = (Double) new ActionLogicalExpression(cmd.brakeExp, this.scope).evaluate();

        app.carBrake(entity, brake);
        return true;

    }
}
