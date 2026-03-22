package com.scenemaxeng.projector;

import com.jme3.scene.Spatial;
import com.scenemaxeng.compiler.ActionCommandRecord;
import com.scenemaxeng.compiler.ProgramDef;

public class StopRecordController extends SceneMaxBaseController{

    public StopRecordController(SceneMaxApp app, ProgramDef prg, SceneMaxScope thread, ActionCommandRecord cmd) {
        super(app, prg, thread, cmd);
    }


    public boolean run(float tpf) {

        if (forceStop) return true;

        if (!targetCalculated) {

            findTargetVar();

            Spatial sp = app.getEntitySpatial(this.targetVar, this.targetVarDef.varType);
            if (sp == null) {
                return true;
            }

            sp.setUserData("__stop_record",true);

        }

        return true;

    }
}
