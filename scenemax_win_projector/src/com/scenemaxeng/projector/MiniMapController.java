package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.MiniMapCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class MiniMapController extends SceneMaxBaseController {

    public MiniMapController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, MiniMapCommand cmd) {
        super(app, prg, scope, cmd);
    }

    public boolean run(float tpf) {

        if (forceStop) return true;

        if(cmd.targetVar!=null) {
            findTargetVar();
        }

        MiniMapCommand cmd = (MiniMapCommand)this.cmd;
        if(cmd.sizeExpr!=null) {
            cmd.sizeVal = ((Double)new ActionLogicalExpressionVm(cmd.sizeExpr,this.scope).evaluate()).intValue();
        }

        if(cmd.heightExpr!=null) {
            cmd.heightVal = ((Double)new ActionLogicalExpressionVm(cmd.heightExpr,this.scope).evaluate()).floatValue();
        }

        app.ShowHideMiniMap(cmd,this.targetVar, this.targetVarDef);
        return true;
    }

}
