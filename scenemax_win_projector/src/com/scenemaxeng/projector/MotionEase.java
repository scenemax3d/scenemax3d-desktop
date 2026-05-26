package com.scenemaxeng.projector;

import com.jme3.math.FastMath;
import com.scenemaxeng.compiler.ActionStatementBase;
import com.scenemaxeng.compiler.MotionEaseType;
import com.scenemaxeng.compiler.TimedMotionCommand;
import com.scenemaxeng.compiler.TimedVariableMotionCommand;
import com.abware.scenemaxlang.parser.SceneMaxParser;

import java.util.List;
import java.util.Locale;

public final class MotionEase {

    public static final MotionEaseSpec LINEAR_SPEC = new MotionEaseSpec(MotionEaseType.LINEAR, null, new float[0]);

    private MotionEase() {
    }

    public static MotionEaseSpec fromCommand(ActionStatementBase cmd, SceneMaxScope scope) {
        if (cmd instanceof TimedVariableMotionCommand) {
            TimedVariableMotionCommand motion = (TimedVariableMotionCommand) cmd;
            return fromParts(motion.motionEaseType, motion.motionEaseFunction, motion.motionEaseParamExprs, scope);
        }
        if (cmd instanceof TimedMotionCommand) {
            TimedMotionCommand motion = (TimedMotionCommand) cmd;
            return fromParts(motion.motionEaseType, motion.motionEaseFunction, motion.motionEaseParamExprs, scope);
        }
        return LINEAR_SPEC;
    }

    public static MotionEaseSpec fromParts(int easeType, String functionName,
                                           List<SceneMaxParser.Logical_expressionContext> paramExprs,
                                           SceneMaxScope scope) {
        return new MotionEaseSpec(easeType, functionName, evaluateParams(paramExprs, scope));
    }

    private static float[] evaluateParams(List<SceneMaxParser.Logical_expressionContext> paramExprs, SceneMaxScope scope) {
        if (paramExprs == null || paramExprs.isEmpty()) {
            return new float[0];
        }

        float[] params = new float[paramExprs.size()];
        for (int i = 0; i < paramExprs.size(); i++) {
            Object value = new ActionLogicalExpressionVm(paramExprs.get(i), scope).evaluate();
            params[i] = (float) ActionLogicalExpressionVm.toDouble(value);
        }
        return params;
    }

    public static float delta(int easeType, float previousProgress, float currentProgress) {
        float from = apply(easeType, previousProgress);
        float to = apply(easeType, currentProgress);
        return to - from;
    }

    public static float delta(MotionEaseSpec spec, float previousProgress, float currentProgress) {
        float from = apply(spec, previousProgress);
        float to = apply(spec, currentProgress);
        return to - from;
    }

    public static float apply(int easeType, float progress) {
        return apply(new MotionEaseSpec(easeType, null, new float[0]), progress);
    }

    public static float apply(MotionEaseSpec spec, float progress) {
        if (spec == null) {
            spec = LINEAR_SPEC;
        }
        float p = FastMath.clamp(progress, 0f, 1f);
        String family = normalizeFunction(spec.functionName);
        if (family == null || family.isEmpty()) {
            family = "quad";
        }
        if ("linear".equals(family) || spec.easeType == MotionEaseType.LINEAR) {
            return p;
        }

        Float easeWindow = findEaseWindow(family, spec.params);
        if (easeWindow != null) {
            return applyWindowed(spec.easeType, family, spec.params, easeWindow, p);
        }

        switch (spec.easeType) {
            case MotionEaseType.EASE_IN:
                return applyEaseIn(family, spec.params, p);
            case MotionEaseType.EASE_OUT:
                return applyEaseOut(family, spec.params, p);
            case MotionEaseType.EASE_IN_OUT:
                if (p < 0.5f) {
                    return 0.5f * applyEaseIn(family, spec.params, p * 2f);
                }
                return 0.5f + 0.5f * applyEaseOut(family, spec.params, (p - 0.5f) * 2f);
            default:
                return p;
        }
    }

    private static float applyWindowed(int easeType, String family, float[] params, float easeWindow, float p) {
        float e = FastMath.clamp(easeWindow, 0f, 1f);
        if (e <= 0f) {
            return p;
        }

        switch (easeType) {
            case MotionEaseType.EASE_IN:
                if (p <= e) {
                    return e * applyEaseIn(family, params, p / e);
                }
                return p;
            case MotionEaseType.EASE_OUT:
                if (p < 1f - e) {
                    return p;
                }
                return (1f - e) + e * applyEaseOut(family, params, (p - (1f - e)) / e);
            case MotionEaseType.EASE_IN_OUT:
                e = Math.min(e, 0.5f);
                if (p <= e) {
                    return e * applyEaseIn(family, params, p / e);
                }
                if (p >= 1f - e) {
                    return (1f - e) + e * applyEaseOut(family, params, (p - (1f - e)) / e);
                }
                return p;
            default:
                return p;
        }
    }

    private static float applyEaseIn(String family, float[] params, float progress) {
        float p = FastMath.clamp(progress, 0f, 1f);
        switch (family) {
            case "sine":
                return 1f - FastMath.cos((p * FastMath.PI) / 2f);
            case "quad":
                return p * p;
            case "cubic":
                return p * p * p;
            case "quart":
                return p * p * p * p;
            case "quint":
                return p * p * p * p * p;
            case "expo":
                return p <= 0f ? 0f : FastMath.pow(2f, 10f * p - 10f);
            case "circ":
                return 1f - FastMath.sqrt(1f - p * p);
            case "back":
                return applyEaseInBack(p, param(params, 0, 1.70158f));
            case "elastic":
                return applyEaseInElastic(p, param(params, 0, 1f), param(params, 1, 0.3f));
            case "bounce":
                return 1f - applyEaseOutBounce(1f - p);
            case "power":
                return FastMath.pow(p, param(params, 0, 2.5f));
            case "cubicbezier":
            case "bezier":
                return cubicBezier(param(params, 0, 0.42f), param(params, 1, 0f),
                        param(params, 2, 1f), param(params, 3, 1f), p);
            default:
                return p * p;
        }
    }

    private static float applyEaseOut(String family, float[] params, float progress) {
        float p = FastMath.clamp(progress, 0f, 1f);
        switch (family) {
            case "bounce":
                return applyEaseOutBounce(p);
            case "cubicbezier":
            case "bezier":
                return cubicBezier(param(params, 0, 0f), param(params, 1, 0f),
                        param(params, 2, 0.58f), param(params, 3, 1f), p);
            default:
                return 1f - applyEaseIn(family, params, 1f - p);
        }
    }

    private static Float findEaseWindow(String family, float[] params) {
        if (params == null || params.length == 0) {
            return null;
        }
        switch (family) {
            case "sine":
            case "quad":
            case "cubic":
            case "quart":
            case "quint":
            case "expo":
            case "circ":
            case "bounce":
                return params[0];
            case "back":
            case "power":
                return params.length > 1 ? params[1] : null;
            case "elastic":
                return params.length > 2 ? params[2] : null;
            case "cubicbezier":
            case "bezier":
                return params.length > 4 ? params[4] : null;
            default:
                return null;
        }
    }

    private static String normalizeFunction(String raw) {
        if (raw == null) {
            return "";
        }
        String name = raw.trim()
                .toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "");
        if (name.startsWith("easeinout")) {
            return name.substring("easeinout".length());
        }
        if (name.startsWith("easein")) {
            return name.substring("easein".length());
        }
        if (name.startsWith("easeout")) {
            return name.substring("easeout".length());
        }
        return name;
    }

    private static float param(float[] params, int index, float defaultValue) {
        if (params == null || params.length <= index) {
            return defaultValue;
        }
        return params[index];
    }

    private static float applyEaseInBack(float p, float overshoot) {
        float c3 = overshoot + 1f;
        return c3 * p * p * p - overshoot * p * p;
    }

    private static float applyEaseInElastic(float p, float amplitude, float period) {
        if (p <= 0f) return 0f;
        if (p >= 1f) return 1f;
        float safePeriod = Math.max(0.0001f, period);
        float safeAmplitude = Math.max(0.0001f, amplitude);
        float s = safePeriod / (2f * FastMath.PI) * (float) Math.asin(Math.min(1f, 1f / safeAmplitude));
        return -(safeAmplitude * FastMath.pow(2f, 10f * (p - 1f))
                * FastMath.sin((p - 1f - s) * (2f * FastMath.PI) / safePeriod));
    }

    private static float applyEaseOutBounce(float p) {
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

    private static float cubicBezier(float x1, float y1, float x2, float y2, float progress) {
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

    private static float cubicBezierComponent(float t, float c1, float c2) {
        float inv = 1f - t;
        return 3f * inv * inv * t * c1
                + 3f * inv * t * t * c2
                + t * t * t;
    }

    public static final class MotionEaseSpec {
        public final int easeType;
        public final String functionName;
        public final float[] params;

        public MotionEaseSpec(int easeType, String functionName, float[] params) {
            this.easeType = easeType;
            this.functionName = functionName;
            this.params = params == null ? new float[0] : params;
        }
    }
}
