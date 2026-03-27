package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.ChannelDrawCommand;

public class UIAttachController extends SceneMaxBaseController {

    public UIAttachController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, ChannelDrawCommand cmd) {
        super(app, prg, scope, cmd);
    }

    public boolean run(float tpf) {

        if (forceStop) return true;

        if (!targetCalculated) {
            targetCalculated = true;
            //findTargetVar();

            ChannelDrawCommand cmd = (ChannelDrawCommand)this.cmd;
            if(cmd.posXExpr!=null) {
                cmd.posXVal = ((Double)new ActionLogicalExpressionVm(cmd.posXExpr,this.scope).evaluate()).floatValue();
                cmd.posYVal = ((Double)new ActionLogicalExpressionVm(cmd.posYExpr,this.scope).evaluate()).floatValue();

            }

            if(cmd.widthExpr!=null) {
                cmd.widthVal = ((Double)new ActionLogicalExpressionVm(cmd.widthExpr,this.scope).evaluate()).intValue();
                cmd.heightVal = ((Double)new ActionLogicalExpressionVm(cmd.heightExpr,this.scope).evaluate()).intValue();
            }

            if(cmd.frameNumExpr!=null) {
                cmd.frameNumVal = ((Double)new ActionLogicalExpressionVm(cmd.frameNumExpr,this.scope).evaluate()).intValue();
            }

            app.channelDraw(cmd);

        }


        return true;
    }

}
