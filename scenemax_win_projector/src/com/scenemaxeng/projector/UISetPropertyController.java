package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.UISetPropertyCommand;
import com.scenemax.designer.ui.widget.*;

/**
 * Runtime controller for:
 *   UI.hud.layer1.text1.text = "Hello world"
 *   UI.hud.layer1.text1.color = "#FF0000FF"
 *   UI.hud.layer1.image1.image = "textures/icon.png"
 *   UI.hud.layer1.button1.text = "Click me"
 *   UI.hud.layer1.panel1.visible = true
 *
 * Sets a property on a specific widget within a UI layer.
 */
public class UISetPropertyController extends SceneMaxBaseController {

    public UISetPropertyController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, UISetPropertyCommand cmd) {
        super(app, prg, scope, cmd);
    }

    @Override
    public boolean run(float tpf) {
        if (forceStop) return true;

        UISetPropertyCommand propCmd = (UISetPropertyCommand) this.cmd;
        UIManager uiManager = app.getUIManager();

        if (uiManager == null) {
            app.handleRuntimeError("UI system not initialized");
            return true;
        }

        // Resolve the target widget
        String widgetName = propCmd.widgetPath;
        if (widgetName.contains(".")) {
            String[] parts = widgetName.split("\\.");
            widgetName = parts[parts.length - 1];
        }

        UIWidgetNode widget = uiManager.resolveWidget(propCmd.uiName, propCmd.layerName, widgetName);
        if (widget == null) {
            app.handleRuntimeError("UI widget not found: " +
                    propCmd.uiName + "." + propCmd.layerName + "." + propCmd.widgetPath);
            return true;
        }

        // Evaluate the value expression
        String value;
        if (propCmd.valueExpr != null) {
            // Runtime expression evaluation
            value = new ActionLogicalExpressionVm(propCmd.valueExpr, this.scope).evaluate().toString();
        } else if (propCmd.stringValue != null) {
            value = propCmd.stringValue;
        } else {
            app.handleRuntimeError("UI set property: no value for " + propCmd.propertyName);
            return true;
        }

        // Apply the property based on widget type and property name
        applyProperty(widget, propCmd.propertyName, value);

        return true; // one-shot controller
    }

    /**
     * Applies a named property to the appropriate widget type.
     */
    private void applyProperty(UIWidgetNode widget, String propertyName, String value) {
        String prop = propertyName.toLowerCase();

        if (widget instanceof UITextViewNode) {
            UITextViewNode textView = (UITextViewNode) widget;
            switch (prop) {
                case "text":
                    textView.setText(value);
                    break;
                case "color":
                case "textcolor":
                    textView.setTextColor(value);
                    break;
                case "fontsize":
                    try {
                        textView.setFontSize(Float.parseFloat(value));
                    } catch (NumberFormatException e) {
                        app.handleRuntimeError("Invalid font size: " + value);
                    }
                    break;
                default:
                    applyCommonProperty(widget, prop, value);
            }
        } else if (widget instanceof UIButtonNode) {
            UIButtonNode button = (UIButtonNode) widget;
            switch (prop) {
                case "text":
                    button.setButtonText(value);
                    break;
                case "color":
                case "buttoncolor":
                    button.setBackgroundColor(value);
                    break;
                case "textcolor":
                case "buttontextcolor":
                    button.setButtonTextColor(value);
                    break;
                default:
                    applyCommonProperty(widget, prop, value);
            }
        } else if (widget instanceof UIImageNode) {
            UIImageNode image = (UIImageNode) widget;
            switch (prop) {
                case "image":
                case "imagepath":
                    image.setImage(value);
                    break;
                default:
                    applyCommonProperty(widget, prop, value);
            }
        } else if (widget instanceof UIPanelNode) {
            UIPanelNode panel = (UIPanelNode) widget;
            switch (prop) {
                case "color":
                case "backgroundcolor":
                    panel.setBackgroundColor(value);
                    break;
                case "image":
                case "backgroundimage":
                    panel.setBackgroundImage(value);
                    break;
                default:
                    applyCommonProperty(widget, prop, value);
            }
        } else {
            applyCommonProperty(widget, prop, value);
        }
    }

    /**
     * Properties common to all widget types.
     */
    private void applyCommonProperty(UIWidgetNode widget, String prop, String value) {
        switch (prop) {
            case "visible":
                widget.setWidgetVisible(
                        value.equalsIgnoreCase("true") || value.equals("1"));
                break;
            default:
                app.handleRuntimeError("Unknown UI property '" + prop +
                        "' on widget " + widget.getName());
        }
    }
}
