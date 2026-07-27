package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.NetworkBroadcastCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class NetworkBroadcastController extends SceneMaxBaseController {

    public NetworkBroadcastController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, NetworkBroadcastCommand cmd) {
        super(app, prg, scope, cmd);
    }

    @Override
    public boolean run(float tpf) {
        NetworkBroadcastCommand broadcast = (NetworkBroadcastCommand) cmd;
        Object eventValue = broadcast.eventNameExpr == null
                ? null
                : new ActionLogicalExpressionVm(broadcast.eventNameExpr, scope).evaluate();
        String eventName = eventValue == null ? "" : String.valueOf(eventValue).trim();
        if (!eventName.isEmpty()) {
            Object messageValue = broadcast.messageExpr == null
                    ? null
                    : new ActionLogicalExpressionVm(broadcast.messageExpr, scope).evaluate();
            app.broadcastNetworkEvent(eventName, messageValue);
        }
        return true;
    }
}
