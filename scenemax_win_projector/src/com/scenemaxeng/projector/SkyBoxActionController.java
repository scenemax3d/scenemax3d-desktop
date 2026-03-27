package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SkyBoxCommand;

public class SkyBoxActionController extends SceneMaxBaseController{

    private final SceneMaxApp app;
    private final SceneMaxScope scope;
    private final SkyBoxCommand cmd;
    private final ProgramDef prg;

    public SkyBoxActionController(SceneMaxApp app, ProgramDef prg, SkyBoxCommand cmd, SceneMaxScope scope) {
        this.app=app;
        this.scope=scope;
        this.cmd=cmd;
        this.prg=prg;
    }

    public boolean run(float tpf)
    {
        if(cmd.isShow) {
            if(!cmd.isShowSolarSystem) {
                String skyboxMaterial = cmd.showExpr;//new ActionLogicalExpressionVm(cmd.showExpr, scope).evaluate().toString();
                app.showSkyBox(skyboxMaterial);
            } else {
                evalSetupVars(cmd);
                app.showSolarSystemSkyBox(cmd);
            }
        } else if(cmd.isSetup) {
            evalSetupVars(cmd);
            app.setupSkyControl(cmd);
        }
        return true;
    }

    private void evalSetupVars(SkyBoxCommand cmd) {
        if(cmd.cloudinessExpr!=null) {
            cmd.cloudinessVal = (Double)new ActionLogicalExpressionVm(cmd.cloudinessExpr,scope).evaluate();
        }

        if(cmd.cloudFlatteningExpr!=null) {
            cmd.cloudFlatteningVal = (Double)new ActionLogicalExpressionVm(cmd.cloudFlatteningExpr,scope).evaluate();
        }

        if(cmd.hourOfDayExpr!=null) {
            cmd.hourOfDayVal = (Double)new ActionLogicalExpressionVm(cmd.hourOfDayExpr,scope).evaluate();

        }


    }


}
