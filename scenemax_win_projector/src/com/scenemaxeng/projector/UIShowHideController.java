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
        String commandPathPrefix = shCmd.uiName != null && !shCmd.uiName.isEmpty()
                ? shCmd.uiName + "." + shCmd.layerName
                : shCmd.layerName;

        if (uiManager == null) {
            app.handleRuntimeError("UI system not initialized");
            return true;
        }

        if (shCmd.widgetPath == null || shCmd.widgetPath.isEmpty()) {
            // Ambiguous short syntax: UI.layer1.panel2.hide
            // If the first segment is not a loaded UI name, treat it as
            // layer.widget against the active UI before falling back to layer visibility.
            if (shCmd.uiName != null && !shCmd.uiName.isEmpty() && !uiManager.isLoaded(shCmd.uiName)) {
                UIWidgetNode nestedWidget = uiManager.resolveWidget(null, shCmd.uiName, shCmd.layerName);
                if (nestedWidget != null) {
                    nestedWidget.setWidgetVisible(shCmd.show);
                    return true;
                }
            }

            // Target is the layer itself
            UILayerNode layer = uiManager.resolveLayer(shCmd.uiName, shCmd.layerName);
            if (layer != null) {
                layer.setLayerVisible(shCmd.show);
            } else {
                app.handleRuntimeError("UI layer not found: " + commandPathPrefix);
            }
        } else {
            // Target is a widget within the layer, resolved by full path when provided.
            UIWidgetNode widget = uiManager.resolveWidget(shCmd.uiName, shCmd.layerName, shCmd.widgetPath);
            if (widget != null) {
                widget.setWidgetVisible(shCmd.show);
            } else {
                app.handleRuntimeError("UI widget not found: " +
                        commandPathPrefix + "." + shCmd.widgetPath);
            }
        }

        return true; // one-shot controller
    }
}
