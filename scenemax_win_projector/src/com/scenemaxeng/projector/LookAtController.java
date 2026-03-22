package com.scenemaxeng.projector;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.scenemaxeng.compiler.LookAtCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class LookAtController extends SceneMaxBaseController{

    public LookAtController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, LookAtCommand cmd) {
        super(app, prg, scope, cmd);
    }

    public boolean run(float tpf) {

        if (forceStop) return true;

        if(!targetCalculated) {
            targetCalculated = true;
            findTargetVar();

            LookAtCommand cmd = (LookAtCommand)this.cmd;

            RunTimeVarDef lookingObject = app.findVarRuntime(prg,scope,cmd.targetVar);

            if(cmd.posStatement!=null) {

                RunTimeVarDef lookatVar = app.findVarRuntime(prg,scope,cmd.posStatement.startEntity);
                Spatial sp = app.getEntitySpatial(lookatVar.varName,lookatVar.varDef.varType);

                if(sp==null) {
                    // error - probably user typed wrong object name
                    return true;
                }

                Vector3f lookat = sp.getWorldTranslation().clone();
                Quaternion locRot = sp.getLocalRotation();
                Util.calcPositionStatementVerbs(this.scope, cmd.posStatement,locRot,lookat);
                app.lookAt(lookingObject, lookat);

            } else if(cmd.moveToTarget!=null) {
                RunTimeVarDef lookatVar = app.findVarRuntime(prg,scope,cmd.moveToTarget);
                app.lookAt(lookingObject, lookatVar);
            } else {
                float x = ((Double)new ActionLogicalExpression(cmd.moveToTargetXExpr,scope).evaluate()).floatValue();
                float y = ((Double)new ActionLogicalExpression(cmd.moveToTargetYExpr,scope).evaluate()).floatValue();
                float z = ((Double)new ActionLogicalExpression(cmd.moveToTargetZExpr,scope).evaluate()).floatValue();
                Vector3f lookatVec = new Vector3f(x,y,z);
                app.lookAt(lookingObject,lookatVec);
            }

        }



        return true;
    }

}
