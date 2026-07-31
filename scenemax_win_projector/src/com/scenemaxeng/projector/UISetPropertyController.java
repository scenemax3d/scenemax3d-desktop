package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.UISetPropertyCommand;
import com.scenemaxeng.common.ui.widget.*;

import java.util.ArrayList;
import java.util.List;

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

        RuntimeUITargetValue.Resolved resolvedTarget = resolveTarget(propCmd, uiManager);
        UIWidgetNode widget = resolvedTarget == null ? null : resolvedTarget.widget;
        if (widget == null) {
            String targetPath = resolvedTarget == null ? describeTarget(propCmd) : resolvedTarget.displayPath();
            app.handleRuntimeError("UI widget not found: " + targetPath);
            return true;
        }

        // Evaluate the value expression
        String value;
        if (propCmd.valueExpr != null) {
            // Runtime expression evaluation
            value = ActionLogicalExpressionVm.formatValueForStringContext(
                    new ActionLogicalExpressionVm(propCmd.valueExpr, this.scope).evaluate());
        } else if (propCmd.stringValue != null) {
            value = propCmd.stringValue;
        } else {
            app.handleRuntimeError("UI set property: no value for " + propCmd.propertyName);
            return true;
        }

        String resolvedPropertyName = resolvePropertyName(widget, propCmd);

        // Apply the property based on widget type and property name
        applyProperty(widget, propCmd, value, resolvedTarget);
        syncMultiplayerWidgetProperty(propCmd, resolvedTarget, widget, resolvedPropertyName, value);

        return true; // one-shot controller
    }

    private RuntimeUITargetValue.Resolved resolveTarget(UISetPropertyCommand propCmd, UIManager uiManager) {
        if (propCmd.targetVarName != null && !propCmd.targetVarName.isEmpty()) {
            RuntimeUITargetValue target = RuntimeUITargetValue.fromVariable(
                    scope, propCmd.targetVarName, app, propCmd.varLineNum);
            return target == null ? null : target.resolve(uiManager);
        }

        UIWidgetNode widget = uiManager.resolveWidget(propCmd.uiName, propCmd.layerName, propCmd.widgetPath);
        return widget == null ? null
                : new RuntimeUITargetValue.Resolved(
                propCmd.uiName, propCmd.layerName, propCmd.widgetPath, null, widget);
    }

    private String describeTarget(UISetPropertyCommand propCmd) {
        if (propCmd.targetVarName != null && !propCmd.targetVarName.isEmpty()) {
            return propCmd.targetVarName;
        }
        String commandPathPrefix = propCmd.uiName != null && !propCmd.uiName.isEmpty()
                ? propCmd.uiName + "." + propCmd.layerName
                : propCmd.layerName;
        return commandPathPrefix + "." + propCmd.widgetPath;
    }

    /**
     * Applies a named property to the appropriate widget type.
     */
    private void applyProperty(UIWidgetNode widget, UISetPropertyCommand propCmd, String value,
                               RuntimeUITargetValue.Resolved resolvedTarget) {
        String prop = resolvePropertyName(widget, propCmd);
        if (prop == null || prop.isEmpty()) {
            app.handleRuntimeError("Unknown UI property target on widget " + widget.getName());
            return;
        }

        if (widget instanceof UIEditTextNode) {
            UIEditTextNode editText = (UIEditTextNode) widget;
            switch (prop) {
                case "text":
                case "value":
                    editText.setText(value);
                    break;
                case "placeholder":
                case "edittextplaceholder":
                    editText.setPlaceholder(value);
                    break;
                case "multiline":
                case "edittextmultiline":
                    editText.setMultiline(value.equalsIgnoreCase("true") || value.equals("1"));
                    break;
                case "shader":
                    app.setUIWidgetShader(editText, value);
                    break;
                case "color":
                case "textcolor":
                    editText.setTextColor(value);
                    break;
                case "backgroundcolor":
                case "edittextbackgroundcolor":
                    editText.setBackgroundColor(value);
                    break;
                case "focusedcolor":
                case "edittextfocusedcolor":
                    editText.setFocusedColor(value);
                    break;
                case "cursorcolor":
                case "edittextcursorcolor":
                    editText.setCursorColor(value);
                    break;
                case "selectioncolor":
                case "edittextselectioncolor":
                    editText.setSelectionColor(value);
                    break;
                case "fontsize":
                    try {
                        editText.setFontSize(Float.parseFloat(value));
                    } catch (NumberFormatException e) {
                        app.handleRuntimeError("Invalid font size: " + value);
                    }
                    break;
                case "font":
                case "fontname":
                    editText.setFontName(value);
                    break;
                default:
                    applyCommonProperty(widget, prop, value);
            }
        } else if (widget instanceof UITextViewNode) {
            UITextViewNode textView = (UITextViewNode) widget;
            switch (prop) {
                case "text":
                    textView.setText(value);
                    syncMultiplayerTextView(propCmd, resolvedTarget, textView, value);
                    break;
                case "shader":
                    app.setUIWidgetShader(textView, value);
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
                case "font":
                case "fontname":
                    textView.setFontName(value);
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
                case "shader":
                    app.setUIWidgetShader(button, value);
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
                case "shader":
                    app.setUIWidgetShader(image, value);
                    break;
                case "sprite":
                case "spritename":
                    image.setSprite(value);
                    break;
                case "frame":
                case "spriteframe":
                    try {
                        image.setSpriteFrame((int) Float.parseFloat(value));
                    } catch (NumberFormatException e) {
                        app.handleRuntimeError("Invalid sprite frame: " + value);
                    }
                    break;
                default:
                    applyCommonProperty(widget, prop, value);
            }
        } else if (widget instanceof UIListViewNode) {
            UIListViewNode listView = (UIListViewNode) widget;
            switch (prop) {
                case "columns":
                case "columncount":
                case "listcolumncount":
                    try {
                        listView.setColumnCount((int) Float.parseFloat(value));
                    } catch (NumberFormatException e) {
                        app.handleRuntimeError("Invalid list column count: " + value);
                    }
                    break;
                case "headers":
                case "listheaders":
                    listView.setHeaders(parseListCells(value));
                    break;
                case "rows":
                case "listrows":
                    listView.setRows(parseListRows(value));
                    break;
                case "widths":
                case "columnwidths":
                case "listcolumnwidths":
                    listView.setColumnWidths(parseListWidths(value));
                    break;
                case "addrow":
                case "appendrow":
                    listView.addRow(parseListCells(value));
                    break;
                case "clear":
                case "clearrows":
                    listView.clearRows();
                    break;
                case "selected":
                case "selectedrow":
                case "selectedrowindex":
                    try {
                        listView.setSelectedRowIndex((int) Float.parseFloat(value));
                    } catch (NumberFormatException e) {
                        app.handleRuntimeError("Invalid selected row index: " + value);
                    }
                    break;
                case "style":
                case "listviewstyle":
                    listView.setListViewStyle(value);
                    break;
                case "transparency":
                case "listviewtransparency":
                    try {
                        listView.setListViewTransparency(Float.parseFloat(value));
                    } catch (NumberFormatException e) {
                        app.handleRuntimeError("Invalid list view transparency: " + value);
                    }
                    break;
                case "headerfont":
                case "listheaderfont":
                case "listheaderfontname":
                    listView.setHeaderFontName(defaultToNull(value));
                    break;
                case "rowfont":
                case "listrowfont":
                case "listrowfontname":
                    listView.setRowFontName(defaultToNull(value));
                    break;
                case "headerfontsize":
                case "listheaderfontsize":
                    try {
                        listView.setHeaderFontSize(Float.parseFloat(value));
                    } catch (NumberFormatException e) {
                        app.handleRuntimeError("Invalid list header font size: " + value);
                    }
                    break;
                case "rowfontsize":
                case "listrowfontsize":
                    try {
                        listView.setRowFontSize(Float.parseFloat(value));
                    } catch (NumberFormatException e) {
                        app.handleRuntimeError("Invalid list row font size: " + value);
                    }
                    break;
                case "shader":
                    app.setUIWidgetShader(listView, value);
                    break;
                default:
                    applyCommonProperty(widget, prop, value);
            }
        } else if (widget instanceof UIPanelNode) {
            UIPanelNode panel = (UIPanelNode) widget;
            switch (prop) {
                case "shader":
                    app.setUIWidgetShader(panel, value);
                    break;
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
            case "shader":
                app.setUIWidgetShader(widget, value);
                break;
            case "visible":
                widget.setWidgetVisible(
                        value.equalsIgnoreCase("true") || value.equals("1"));
                break;
            default:
                app.handleRuntimeError("Unknown UI property '" + prop +
                        "' on widget " + widget.getName());
        }
    }

    private String resolvePropertyName(UIWidgetNode widget, UISetPropertyCommand propCmd) {
        if (propCmd.propertyName != null && !propCmd.propertyName.isEmpty()) {
            return propCmd.propertyName.toLowerCase();
        }

        if (propCmd.implicitWidgetValue && widget instanceof UIImageNode) {
            return "sprite";
        }
        if (propCmd.implicitWidgetValue && widget instanceof UIEditTextNode) {
            return "text";
        }

        return null;
    }

    private void syncMultiplayerTextView(UISetPropertyCommand propCmd, RuntimeUITargetValue.Resolved resolvedTarget,
                                         UITextViewNode textView, String value) {
        if (cmd != null && cmd.fromMultiplayerNetwork) {
            return;
        }
        if (app == null || propCmd == null || textView == null || textView.getWidgetDef() == null
                || !textView.getWidgetDef().isMultiplayer()) {
            return;
        }
        String uiName = resolvedTarget != null ? resolvedTarget.uiName : propCmd.uiName;
        String layerName = resolvedTarget != null ? resolvedTarget.layerName : propCmd.layerName;
        String widgetPath = resolvedTarget != null ? resolvedTarget.widgetPath : propCmd.widgetPath;
        app.syncMultiplayerUIText(uiName, layerName, widgetPath, value);
    }

    private void syncMultiplayerWidgetProperty(UISetPropertyCommand propCmd, RuntimeUITargetValue.Resolved resolvedTarget,
                                               UIWidgetNode widget, String propertyName, String value) {
        if (cmd != null && cmd.fromMultiplayerNetwork) {
            return;
        }
        if (app == null || propCmd == null || widget == null || widget.getWidgetDef() == null
                || !widget.getWidgetDef().isMultiplayer()
                || propertyName == null || propertyName.trim().isEmpty()) {
            return;
        }
        if (widget instanceof UITextViewNode && "text".equalsIgnoreCase(propertyName)) {
            return;
        }
        String uiName = resolvedTarget != null ? resolvedTarget.uiName : propCmd.uiName;
        String layerName = resolvedTarget != null ? resolvedTarget.layerName : propCmd.layerName;
        String widgetPath = resolvedTarget != null ? resolvedTarget.widgetPath : propCmd.widgetPath;
        app.syncMultiplayerUIProperty(uiName, layerName, widgetPath, propertyName, value);
    }

    private List<String> parseListCells(String value) {
        List<String> cells = new ArrayList<>();
        if (value == null || value.isEmpty()) {
            return cells;
        }
        String[] parts = value.split("\\|", -1);
        for (String part : parts) {
            cells.add(part.trim());
        }
        return cells;
    }

    private List<List<String>> parseListRows(String value) {
        List<List<String>> rows = new ArrayList<>();
        if (value == null || value.trim().isEmpty()) {
            return rows;
        }
        String[] lines = value.split("\\R|;", -1);
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                rows.add(parseListCells(line));
            }
        }
        return rows;
    }

    private List<Float> parseListWidths(String value) {
        List<Float> widths = new ArrayList<>();
        if (value == null || value.trim().isEmpty()) {
            return widths;
        }
        String[] parts = value.split("[|,]", -1);
        for (String part : parts) {
            try {
                widths.add(Math.max(1f, Float.parseFloat(part.trim())));
            } catch (NumberFormatException e) {
                app.handleRuntimeError("Invalid list column width: " + part.trim());
            }
        }
        return widths;
    }

    private String defaultToNull(String value) {
        if (value == null || value.trim().isEmpty() || "(default)".equalsIgnoreCase(value.trim())
                || "default".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return value;
    }
}
