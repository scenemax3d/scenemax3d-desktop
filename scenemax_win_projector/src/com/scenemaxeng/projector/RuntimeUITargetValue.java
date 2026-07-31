package com.scenemaxeng.projector;

import com.scenemaxeng.common.ui.widget.UILayerNode;
import com.scenemaxeng.common.ui.widget.UIManager;
import com.scenemaxeng.common.ui.widget.UIWidgetNode;

final class RuntimeUITargetValue {
    final String expressionPath;

    RuntimeUITargetValue(String expressionPath) {
        this.expressionPath = expressionPath == null ? "" : expressionPath.trim();
    }

    Resolved resolve(UIManager uiManager) {
        if (uiManager == null || expressionPath.isEmpty()) {
            return null;
        }

        String[] parts = expressionPath.split("\\.");
        if (parts.length == 1) {
            UILayerNode layer = uiManager.resolveLayer(null, parts[0]);
            return layer == null ? null : new Resolved(null, parts[0], "", layer, null);
        }

        if (uiManager.isLoaded(parts[0])) {
            Resolved explicit = resolveExplicit(uiManager, parts);
            if (explicit != null) {
                return explicit;
            }
        }

        UIWidgetNode activeWidget = uiManager.resolveWidget(null, parts[0], join(parts, 1, parts.length));
        if (activeWidget != null) {
            return new Resolved(null, parts[0], join(parts, 1, parts.length), null, activeWidget);
        }

        return resolveExplicit(uiManager, parts);
    }

    String displayPath() {
        return expressionPath;
    }

    static RuntimeUITargetValue fromVariable(SceneMaxScope scope, String varName, SceneMaxApp app, int line) {
        if (scope == null || varName == null || varName.trim().isEmpty()) {
            return null;
        }
        VarInst var = scope.getVar(varName);
        if (var == null || !(var.value instanceof RuntimeUITargetValue)) {
            if (app != null) {
                String prefix = line > 0 ? "Line " + line + ": " : "";
                app.handleRuntimeError(prefix + "Variable '" + varName + "' does not contain a UI target");
            }
            return null;
        }
        return (RuntimeUITargetValue) var.value;
    }

    private Resolved resolveExplicit(UIManager uiManager, String[] parts) {
        if (parts.length < 2) {
            return null;
        }

        String uiName = parts[0];
        String layerName = parts[1];
        if (parts.length == 2) {
            UILayerNode layer = uiManager.resolveLayer(uiName, layerName);
            return layer == null ? null : new Resolved(uiName, layerName, "", layer, null);
        }

        String widgetPath = join(parts, 2, parts.length);
        UIWidgetNode widget = uiManager.resolveWidget(uiName, layerName, widgetPath);
        return widget == null ? null : new Resolved(uiName, layerName, widgetPath, null, widget);
    }

    static String join(String[] parts, int startInclusive, int endExclusive) {
        StringBuilder sb = new StringBuilder();
        for (int i = startInclusive; i < endExclusive; i++) {
            if (i > startInclusive) {
                sb.append('.');
            }
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    static final class Resolved {
        final String uiName;
        final String layerName;
        final String widgetPath;
        final UILayerNode layer;
        final UIWidgetNode widget;

        Resolved(String uiName, String layerName, String widgetPath, UILayerNode layer, UIWidgetNode widget) {
            this.uiName = uiName;
            this.layerName = layerName;
            this.widgetPath = widgetPath == null ? "" : widgetPath;
            this.layer = layer;
            this.widget = widget;
        }

        String displayPath() {
            StringBuilder sb = new StringBuilder();
            if (uiName != null && !uiName.isEmpty()) {
                sb.append(uiName).append('.');
            }
            sb.append(layerName);
            if (widgetPath != null && !widgetPath.isEmpty()) {
                sb.append('.').append(widgetPath);
            }
            return sb.toString();
        }
    }
}
