package com.scenemaxeng.projector;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.scenemaxeng.compiler.ActionCommandPos;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.VariableDef;

public class EntityPosController extends SceneMaxBaseController {

    public EntityPosController(SceneMaxApp app, ProgramDef prg, SceneMaxScope thread, ActionCommandPos cmd) {
        super(app,prg,thread,cmd);
    }

    public boolean run(float tpf) {
        if (forceStop) return true;

        Double valX=null;
        Double valY=null;
        Double valZ=null;
        RunTimeVarDef entityForPos=null;
        Vector3f calculatedPosition=null;

        ActionCommandPos cmd = (ActionCommandPos) this.cmd;
        if(cmd.posStatement!=null) {
            RunTimeVarDef lookatVar = app.findVarRuntime(prg,this.scope,cmd.posStatement.startEntity);
            Spatial sp = app.getEntitySpatial(lookatVar.varName,lookatVar.varDef.varType);

            if(sp==null) {
                // error - probably user typed wrong object name
                return true;
            }

            calculatedPosition = sp.getWorldTranslation().clone();
            Quaternion locRot = sp.getLocalRotation();
            Util.calcPositionStatementVerbs(this.scope, cmd.posStatement,locRot,calculatedPosition);

        } else if(cmd.x!=null) {
            valX = (Double) new ActionLogicalExpressionVm(cmd.x, this.scope).evaluate();
            valY = (Double) new ActionLogicalExpressionVm(cmd.y, this.scope).evaluate();
            valZ = (Double) new ActionLogicalExpressionVm(cmd.z, this.scope).evaluate();
        } else if(cmd.entityPos!=null) {

            entityForPos = app.findVarRuntime(prg,this.scope,cmd.entityPos.entityName);
            if(entityForPos!=null) {
                Spatial sp = null;
                if(cmd.entityPos.entityJointName!=null) {
                    AppModel am = app.getAppModel(entityForPos.varName);
                    sp = am.getJointAttachementNode(cmd.entityPos.entityJointName);
                } else {
                    sp = app.getEntitySpatial(entityForPos.varName, entityForPos.varDef.varType);
                }

                if (sp != null) {
                    calculatedPosition = sp.getWorldTranslation();// sp.getLocalTranslation();
                }
            }
        }

        if (!this.targetCalculated) {
            this.findTargetVar();
            this.targetCalculated = true;
        }

        this.enableEntity(targetVar);// enable this entity
        if(StopModelController.forceStopCommands.get(targetVar)!=null) {
            return true;
        }

        if(targetVarDef.varType== ProgramDef.VAR_TYPE_3D){
            app.posModel(targetVar,valX,valY,valZ, entityForPos, calculatedPosition);
        } else if(targetVarDef.varType== ProgramDef.VAR_TYPE_2D){
            app.posSprite(targetVar,valX,valY,valZ,entityForPos, calculatedPosition);
        } else if(targetVarDef.varType== ProgramDef.VAR_TYPE_CAMERA){
            app.posCamera(valX,valY,valZ,entityForPos, calculatedPosition);
        } else if(targetVarDef.varType== ProgramDef.VAR_TYPE_SPHERE){
            app.posSphere(targetVar,valX,valY,valZ,entityForPos, calculatedPosition);
        } else if(targetVarDef.varType== VariableDef.VAR_TYPE_BOX){
            app.posBox(targetVar,valX,valY,valZ,entityForPos, calculatedPosition);
        }

        return true;
    }


}
