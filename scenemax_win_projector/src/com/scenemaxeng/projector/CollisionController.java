package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.CollisionStatementCommand;
import com.scenemaxeng.compiler.DoBlockCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.VariableDef;

public class CollisionController extends CompositeController {

    private String targetVar1;
    private String targetVar2;

    public CollisionController(SceneMaxApp app, ProgramDef prg, SceneMaxScope thread, CollisionStatementCommand cmd) {
        super(app,prg,thread,cmd);
    }

    public boolean run(float tpf)
    {
        if(forceStop) return true;

        CollisionStatementCommand collCmd = (CollisionStatementCommand)this.cmd;
        //RunTimeVarDef rt1 = findTargetVar(collCmd.varDef1.varName);
        RunTimeVarDef rt2 = findTargetVar(collCmd.destEntity.varName);
        //this.targetVar1 = rt1.varName;
        this.targetVar2 = rt2.varName;

        DoBlockCommand cmd = collCmd.doBlock;
        DoBlockController collisionController = new DoBlockController(app, this.scope, cmd);

        collisionController.app = app;
        collisionController.async = cmd.isAsync;

        int counter = 0;
        for (VariableDef vd : collCmd.sourceEntities) {
            RunTimeVarDef rt1 = findTargetVar(vd.varName);

            app.addCollisionHandler(
                    rt1.varName,
                    rt2.varName,
                    collisionController,
                    this.scope.getEntityInst(vd.varName),
                    this.scope.getEntityInst(collCmd.destEntity.varName),
                    collCmd.sourceJoints.get(counter),
                    collCmd.destJoint,
                    collCmd.goExpr);
        }

//        app.addCollisionHandler(this.targetVar1,this.targetVar2,
//                collisionController,
//                thread.getEntityInst(collCmd.varDef1.varName),
//                thread.getEntityInst(collCmd.varDef2.varName),
//                collCmd.part1,
//                collCmd.part2,
//                collCmd.goExpr);

        return true;

    }




}
