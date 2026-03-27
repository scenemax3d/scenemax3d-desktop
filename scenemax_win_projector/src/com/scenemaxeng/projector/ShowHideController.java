package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ActionCommandShowHide;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.VariableDef;

public class ShowHideController extends SceneMaxBaseController {

    public ShowHideController(SceneMaxApp app, ProgramDef prg, ActionCommandShowHide cmd, SceneMaxScope thread) {
        super(app,prg,thread,cmd);

    }

    //
    @Override
    public boolean run(float tpf) {


        if (forceStop) return true;

        if (!targetCalculated) {

            targetCalculated = true;
            findTargetVar();

        }

        ActionCommandShowHide cmd = (ActionCommandShowHide)this.cmd;
        if(targetVarDef.varType== VariableDef.VAR_TYPE_3D) {
            if(cmd.joints){
                if(cmd.showJointsSizeExpr!=null) {
                    cmd.showJointsSizeVal = (Double) new ActionLogicalExpressionVm(cmd.showJointsSizeExpr,this.scope).evaluate();
                }
            }
            app.showHideModel(targetVar, cmd);
        } else if(targetVarDef.varType== VariableDef.VAR_TYPE_2D) {
            app.showHideSprite(targetVar, cmd);
        } else if(targetVarDef.varType== VariableDef.VAR_TYPE_SPHERE) {
            app.showHideSphere(targetVar, cmd);
        }  else if(targetVarDef.varType== VariableDef.VAR_TYPE_BOX) {
            app.showHideBox(targetVar, cmd);
        }
        return true;

    }

}
