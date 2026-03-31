package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.UILoadCommand;

import java.io.File;

/**
 * Runtime controller for: UI.load "ui_name"
 *
 * Loads a .smui document via UIManager, creating the JME node hierarchy
 * and attaching layers to guiNode (screen-space) or rootNode (world-space).
 *
 * Note: In the standard pipeline, UI.load is handled as a resource-loading step
 * via loadResource() in SceneMaxApp, not through runAction(). This controller
 * exists as a utility for programmatic use or future extensions.
 */
public class UILoadController extends SceneMaxBaseController {

    private UILoadCommand loadCmd;

    public UILoadController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, UILoadCommand cmd) {
        super();
        this.app = app;
        this.prg = prg;
        this.scope = scope;
        this.loadCmd = cmd;
    }

    @Override
    public boolean run(float tpf) {
        if (forceStop) return true;

        try {
            if (loadCmd.filePath != null && !loadCmd.filePath.isEmpty()) {
                File uiFile = new File(loadCmd.filePath);
                if (uiFile.exists()) {
                    app.loadUIDocument(uiFile);
                } else {
                    app.handleRuntimeError("UI file not found: " + loadCmd.filePath);
                }
            } else {
                app.handleRuntimeError("UI.load: no file path resolved for '" + loadCmd.uiName + "'");
            }
        } catch (Exception e) {
            app.handleRuntimeError("UI.load failed: " + e.getMessage());
        }

        return true; // one-shot controller
    }
}
