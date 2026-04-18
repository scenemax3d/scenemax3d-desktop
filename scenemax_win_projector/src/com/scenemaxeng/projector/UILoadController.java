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
 * UI.load now runs through the regular controller/action pipeline, just like
 * the rest of the UI commands.
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
                if (!uiFile.exists()) {
                    // The script may live in a sub-folder while the .smui doc
                    // sits higher up in the project tree. Walk up parent dirs
                    // to locate it before falling back to packaged resources.
                    File found = findUiFileInParents(uiFile.getParentFile(), loadCmd.uiName);
                    if (found != null) {
                        uiFile = found;
                    }
                }
                if (uiFile.exists()) {
                    app.loadUIDocument(uiFile);
                } else if (app.loadPackagedUiDocument(loadCmd.uiName)) {
                    // loaded from jar resources
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

    private File findUiFileInParents(File startDir, String uiName) {
        if (startDir == null || uiName == null || uiName.isEmpty()) {
            return null;
        }
        File dir = startDir.getParentFile();
        int levels = 0;
        while (dir != null && levels < 10) {
            File candidate = new File(dir, uiName + ".smui");
            if (candidate.exists()) {
                return candidate;
            }
            dir = dir.getParentFile();
            levels++;
        }
        return null;
    }
}
