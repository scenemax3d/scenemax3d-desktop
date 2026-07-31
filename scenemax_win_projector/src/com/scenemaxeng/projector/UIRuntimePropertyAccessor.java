package com.scenemaxeng.projector;

import com.scenemaxeng.common.ui.model.UIWidgetDef;
import com.scenemaxeng.common.ui.layout.LayoutRect;
import com.scenemaxeng.common.ui.widget.UIButtonNode;
import com.scenemaxeng.common.ui.widget.UIEditTextNode;
import com.scenemaxeng.common.ui.widget.UIImageNode;
import com.scenemaxeng.common.ui.widget.UIListViewNode;
import com.scenemaxeng.common.ui.widget.UIManager;
import com.scenemaxeng.common.ui.widget.UIPanelNode;
import com.scenemaxeng.common.ui.widget.UITextViewNode;
import com.scenemaxeng.common.ui.widget.UIWidgetNode;

import java.util.List;

final class UIRuntimePropertyAccessor {
    private UIRuntimePropertyAccessor() {
    }

    static Object read(SceneMaxApp app, String expressionPath, int line) {
        if (app == null) {
            return null;
        }

        UIManager uiManager = app.getUIManager();
        if (uiManager == null) {
            app.handleRuntimeError("Line " + line + ": UI system not initialized");
            return null;
        }

        String[] parts = expressionPath == null ? new String[0] : expressionPath.split("\\.");
        if (parts.length < 3) {
            return new RuntimeUITargetValue(expressionPath);
        }

        String propertyName = parts[parts.length - 1];
        UIWidgetNode widget = resolveWidget(uiManager, parts);
        if (widget == null) {
            RuntimeUITargetValue target = resolveTarget(uiManager, expressionPath);
            if (target != null) {
                return target;
            }
            app.handleRuntimeError("Line " + line + ": UI widget not found: " + withoutLastSegment(parts));
            return null;
        }

        Object value = readProperty(widget, propertyName);
        if (value == UnsupportedProperty.INSTANCE) {
            RuntimeUITargetValue target = resolveTarget(uiManager, expressionPath);
            if (target != null) {
                return target;
            }
            app.handleRuntimeError("Line " + line + ": Unknown UI property '" + propertyName
                    + "' on widget " + widget.getName());
            return null;
        }
        return value;
    }

    private static UIWidgetNode resolveWidget(UIManager uiManager, String[] parts) {
        String layerName = parts[0];
        String shortWidgetPath = join(parts, 1, parts.length - 1);
        UIWidgetNode widget = uiManager.resolveWidget(null, layerName, shortWidgetPath);
        if (widget != null) {
            return widget;
        }

        if (parts.length >= 4) {
            String uiName = parts[0];
            String explicitLayerName = parts[1];
            String explicitWidgetPath = join(parts, 2, parts.length - 1);
            widget = uiManager.resolveWidget(uiName, explicitLayerName, explicitWidgetPath);
        }
        return widget;
    }

    private static RuntimeUITargetValue resolveTarget(UIManager uiManager, String expressionPath) {
        RuntimeUITargetValue target = new RuntimeUITargetValue(expressionPath);
        return target.resolve(uiManager) == null ? null : target;
    }

    private static Object readProperty(UIWidgetNode widget, String rawPropertyName) {
        UIWidgetDef def = widget.getWidgetDef();
        String prop = normalize(rawPropertyName);

        if (widget instanceof UIEditTextNode) {
            UIEditTextNode editText = (UIEditTextNode) widget;
            if ("text".equals(prop) || "value".equals(prop)) {
                return editText.getText();
            }
            if ("placeholder".equals(prop) || "edittextplaceholder".equals(prop)) {
                return def.getEditTextPlaceholder();
            }
            if ("multiline".equals(prop) || "edittextmultiline".equals(prop)) {
                return Boolean.valueOf(def.isEditTextMultiline());
            }
            if ("backgroundcolor".equals(prop) || "edittextbackgroundcolor".equals(prop)) {
                return def.getEditTextBackgroundColor();
            }
            if ("focusedcolor".equals(prop) || "edittextfocusedcolor".equals(prop)) {
                return def.getEditTextFocusedColor();
            }
            if ("cursorcolor".equals(prop) || "edittextcursorcolor".equals(prop)) {
                return def.getEditTextCursorColor();
            }
            if ("selectioncolor".equals(prop) || "edittextselectioncolor".equals(prop)) {
                return def.getEditTextSelectionColor();
            }
        }

        if (widget instanceof UITextViewNode) {
            UITextViewNode textView = (UITextViewNode) widget;
            if ("text".equals(prop) || "value".equals(prop)) {
                return textView.getText();
            }
        }

        if (widget instanceof UIButtonNode) {
            if ("text".equals(prop) || "value".equals(prop)) {
                return def.getButtonText();
            }
            if ("color".equals(prop) || "buttoncolor".equals(prop)) {
                return def.getButtonColor();
            }
            if ("textcolor".equals(prop) || "buttontextcolor".equals(prop)) {
                return def.getButtonTextColor();
            }
        }

        if (widget instanceof UIImageNode) {
            if ("image".equals(prop) || "imagepath".equals(prop) || "value".equals(prop)) {
                return def.getImagePath();
            }
            if ("sprite".equals(prop) || "spritename".equals(prop)) {
                return def.getSpriteName();
            }
            if ("frame".equals(prop) || "spriteframe".equals(prop)) {
                return Double.valueOf(def.getSpriteFrame());
            }
        }

        if (widget instanceof UIListViewNode) {
            if ("selected".equals(prop) || "selectedrow".equals(prop) || "selectedrowindex".equals(prop)) {
                return Double.valueOf(((UIListViewNode) widget).getSelectedRowIndex());
            }
            if ("columns".equals(prop) || "columncount".equals(prop) || "listcolumncount".equals(prop)) {
                return Double.valueOf(def.getListColumnCount());
            }
            if ("headers".equals(prop) || "listheaders".equals(prop)) {
                return joinList(def.getListHeaders());
            }
            if ("rows".equals(prop) || "listrows".equals(prop)) {
                return joinRows(def.getListRows());
            }
            if ("style".equals(prop) || "listviewstyle".equals(prop)) {
                return def.getListViewStyle();
            }
            if ("transparency".equals(prop) || "listviewtransparency".equals(prop)) {
                return Double.valueOf(def.getListViewTransparency());
            }
        }

        if (widget instanceof UIPanelNode) {
            if ("color".equals(prop) || "backgroundcolor".equals(prop)) {
                return def.getBackgroundColor();
            }
            if ("image".equals(prop) || "backgroundimage".equals(prop)) {
                return def.getBackgroundImage();
            }
        }

        if ("visible".equals(prop)) {
            return Boolean.valueOf(def.isVisible());
        }
        if ("name".equals(prop)) {
            return def.getName();
        }
        if ("type".equals(prop)) {
            return def.getType() == null ? null : def.getType().name();
        }
        if ("textcolor".equals(prop) || "color".equals(prop)) {
            return def.getTextColor();
        }
        if ("fontsize".equals(prop)) {
            return Double.valueOf(def.getFontSize());
        }
        if ("font".equals(prop) || "fontname".equals(prop)) {
            return def.getFontName();
        }
        if ("x".equals(prop)) {
            LayoutRect rect = widget.getLayoutRect();
            return Double.valueOf(rect == null ? 0f : rect.x);
        }
        if ("y".equals(prop)) {
            LayoutRect rect = widget.getLayoutRect();
            return Double.valueOf(rect == null ? 0f : rect.y);
        }
        if ("width".equals(prop)) {
            LayoutRect rect = widget.getLayoutRect();
            return Double.valueOf(rect == null ? def.getWidth() : rect.width);
        }
        if ("height".equals(prop)) {
            LayoutRect rect = widget.getLayoutRect();
            return Double.valueOf(rect == null ? def.getHeight() : rect.height);
        }

        return UnsupportedProperty.INSTANCE;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace("_", "").replace("-", "").toLowerCase();
    }

    private static String withoutLastSegment(String[] parts) {
        return join(parts, 0, Math.max(0, parts.length - 1));
    }

    private static String join(String[] parts, int startInclusive, int endExclusive) {
        StringBuilder sb = new StringBuilder();
        for (int i = startInclusive; i < endExclusive; i++) {
            if (i > startInclusive) {
                sb.append('.');
            }
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    private static String joinList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append(value == null ? "" : value);
        }
        return sb.toString();
    }

    private static String joinRows(List<List<String>> rows) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (List<String> row : rows) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(joinList(row));
        }
        return sb.toString();
    }

    private enum UnsupportedProperty {
        INSTANCE
    }
}
