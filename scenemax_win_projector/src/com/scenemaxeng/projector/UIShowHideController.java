package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.UIShowHideCommand;
import com.scenemaxeng.common.ui.widget.UILayerNode;
import com.scenemaxeng.common.ui.widget.UIManager;
import com.scenemaxeng.common.ui.widget.UIWidgetNode;

/**
 * Runtime controller for:
 *   UI.hud.layer1.show
 *   UI.hud.layer1.hide
 *   UI.hud.layer1.panel1.show
 *   UI.hud.layer1.panel1.button1.hide
 *
 * Shows or hides a layer or a specific widget within a layer.
 */
public class UIShowHideController extends SceneMaxBaseController {

    public UIShowHideController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, UIShowHideCommand cmd) {
        super(app, prg, scope, cmd);
    }

    @Override
    public boolean run(float tpf) {
        if (forceStop) return true;

        UIShowHideCommand shCmd = (UIShowHideCommand) this.cmd;
        UIManager uiManager = app.getUIManager();

        if (uiManager == null) {
            app.handleRuntimeError("UI system not initialized");
            return true;
        }

        if (shCmd.widgetPath == null || shCmd.widgetPath.isEmpty()) {
            // Target is the layer itself
            UILayerNode layer = uiManager.resolveLayer(shCmd.uiName, shCmd.layerName);
            if (layer != null) {
                layer.setLayerVisible(shCmd.show);
            } else {
                app.handleRuntimeError("UI layer not found: " + shCmd.uiName + "." + shCmd.layerName);
            }
        } else {
            // Target is a widget within the layer
            // The widgetPath may be "panel1" or "panel1.button1" etc.
            // Extract the leaf widget name (all names are unique within a layer)
            String widgetName = shCmd.widgetPath;
            if (widgetName.contains(".")) {
                String[] parts = widgetName.split("\\.");
                widgetName = parts[parts.length - 1];
            }

            UIWidgetNode widget = uiManager.resolveWidget(shCmd.uiName, shCmd.layerName, widgetName);
            if (widget != null) {
                widget.setWidgetVisible(shCmd.show);
            } else {
                app.handleRuntimeError("UI widget not found: " +
                        shCmd.uiName + "." + shCmd.layerName + "." + shCmd.widgetPath);
            }
        }

        return true; // one-shot controller
    }
}
