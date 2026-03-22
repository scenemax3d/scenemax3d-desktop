package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.KillEntityCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.VariableDef;

public class KillEntityController extends SceneMaxBaseController{

    public KillEntityController(SceneMaxApp app, ProgramDef prg, SceneMaxScope thread, KillEntityCommand cmd) {
        super(app, prg, thread, cmd);
    }

    public boolean run(float tpf) {

        if (forceStop) return true;

        findTargetVar();

        if(targetVarDef.varType== VariableDef.VAR_TYPE_3D) {
            app.killModel(targetVar);
        } else if(targetVarDef.varType== VariableDef.VAR_TYPE_2D) {
            app.killSprite(targetVar);
        } else if(targetVarDef.varType== VariableDef.VAR_TYPE_BOX) {
            app.killBox(targetVar);
        } else if(targetVarDef.varType== VariableDef.VAR_TYPE_SPHERE) {
            app.killSphere(targetVar);
        } else if(targetVarDef.varType== VariableDef.VAR_TYPE_OBJECT) {
            //app.killSprite(targetVar);
        }
        return true;
    }

}
