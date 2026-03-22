package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SetMaterialCommand;
import com.scenemaxeng.compiler.VariableDef;

public class SetMaterialController extends SceneMaxBaseController {

    public SetMaterialController(SceneMaxApp app, ProgramDef prg, SceneMaxScope thread, SetMaterialCommand cmd) {
        super(app, prg, thread, cmd);
    }

    public boolean run(float tpf) {

        if (forceStop) return true;

        findTargetVar();

        SetMaterialCommand cmd = (SetMaterialCommand) this.cmd;
        String material = new ActionLogicalExpression(cmd.materialNameExpr,this.scope).evaluate().toString();

        if(this.targetVarDef.varType== VariableDef.VAR_TYPE_BOX) {
            this.app.setBoxMaterial(this.targetVar, material);
        } else if(this.targetVarDef.varType== VariableDef.VAR_TYPE_SPHERE) {
            this.app.setSphereMaterial(this.targetVar, material);
        }


        return true;
    }

}
