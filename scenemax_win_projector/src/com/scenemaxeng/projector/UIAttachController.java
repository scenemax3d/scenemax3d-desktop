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
                cmd.posXVal = ((Double)new ActionLogicalExpression(cmd.posXExpr,this.scope).evaluate()).floatValue();
                cmd.posYVal = ((Double)new ActionLogicalExpression(cmd.posYExpr,this.scope).evaluate()).floatValue();

            }

            if(cmd.widthExpr!=null) {
                cmd.widthVal = ((Double)new ActionLogicalExpression(cmd.widthExpr,this.scope).evaluate()).intValue();
                cmd.heightVal = ((Double)new ActionLogicalExpression(cmd.heightExpr,this.scope).evaluate()).intValue();
            }

            if(cmd.frameNumExpr!=null) {
                cmd.frameNumVal = ((Double)new ActionLogicalExpression(cmd.frameNumExpr,this.scope).evaluate()).intValue();
            }

            app.channelDraw(cmd);

        }


        return true;
    }

}
