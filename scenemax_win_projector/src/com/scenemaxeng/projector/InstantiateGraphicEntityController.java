package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.GraphicEntityCreationCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.VariableDef;

import java.util.concurrent.*;

public class InstantiateGraphicEntityController extends SceneMaxBaseController {

    private GraphicEntityCreationCommand cmd;
    private int status = 0;

    public InstantiateGraphicEntityController(SceneMaxApp app, ProgramDef prg, GraphicEntityCreationCommand cmd, SceneMaxScope thread) {
        this.app=app;
        this.prg=prg;
        this.cmd=cmd;
        this.scope=thread;
    }

    @Override
    public boolean run(float tpf) {
        if (status == 0) {
            if (this.isSharedEntityExists()) {
                return true;
            }
            if (cmd.varDef.varType == VariableDef.VAR_TYPE_3D && cmd.isAsync) {
                status = 1; // loading
                load3DModelAsync();
            } else {
                app.instantiateVariable(prg, cmd.varDef, this.scope);
                return true;
            }
        }

        return status == 2; // 2 == Java thread finished
    }

    private boolean isSharedEntityExists() {
        VariableDef vd = this.prg.vars_index.get(cmd.varDef.varName);
        if (vd !=null && vd.isShared) {
            return this.scope.entities.containsKey(cmd.varDef.varName);
        }

        return false;
    }

    private void load3DModelAsync() {

        // Create ModelInst on the JME thread (this method runs inside a controller)
        final ModelInst inst = app.instantiate3DModelAsync(prg, cmd.varDef, this.scope);
        final String resourceName = inst.modelDef.name;

        // Preload the model asset on a background thread (heavy disk I/O).
        // This populates the asset cache so the subsequent loadModel() on the
        // JME thread will be fast.
        CompletableFuture.supplyAsync(() -> {
            app.preloadModelAsset(resourceName);
            return true;
        }, app.getExecutorService())
        .thenAccept(result -> {
            // All physics and scene-graph work must happen on the JME thread
            app.enqueue(() -> {
                app.loadModel(cmd.varDef.varName, resourceName, inst);
                this.status = 2;
            });
        });
    }

}
