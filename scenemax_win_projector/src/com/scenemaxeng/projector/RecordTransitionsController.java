package com.scenemaxeng.projector;

import com.jme3.scene.Spatial;
import com.scenemaxeng.compiler.ActionCommandRecord;
import com.scenemaxeng.compiler.ProgramDef;

public class RecordTransitionsController extends SceneMaxBaseController{

    private Spatial spatial;
    private Double interval;
    private float timePassed=0;
    private StringBuilder records = new StringBuilder();

    public RecordTransitionsController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, ActionCommandRecord cmd) {
        super(app, prg, scope, cmd);
    }


    public boolean run(float tpf) {

        if (forceStop) return true;

        if (!targetCalculated) {

            findTargetVar();

            this.spatial = app.getEntitySpatial(this.targetVar,this.targetVarDef.varType);
            if(this.spatial==null) {
                return true;
            }

            ActionCommandRecord cmd = (ActionCommandRecord)this.cmd;
            this.interval = (Double)new ActionLogicalExpression(cmd.everyTimeExpr,this.scope).evaluate();

            this.spatial.setUserData("__stop_record",false);
            targetCalculated=true;
        }

        if(this.spatial.getUserData("__stop_record")) {
            String records = this.records.toString();
            if(records.length()>0) {
                records=records.substring(1);
            }
            this.spatial.setUserData("__record_transitions",records);
            return true;
        }

        timePassed+=tpf;
        if(timePassed>=interval) {
            addTransitionRecord();
            timePassed=0;
        }

        return false;

    }

    private void addTransitionRecord() {

        records.append(app.getSpatialTransitionRecord(this.spatial));

    }

}
