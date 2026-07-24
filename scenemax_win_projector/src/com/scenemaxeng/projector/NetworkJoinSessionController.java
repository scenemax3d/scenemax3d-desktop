package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.NetworkJoinSessionCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class NetworkJoinSessionController extends SceneMaxBaseController {

    public NetworkJoinSessionController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope,
                                        NetworkJoinSessionCommand cmd) {
        super(app, prg, scope, cmd);
    }

    @Override
    public boolean run(float tpf) {
        NetworkJoinSessionCommand join = (NetworkJoinSessionCommand) cmd;
        Object value = join.sessionExpr == null ? null : new ActionLogicalExpressionVm(join.sessionExpr, scope).evaluate();
        app.joinNetworkSession(value);
        return true;
    }
}
