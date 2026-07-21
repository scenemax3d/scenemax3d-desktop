package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.NetworkSendCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class NetworkSendController extends SceneMaxBaseController {

    public NetworkSendController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, NetworkSendCommand cmd) {
        super(app, prg, scope, cmd);
    }

    @Override
    public boolean run(float tpf) {
        NetworkSendCommand send = (NetworkSendCommand) cmd;
        Object value = send.eventNameExpr == null ? null : new ActionLogicalExpressionVm(send.eventNameExpr, scope).evaluate();
        String eventName = value == null ? "" : String.valueOf(value).trim();
        if (!eventName.isEmpty()) {
            app.sendNetworkEventToCollisionTarget(scope, eventName);
        }
        return true;
    }
}
