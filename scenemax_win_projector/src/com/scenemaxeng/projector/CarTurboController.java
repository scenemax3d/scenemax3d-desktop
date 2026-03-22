package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.CarTurboCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class CarTurboController extends SceneMaxBaseController{

    public CarTurboController(SceneMaxApp app, ProgramDef prg, SceneMaxScope thread, CarTurboCommand cmd) {
        super(app, prg, thread, cmd);

    }


    public boolean run(float tpf) {

        if (forceStop) return true;

        CarTurboCommand cmd = (CarTurboCommand) this.cmd;
        RunTimeVarDef entity = findTargetVar(cmd.targetVar);
        Double x = (Double) new ActionLogicalExpression(cmd.xExpr, this.scope).evaluate();
        Double y = (Double) new ActionLogicalExpression(cmd.yExpr, this.scope).evaluate();
        Double z = (Double) new ActionLogicalExpression(cmd.zExpr, this.scope).evaluate();

        app.carTurbo(entity, x,y,z);
        return true;

    }
}
