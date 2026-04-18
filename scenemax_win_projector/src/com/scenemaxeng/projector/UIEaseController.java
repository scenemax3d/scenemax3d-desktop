package com.scenemaxeng.projector;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.UIEaseCommand;
import com.scenemaxeng.common.ui.widget.UILayerNode;
import com.scenemaxeng.common.ui.widget.UIManager;
import com.scenemaxeng.common.ui.widget.UIWidgetNode;

import java.util.Locale;

/**
 * Runtime controller for UI slide easing:
 *   UI.layer1.panel1.ease("EaseInQuad", Up, 0.5)
 *   UI.hud.layer1.ease("EaseOutBounce", Left, 1)
 */
public class UIEaseController extends SceneMaxBaseController {

    private UIWidgetNode widgetTarget;
    private UILayerNode layerTarget;
    private UILayerNode owningLayer;
    private String rawEaseName;
    private String normalizedEaseName;
    private String normalizedDirection;
    private float durationSeconds;
    private float elapsedSeconds;
    private Vector3f travelOffset = new Vector3f();
    private boolean hideAtEnd;
    private boolean initialized;

    public UIEaseController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, UIEaseCommand cmd) {
        super(app, prg, scope, cmd);
    }

    @Override
    public boolean run(float tpf) {
        if (forceStop) {
            resetTargetOffset();
            return true;
        }

        if (!initialized) {
            if (!initializeEase()) {
                return true;
            }
        }

        if (durationSeconds <= 0f) {
            finishAnimation();
            return true;
        }

        elapsedSeconds = Math.min(durationSeconds, elapsedSeconds + tpf);
        float progress = FastMath.clamp(elapsedSeconds / durationSeconds, 0f, 1f);
        applyAnimatedOffset(progress);
        if (progress >= 1f) {
            finishAnimation();
            return true;
        }

        return false;
    }

    private boolean initializeEase() {
        initialized = true;
        UIEaseCommand easeCmd = (UIEaseCommand) this.cmd;
        UIManager uiManager = app.getUIManager();

        if (uiManager == null) {
            app.handleRuntimeError("UI system not initialized");
            return false;
        }

        String commandPathPrefix = easeCmd.uiName != null && !easeCmd.uiName.isEmpty()
                ? easeCmd.uiName + "." + easeCmd.layerName
                : easeCmd.layerName;

        if (easeCmd.widgetPath == null || easeCmd.widgetPath.isEmpty()) {
            if (easeCmd.uiName != null && !easeCmd.uiName.isEmpty() && !uiManager.isLoaded(easeCmd.uiName)) {
                UIWidgetNode nestedWidget = uiManager.resolveWidget(null, easeCmd.uiName, easeCmd.layerName);
                if (nestedWidget != null) {
                    widgetTarget = nestedWidget;
                    owningLayer = findOwningLayer(widgetTarget);
                }
            }

            if (widgetTarget == null) {
                layerTarget = uiManager.resolveLayer(easeCmd.uiName, easeCmd.layerName);
                owningLayer = layerTarget;
            }
        } else {
            widgetTarget = uiManager.resolveWidget(easeCmd.uiName, easeCmd.layerName, easeCmd.widgetPath);
            owningLayer = findOwningLayer(widgetTarget);
        }

        if (widgetTarget == null && layerTarget == null) {
            String targetPath = (easeCmd.widgetPath == null || easeCmd.widgetPath.isEmpty())
                    ? commandPathPrefix
                    : commandPathPrefix + "." + easeCmd.widgetPath;
            app.handleRuntimeError("UI ease target not found: " + targetPath);
            return false;
        }

        rawEaseName = evaluateExpression(easeCmd.easingExpr);
        normalizedEaseName = normalizeEaseName(rawEaseName);
        if (!isSupportedEase(normalizedEaseName)) {
            app.handleRuntimeError("Unsupported UI ease function: " + rawEaseName);
            return false;
        }

        normalizedDirection = normalizeDirection(easeCmd.directionName);
        if (normalizedDirection.isEmpty()) {
            app.handleRuntimeError("Unsupported UI ease direction: " + easeCmd.directionName);
            return false;
        }

        durationSeconds = parseDurationSeconds(evaluateExpression(easeCmd.durationExpr));
        hideAtEnd = normalizedEaseName.startsWith("easeout");
        elapsedSeconds = 0f;
        travelOffset = resolveTravelOffset();

        setTargetVisible(true);
        if (!hideAtEnd) {
            setTargetOffset(travelOffset);
        } else {
            resetTargetOffset();
        }
        return true;
    }

    private String evaluateExpression(com.abware.scenemaxlang.parser.SceneMaxParser.Logical_expressionContext expr) {
        Object value = new ActionLogicalExpressionVm(expr, this.scope).evaluate();
        return value == null ? "" : value.toString();
    }

    private float parseDurationSeconds(String value) {
        try {
            return Math.max(0f, Float.parseFloat(value));
        } catch (NumberFormatException ex) {
            app.handleRuntimeError("Invalid UI ease duration: " + value);
            return 0f;
        }
    }

    private void applyAnimatedOffset(float progress) {
        float easedProgress = applyEase(normalizedEaseName, progress);
        // The direction parameter describes the visible travel direction.
        // Ease-out moves toward the hidden side in that direction, while
        // ease-in must begin on the opposite side and travel into place.
        Vector3f offset = hideAtEnd
                ? travelOffset.mult(easedProgress)
                : travelOffset.mult(-(1f - easedProgress));
        setTargetOffset(offset);
    }

    private void finishAnimation() {
        resetTargetOffset();
        setTargetVisible(!hideAtEnd);
    }

    private void setTargetVisible(boolean visible) {
        if (widgetTarget != null) {
            widgetTarget.setWidgetVisible(visible);
        } else if (layerTarget != null) {
            layerTarget.setLayerVisible(visible);
        }
    }

    private void setTargetOffset(Vector3f offset) {
        if (widgetTarget != null) {
            widgetTarget.setAnimationOffset(offset.x, offset.y, offset.z);
        } else if (layerTarget != null) {
            layerTarget.setAnimationOffset(offset.x, offset.y, offset.z);
        }
    }

    private void resetTargetOffset() {
        if (widgetTarget != null) {
            widgetTarget.clearAnimationOffset();
        } else if (layerTarget != null) {
            layerTarget.clearAnimationOffset();
        }
    }

    private Vector3f resolveTravelOffset() {
        float width = getTargetWidth();
        float height = getTargetHeight();
        // Use the target's own size as the travel distance so the full easing
        // remains visible. For widgets this means sliding by exactly their
        // width/height; for layers, their runtime size is the screen size.
        switch (normalizedDirection) {
            case "left":
                return new Vector3f(-width, 0f, 0f);
            case "right":
                return new Vector3f(width, 0f, 0f);
            case "up":
                return new Vector3f(0f, height, 0f);
            case "down":
                return new Vector3f(0f, -height, 0f);
            default:
                return new Vector3f();
        }
    }

    private Spatial getTargetSpatial() {
        return widgetTarget != null ? widgetTarget : layerTarget;
    }

    private float getTargetWidth() {
        if (widgetTarget != null) {
            return Math.max(1f, widgetTarget.getRuntimeWidth());
        }
        return layerTarget != null ? Math.max(1f, layerTarget.getRuntimeWidth()) : 1f;
    }

    private float getTargetHeight() {
        if (widgetTarget != null) {
            return Math.max(1f, widgetTarget.getRuntimeHeight());
        }
        return layerTarget != null ? Math.max(1f, layerTarget.getRuntimeHeight()) : 1f;
    }

    private boolean isScreenSpaceTarget() {
        return owningLayer != null && owningLayer.isScreenSpace();
    }

    private UILayerNode findOwningLayer(Spatial spatial) {
        Spatial current = spatial;
        while (current != null) {
            if (current instanceof UILayerNode) {
                return (UILayerNode) current;
            }
            current = current.getParent();
        }
        return null;
    }

    private String normalizeEaseName(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim()
                .toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "");
    }

    private String normalizeDirection(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isSupportedEase(String easeName) {
        switch (easeName) {
            case "easeinquad":
            case "easeoutquad":
            case "easeincubic":
            case "easeoutcubic":
            case "easeinquart":
            case "easeoutquart":
            case "easeinquint":
            case "easeoutquint":
            case "easeinsine":
            case "easeoutsine":
            case "easeincirc":
            case "easeoutcirc":
            case "easeinexpo":
            case "easeoutexpo":
            case "easeinback":
            case "easeoutback":
            case "easeinelastic":
            case "easeoutelastic":
            case "easeinbounce":
            case "easeoutbounce":
            case "easeinpower":
            case "easeoutpower":
            case "easeinbezier":
            case "easeoutbezier":
            case "easeincustom":
            case "easeoutcustom":
                return true;
            default:
                return false;
        }
    }

    private float applyEase(String easeName, float progress) {
        float p = FastMath.clamp(progress, 0f, 1f);
        switch (easeName) {
            case "easeinquad":
                return p * p;
            case "easeoutquad":
                return 1f - (1f - p) * (1f - p);
            case "easeincubic":
                return p * p * p;
            case "easeoutcubic":
                return 1f - FastMath.pow(1f - p, 3f);
            case "easeinquart":
                return p * p * p * p;
            case "easeoutquart":
                return 1f - FastMath.pow(1f - p, 4f);
            case "easeinquint":
                return p * p * p * p * p;
            case "easeoutquint":
                return 1f - FastMath.pow(1f - p, 5f);
            case "easeinsine":
                return 1f - FastMath.cos((p * FastMath.PI) / 2f);
            case "easeoutsine":
                return FastMath.sin((p * FastMath.PI) / 2f);
            case "easeincirc":
                return 1f - FastMath.sqrt(1f - p * p);
            case "easeoutcirc":
                return FastMath.sqrt(1f - FastMath.pow(p - 1f, 2f));
            case "easeinexpo":
                return p <= 0f ? 0f : FastMath.pow(2f, 10f * p - 10f);
            case "easeoutexpo":
                return p >= 1f ? 1f : 1f - FastMath.pow(2f, -10f * p);
            case "easeinback":
                return applyEaseInBack(p);
            case "easeoutback":
                return applyEaseOutBack(p);
            case "easeinelastic":
                return applyEaseInElastic(p);
            case "easeoutelastic":
                return applyEaseOutElastic(p);
            case "easeinbounce":
                return 1f - applyEaseOutBounce(1f - p);
            case "easeoutbounce":
                return applyEaseOutBounce(p);
            case "easeinpower":
                return FastMath.pow(p, 2.5f);
            case "easeoutpower":
                return 1f - FastMath.pow(1f - p, 2.5f);
            case "easeinbezier":
                return cubicBezier(0.42f, 0f, 1f, 1f, p);
            case "easeoutbezier":
                return cubicBezier(0f, 0f, 0.58f, 1f, p);
            case "easeincustom":
                return cubicBezier(0.6f, 0.04f, 0.98f, 0.335f, p);
            case "easeoutcustom":
                return cubicBezier(0.075f, 0.82f, 0.165f, 1f, p);
            default:
                return p;
        }
    }

    private float applyEaseInBack(float p) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        return c3 * p * p * p - c1 * p * p;
    }

    private float applyEaseOutBack(float p) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        return 1f + c3 * FastMath.pow(p - 1f, 3f) + c1 * FastMath.pow(p - 1f, 2f);
    }

    private float applyEaseInElastic(float p) {
        if (p <= 0f) return 0f;
        if (p >= 1f) return 1f;
        float c4 = (2f * FastMath.PI) / 3f;
        return -FastMath.pow(2f, 10f * p - 10f)
                * FastMath.sin((p * 10f - 10.75f) * c4);
    }

    private float applyEaseOutElastic(float p) {
        if (p <= 0f) return 0f;
        if (p >= 1f) return 1f;
        float c4 = (2f * FastMath.PI) / 3f;
        return FastMath.pow(2f, -10f * p)
                * FastMath.sin((p * 10f - 0.75f) * c4) + 1f;
    }

    private float applyEaseOutBounce(float p) {
        float n1 = 7.5625f;
        float d1 = 2.75f;
        if (p < 1f / d1) {
            return n1 * p * p;
        } else if (p < 2f / d1) {
            float t = p - 1.5f / d1;
            return n1 * t * t + 0.75f;
        } else if (p < 2.5f / d1) {
            float t = p - 2.25f / d1;
            return n1 * t * t + 0.9375f;
        } else {
            float t = p - 2.625f / d1;
            return n1 * t * t + 0.984375f;
        }
    }

    private float cubicBezier(float x1, float y1, float x2, float y2, float progress) {
        float t0 = 0f;
        float t1 = 1f;
        float t = progress;
        for (int i = 0; i < 12; i++) {
            float x = cubicBezierComponent(t, x1, x2);
            if (Math.abs(x - progress) < 0.0005f) {
                break;
            }
            if (x < progress) {
                t0 = t;
            } else {
                t1 = t;
            }
            t = (t0 + t1) * 0.5f;
        }
        return cubicBezierComponent(t, y1, y2);
    }

    private float cubicBezierComponent(float t, float c1, float c2) {
        float inv = 1f - t;
        return 3f * inv * inv * t * c1
                + 3f * inv * t * t * c2
                + t * t * t;
    }
}
