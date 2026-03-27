package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ForCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class ForController extends CompositeController {

    private ForCommand cmd;
    private DoBlockController doBlock;

    // Cached to avoid per-frame allocation
    private ActionLogicalExpressionVm stopCondExpr;
    private VariableAssignmentController incrementCtl;

    public ForController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, ForCommand cmd) {
        super(app, prg, scope, cmd);
        this.cmd = cmd;
    }

    public boolean run(float tpf) {

        if (forceStop) return true;
        if (!targetCalculated) {
            targetCalculated = true;

            VariableAssignmentController ctl = new VariableAssignmentController(this.app,scope,prg,this.cmd.declareIndexCommand);
            ctl.run(tpf);

            this.doBlock = new DoBlockController(this.app, this.scope, this.cmd.doBlock);
            this.doBlock.async = this.cmd.isAsync;
            this.add(this.doBlock);

            // Cache these for reuse every frame
            stopCondExpr = new ActionLogicalExpressionVm(this.cmd.stopConditionExpr, scope);
            incrementCtl = new VariableAssignmentController(this.app, scope, prg, this.cmd.incrementIndexCommand);
        }

        Boolean continueLoop = (Boolean) stopCondExpr.evaluate();
        if (!continueLoop) {
            return true; // finish loop
        }

        boolean bodyFinished = super.run(tpf);
        if (bodyFinished) {
            // increment loop index - reuse cached controller
            incrementCtl.run(tpf);

            this.doBlock.reset();
            this.runningControllerIndex = 0;
        }

        return false;
    }

}
