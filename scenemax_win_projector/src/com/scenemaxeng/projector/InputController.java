package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.DoBlockCommand;
import com.scenemaxeng.compiler.InputStatementCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class InputController extends SceneMaxBaseController {

    public InputController(SceneMaxApp app, ProgramDef prg, SceneMaxScope thread, InputStatementCommand cmd) {
        super(app,prg,thread,cmd);
    }

    public boolean run(float tpf) {

        if (forceStop) return true;

        InputStatementCommand cmd = (InputStatementCommand) this.cmd;
        DoBlockCommand dbc = cmd.doBlock;
        UserInputDoBlockController c = new UserInputDoBlockController(app, this.scope,dbc);
        c.goExpr = cmd.goExpr;

        if(cmd.varDef!=null) {
            findTargetVar();
            c.targetVar=this.targetVar;
            c.targetVarDef=this.targetVarDef;
        }

        c.app = app;
        c.async = cmd.isAsync;
        app.addInputHandler(cmd,c);

        return true;

    }


}
