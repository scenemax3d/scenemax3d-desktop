package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ForEachCommand;
import com.scenemaxeng.compiler.FunctionInvocationCommand;
import com.scenemaxeng.compiler.ProgramDef;

import java.util.List;

public class ForEachCommandController extends CompositeController {

    public ForEachCommandController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, ForEachCommand cmd) {
        super(app, prg, scope, cmd);
    }

    public boolean run(float tpf) {

        if (forceStop) return true;
        if(!targetCalculated) {
            targetCalculated=true;

            ForEachCommand cmd = (ForEachCommand)this.cmd;
            List<EntityInstBase> entities;
            if (cmd.targetCollectionExpr != null) {
                entities = (List<EntityInstBase>)new ActionLogicalExpressionVm(cmd.targetCollectionExpr, this.scope).evaluate();
            } else {
                entities = app.getAllEntities(cmd.entityType, cmd.name, cmd.nameComparator);
            }
            for(EntityInstBase e:entities) {
                FunctionInvocationCommand fic = new FunctionInvocationCommand();
                fic.funcName = cmd.funcDef.name;
                fic.funcParam = e;
                SceneMaxBaseController ctl = app.runFunctionInvocationCommand(prg, scope, fic);
                this.add(ctl);
            }

        }

        return super.run(tpf);
    }

}
