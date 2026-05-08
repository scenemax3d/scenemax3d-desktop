package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.LoggerCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class LoggerCommandController extends SceneMaxBaseController {

    public LoggerCommandController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, LoggerCommand cmd) {
        super(app, prg, scope, cmd);
    }

    public boolean run(float tpf) {
        LoggerCommand loggerCommand = (LoggerCommand) this.cmd;
        Object value = new ActionLogicalExpressionVm(loggerCommand.message, this.scope).evaluate();
        String message = value == null ? "null" : value.toString();
        app.logRuntimeMessage(loggerCommand.level, message);
        return true;
    }
}
