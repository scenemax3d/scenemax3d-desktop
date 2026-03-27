package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.VehicleSetupCommand;

public class CarSetupController extends SceneMaxBaseController{

    public CarSetupController(SceneMaxApp app, ProgramDef prg, SceneMaxScope thread, VehicleSetupCommand cmd) {
        super(app, prg, thread, cmd);

    }


    public boolean run(float tpf) {

        if (forceStop) return true;

        VehicleSetupCommand cmd = (VehicleSetupCommand) this.cmd;
        RunTimeVarDef entity = findTargetVar(cmd.targetVar);

        if(cmd.setupEngine) {
            if(cmd.enginePowerExp!=null) {
                cmd.enginePowerVal = (Double) new ActionLogicalExpressionVm(cmd.enginePowerExp, this.scope).evaluate();
            }

            if(cmd.engineBreakingExp!=null) {
                cmd.engineBreakingVal = (Double) new ActionLogicalExpressionVm(cmd.engineBreakingExp, this.scope).evaluate();
            }

        } else {


            if (cmd.compressionExpr != null) {
                cmd.compressionVal = (Double) new ActionLogicalExpressionVm(cmd.compressionExpr, this.scope).evaluate();
            }

            if (cmd.dampingExpr != null) {
                cmd.dampingVal = (Double) new ActionLogicalExpressionVm(cmd.dampingExpr, this.scope).evaluate();
            }

            if (cmd.frictionExpr != null) {
                cmd.frictionVal = (Double) new ActionLogicalExpressionVm(cmd.frictionExpr, this.scope).evaluate();
            }

            if (cmd.stiffnessExpr != null) {
                cmd.stiffnessVal = (Double) new ActionLogicalExpressionVm(cmd.stiffnessExpr, this.scope).evaluate();
            }

            if (cmd.lengthExpr != null) {
                cmd.lengthVal = (Double) new ActionLogicalExpressionVm(cmd.lengthExpr, this.scope).evaluate();
            }

        }


        app.setupVehicle(entity,cmd);

        return true;
    }

}
