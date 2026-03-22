package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.TerrainCommand;

public class TerrainController extends SceneMaxBaseController {

    private final TerrainCommand cmd;
    private SceneMaxApp app;
    private ProgramDef prg;
    private SceneMaxScope scope;

    public TerrainController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, TerrainCommand cmd) {
        this.app=app;
        this.prg=prg;
        this.scope=scope;
        this.cmd=cmd;

    }

    public boolean run(float tpf) {
        if (forceStop) return true;
        ActionLogicalExpression exp = new ActionLogicalExpression(cmd.terrainNameExprCtx,this.scope);
        String asset = exp.evaluate().toString();
        app.loadTerrain(asset);

        return true;
    }


}
