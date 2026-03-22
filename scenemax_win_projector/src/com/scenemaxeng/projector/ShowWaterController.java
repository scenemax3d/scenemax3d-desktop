package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.WaterShowCommand;

public class ShowWaterController extends SceneMaxBaseController{

    private final SceneMaxApp app;
    private final SceneMaxScope scope;
    private final WaterShowCommand cmd;
    private final ProgramDef prg;

    public ShowWaterController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, WaterShowCommand cmd) {
        this.app=app;
        this.scope=scope;
        this.cmd=cmd;
        this.prg=prg;

    }

    public boolean run(float tpf) {
        if (forceStop) return true;

        if(cmd.pos!=null) {
            if(cmd.pos.pos_axes()!=null) {
                cmd.posX = ((Double) new ActionLogicalExpression(cmd.pos.pos_axes().print_pos_x().logical_expression(), scope).evaluate()).floatValue();
                cmd.posY = ((Double) new ActionLogicalExpression(cmd.pos.pos_axes().print_pos_y().logical_expression(), scope).evaluate()).floatValue();
                cmd.posZ = ((Double) new ActionLogicalExpression(cmd.pos.pos_axes().print_pos_z().logical_expression(), scope).evaluate()).floatValue();
            } else {
                cmd.entityPos = cmd.pos.pos_entity().getText();
            }
        }

        if(cmd.depth!=null) {
            cmd.depthVal = ((Double)new ActionLogicalExpression(cmd.depth.logical_expression(),scope).evaluate()).floatValue();
        }

        if(cmd.speed!=null) {
            cmd.speedVal = ((Double)new ActionLogicalExpression(cmd.speed.logical_expression(),scope).evaluate()).floatValue();
        }

        if(cmd.strength!=null) {
            cmd.strengthVal = ((Double)new ActionLogicalExpression(cmd.strength.logical_expression(),scope).evaluate()).floatValue();
        }

        app.showWater(cmd);
        return true;
    }

}
