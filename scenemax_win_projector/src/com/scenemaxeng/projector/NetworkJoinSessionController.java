package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.NetworkJoinSessionCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class NetworkJoinSessionController extends SceneMaxBaseController {

    private Object sessionSelector;
    private boolean joinStarted;
    private boolean waitForJoin;

    public NetworkJoinSessionController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope,
                                        NetworkJoinSessionCommand cmd) {
        super(app, prg, scope, cmd);
    }

    @Override
    public boolean run(float tpf) {
        if (!joinStarted) {
            NetworkJoinSessionCommand join = (NetworkJoinSessionCommand) cmd;
            sessionSelector = join.sessionExpr == null ? null : new ActionLogicalExpressionVm(join.sessionExpr, scope).evaluate();
            waitForJoin = app.joinNetworkSession(sessionSelector);
            joinStarted = true;
        }
        return !waitForJoin || app.isNetworkSessionJoinComplete(sessionSelector);
    }
}
