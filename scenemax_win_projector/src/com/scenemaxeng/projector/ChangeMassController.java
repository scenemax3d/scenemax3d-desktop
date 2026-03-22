package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ChangeMassCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.VariableDef;

public class ChangeMassController extends SceneMaxBaseController{
    public ChangeMassController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, ChangeMassCommand cmd) {
        super(app, prg, scope, cmd);
    }

    public boolean run(float tpf) {

        if (forceStop) return true;

        if (!targetCalculated) {

            ChangeMassCommand cmd = (ChangeMassCommand) this.cmd;

            targetCalculated = true;
            findTargetVar();

            Double mass = (Double)new ActionLogicalExpression(cmd.massExpr,this.scope).evaluate();

            if (targetVarDef.varType == VariableDef.VAR_TYPE_3D) {
                this.app.applyModelMass(this.targetVar, mass);
            } else if (targetVarDef.varType == VariableDef.VAR_TYPE_SPHERE) {
                this.app.applySphereMass(this.targetVar, mass);
            } else if (targetVarDef.varType == VariableDef.VAR_TYPE_BOX) {
                this.app.applyBoxMass(this.targetVar, mass);
            }

        }

        return true;

    }
}
