package com.scenemaxeng.projector;

import com.jme3.math.Vector3f;
import com.scenemaxeng.compiler.AttachToCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class AttachToController extends SceneMaxBaseController{

    public AttachToController(SceneMaxApp app, ProgramDef prg, SceneMaxScope thread, AttachToCommand cmd) {
        super(app, prg, thread, cmd);

    }

    public boolean run(float tpf) {

        if (forceStop) return true;

        AttachToCommand cmd = (AttachToCommand) this.cmd;

        RunTimeVarDef targetEntity = findTargetVar(cmd.targetVar);
        if(targetEntity==null) {
            app.handleRuntimeError("You are trying to attach entity '"+cmd.entityNameToAttach+"' to Entity '"+cmd.targetVar+"' which doesn't exist" );
            return true;
        }

        Double xPos=0.0, yPos=0.0, zPos=0.0, xRot=0.0, yRot=0.0, zRot=0.0;;
        Vector3f offsetPos=null;
        Vector3f offsetRot=null;

        if(cmd.xExpr!=null) {
            xPos = (Double) new ActionLogicalExpression(cmd.xExpr, this.scope).evaluate();
            yPos = (Double) new ActionLogicalExpression(cmd.yExpr, this.scope).evaluate();
            zPos = (Double) new ActionLogicalExpression(cmd.zExpr, this.scope).evaluate();
            offsetPos = new Vector3f(xPos.floatValue(),yPos.floatValue(),zPos.floatValue());
        }

        if(cmd.rxExpr!=null) {
            xRot = (Double) new ActionLogicalExpression(cmd.rxExpr, this.scope).evaluate();
            yRot = (Double) new ActionLogicalExpression(cmd.ryExpr, this.scope).evaluate();
            zRot = (Double) new ActionLogicalExpression(cmd.rzExpr, this.scope).evaluate();
            offsetRot = new Vector3f(xRot.floatValue(),yRot.floatValue(),zRot.floatValue());
        }

        if(cmd.entityNameToAttach.equalsIgnoreCase("camera")) {
            app.setFpsCameraOn(targetEntity.varName, targetEntity.varDef, offsetPos,offsetRot);
        } else {
            RunTimeVarDef entityToAttach = findTargetVar(cmd.entityNameToAttach);
            //app.attachEntity(targetEntity, entityToAttach, jointName, xPos, yPos, zPos);
            app.attachEntity2(entityToAttach, cmd.sourceJointName, targetEntity, cmd.jointName, xPos, yPos, zPos);
        }

        return true;
    }


}
