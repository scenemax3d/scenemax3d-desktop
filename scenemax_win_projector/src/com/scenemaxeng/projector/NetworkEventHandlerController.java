package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.NetworkEventHandlerCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class NetworkEventHandlerController extends SceneMaxBaseController {

    public NetworkEventHandlerController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope,
                                         NetworkEventHandlerCommand cmd) {
        super(app, prg, scope, cmd);
    }

    @Override
    public boolean run(float tpf) {
        NetworkEventHandlerCommand handler = (NetworkEventHandlerCommand) cmd;
        Object value = handler.eventNameExpr == null
                ? null
                : new ActionLogicalExpressionVm(handler.eventNameExpr, scope).evaluate();
        String eventName = value == null ? "" : String.valueOf(value).trim();
        if (!eventName.isEmpty() && handler.doBlock != null) {
            app.registerNetworkEventHandler(eventName, scope, handler.doBlock);
        }
        return true;
    }
}
