package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.CreateSpriteCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class CreateSpriteController extends SceneMaxBaseController {

    public CreateSpriteController(SceneMaxApp app, ProgramDef prg, CreateSpriteCommand cmd, SceneMaxScope thread) {
        this.app = app;
        this.prg = prg;
        this.scope = thread;
        this.cmd = cmd;
    }

    @Override
    public boolean run(float tpf) {

        CreateSpriteCommand cmd = (CreateSpriteCommand) this.cmd;
//        String spriteResName = null;
//        SpriteDef def;
//
        if (cmd.spriteDef.nameExpr != null) {
            cmd.varDef.resName = new ActionLogicalExpression(cmd.spriteDef.nameExpr, this.scope).evaluate().toString();
        }

        SpriteInst inst = app.createSpriteInst(this.scope, cmd);
        this.scope.entities.put(cmd.varDef.varName, inst);
        app.loadSprite(inst);

        return true;
    }

}
