package com.scenemaxeng.projector;

import com.abware.scenemaxlang.parser.SceneMaxBaseVisitor;
import com.abware.scenemaxlang.parser.SceneMaxParser;
import com.scenemaxeng.compiler.ActionStatementBase;
import com.scenemaxeng.compiler.VariableDef;
import org.antlr.v4.runtime.ParserRuleContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class ActionLogicalExpression extends ActionStatementBase {

    SceneMaxScope scope;
    ParserRuleContext ctx;
    static SceneMaxApp app;

    // ThreadLocal pool: one visitor per thread, reused for top-level evaluations only
    private static final ThreadLocal<LogicalExpressionVisitor> visitorPool = new ThreadLocal<>();
    // Tracks nesting depth so nested evals don't clobber the pooled visitor
    private static final ThreadLocal<int[]> evalDepth = ThreadLocal.withInitial(() -> new int[]{0});

    public ActionLogicalExpression(ParserRuleContext ctx, SceneMaxScope scope) {
        this.scope = scope;
        this.ctx = ctx;
    }

    public static void setApp(SceneMaxApp app) {
        ActionLogicalExpression.app = app;
    }

    public Object evaluate() {
        int[] depth = evalDepth.get();
        LogicalExpressionVisitor v;
        if (depth[0] == 0) {
            // Top-level: reuse pooled visitor
            v = visitorPool.get();
            if (v == null) {
                v = new LogicalExpressionVisitor();
                visitorPool.set(v);
            }
        } else {
            // Nested: must use fresh visitor to preserve caller's state
            v = new LogicalExpressionVisitor();
        }
        v.init(this.scope);
        depth[0]++;
        try {
            ctx.accept(v);
            return v.getEvalResult();
        } finally {
            depth[0]--;
        }
    }

    /**
     * Fast nested evaluation used internally by the visitor.
     * Always creates a fresh visitor since the caller's visitor is still live.
     */
    static Object evaluateNested(ParserRuleContext ctx, SceneMaxScope scope) {
        LogicalExpressionVisitor v = new LogicalExpressionVisitor();
        v.init(scope);
        int[] depth = evalDepth.get();
        depth[0]++;
        try {
            ctx.accept(v);
            return v.getEvalResult();
        } finally {
            depth[0]--;
        }
    }

    /**
     * Extract a double value from an Object without going through toString().
     * This is the single biggest micro-optimization: eliminates millions of
     * Double -> String -> Double round-trips.
     */
    static double toDouble(Object obj) {
        if (obj instanceof Double) return (Double) obj;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        if (obj == null) return 0.0;
        return Double.parseDouble(obj.toString());
    }

    private static class LogicalExpressionVisitor extends SceneMaxBaseVisitor<Object> {
        private static final String NEW_LINE_STRING = "newline";

        // Reusable StringBuilder for string concatenation - avoids allocation per expression
        private final StringBuilder sb = new StringBuilder(64);

        private SceneMaxScope scope;
        private Object res;
        private double retval;
        private String retvalStr;
        private Object retvalObj;
        private boolean isResBool;
        private Boolean retvalBool;
        private boolean isResString;
        private boolean isObject;
        private boolean hasRuntimeError;
        private boolean hasNumericRetval;

        void init(SceneMaxScope scope) {
            this.scope = scope;
            this.res = null;
            this.retval = 0.0;
            this.retvalStr = "";
            this.retvalObj = null;
            this.isResBool = false;
            this.retvalBool = null;
            this.isResString = false;
            this.isObject = false;
            this.hasRuntimeError = false;
            this.hasNumericRetval = false;
        }

        Object getEvalResult() {
            // Normalize numeric types to Double for callers that cast to (Double).
            // JSON returns Long/Integer for whole numbers, and various internal
            // paths may produce Integer (e.g. List.size()). The rest of the
            // engine universally expects numeric results as Double.
            if (res instanceof Number && !(res instanceof Double)) {
                res = ((Number) res).doubleValue();
            }
            return res;
        }

        // ── OR expression ──────────────────────────────────────────────
        public Object visitLogical_expression(SceneMaxParser.Logical_expressionContext ctx) {
            int count = ctx.getChildCount();
            // Fast path: single child (most common case - no OR operators)
            if (count == 1) {
                ctx.getChild(0).accept(this);
                return res;
            }

            boolean retval = false;
            for (int i = 0; i < count; ++i) {
                ctx.getChild(i).accept(this);
                if (res instanceof Boolean) {
                    retval = (retval || (Boolean) res);
                }
                if (isObject || retval) {
                    break;
                }
            }
            return retval;
        }

        // ── AND expression ─────────────────────────────────────────────
        public Object visitBooleanAndExpression(SceneMaxParser.BooleanAndExpressionContext ctx) {
            int count = ctx.getChildCount();
            // Fast path: single child (most common case - no AND operators)
            if (count == 1) {
                ctx.getChild(0).accept(this);
                return res;
            }

            boolean retval = true;
            for (int i = 0; i < count; ++i) {
                ctx.getChild(i).accept(this);
                if (res instanceof Boolean) {
                    retval = (retval && (Boolean) res);
                } else if (isObject) {
                    break;
                }
                if (!retval) {
                    break;
                }
            }
            return retval;
        }

        // ── Relational expression ──────────────────────────────────────
        public Object visitRelationalExpression(SceneMaxParser.RelationalExpressionContext ctx) {
            int count = ctx.getChildCount();
            // Fast path: single child (just a value, no comparison)
            if (count == 1) {
                ctx.getChild(0).accept(this);
                return res;
            }

            Object leftObj = null;
            double left = 0.0;
            boolean retval = true;
            String leftStr = "";
            String sign = "";

            for (int i = 0; i < count; ++i) {
                if ((i & 1) == 0) { // i % 2 == 0 via bitwise (avoids division)
                    ctx.getChild(i).accept(this);

                    if (isObject) {
                        if (leftObj == null) {
                            leftObj = res;
                            continue;
                        }
                        if (!(leftObj instanceof EntityInstBase)) {
                            return true;
                        }
                    }

                    if (sign.isEmpty()) {
                        // First operand
                        if (isResString) {
                            leftStr = res.toString();
                        } else if (res instanceof Boolean) {
                            retval = (Boolean) res;
                        } else {
                            left = toDouble(res);
                        }
                    } else {
                        // Compare using first char for speed
                        char c0 = sign.charAt(0);
                        switch (c0) {
                            case '=': // ==
                                retval = isResString ? leftStr.equals(res.toString()) : left == toDouble(res);
                                break;
                            case '!': // !=
                                retval = isResString ? !leftStr.equals(res.toString()) : left != toDouble(res);
                                break;
                            case '>': // > or >=
                                if (!isResString) {
                                    double right = toDouble(res);
                                    retval = sign.length() == 1 ? left > right : left >= right;
                                }
                                break;
                            case '<': // < or <= or <>
                                if (!isResString) {
                                    if (sign.length() == 1) {
                                        retval = left < toDouble(res);
                                    } else if (sign.charAt(1) == '=') {
                                        retval = left <= toDouble(res);
                                    } else { // <>
                                        retval = isResString ? !leftStr.equals(res.toString()) : left != toDouble(res);
                                    }
                                } else if (sign.length() > 1 && sign.charAt(1) == '>') {
                                    // <> with string
                                    retval = !leftStr.equals(res.toString());
                                }
                                break;
                            case 'c': // collides
                                retval = app.checkCollision((EntityInstBase) leftObj, (EntityInstBase) res);
                                break;
                        }
                    }
                } else {
                    sign = ctx.getChild(i).getText();
                }
            }

            if (!sign.isEmpty()) {
                isResBool = true;
                isResString = false;
                res = retval;
            }

            return retval;
        }

        // ── Multiplicative expression ──────────────────────────────────
        public Object visitMultiplicativeExpression(SceneMaxParser.MultiplicativeExpressionContext ctx) {
            int count = ctx.getChildCount();
            // Fast path: single child (no * / % operators)
            if (count == 1) {
                ctx.getChild(0).accept(this);
                return res;
            }

            double retval = 0.0;
            String sign = "";

            for (int i = 0; i < count; ++i) {
                if ((i & 1) == 0) {
                    ctx.getChild(i).accept(this);
                    if (sign.isEmpty()) {
                        if (res != null) {
                            if (res instanceof Double) {
                                retval = (Double) res;
                            } else if (res instanceof String) {
                                return res;
                            } else {
                                return res;
                            }
                        } else {
                            retval = 0.0;
                        }
                    } else {
                        double right = toDouble(res);
                        char c = sign.charAt(0);
                        if (c == '*') {
                            retval *= right;
                        } else if (c == '/') {
                            retval /= right;
                        } else { // %
                            retval %= right;
                        }
                    }
                } else {
                    sign = ctx.getChild(i).getText();
                }
            }

            res = retval;
            return retval;
        }

        // ── Additive expression ────────────────────────────────────────
        public Object visitAdditiveExpression(SceneMaxParser.AdditiveExpressionContext ctx) {
            int count = ctx.getChildCount();
            // Fast path: single child (no + - operators)
            if (count == 1) {
                ctx.getChild(0).accept(this);
                return res;
            }

            retval = 0.0;
            retvalStr = "";

            String sign = "";

            for (int i = 0; i < count; ++i) {
                if ((i & 1) == 0) {
                    ctx.getChild(i).accept(this);
                    if (sign.isEmpty()) {
                        // First operand
                        if (isResString) {
                            retvalStr = res.toString();
                        } else if (isObject) {
                            retvalObj = res;
                        } else if (res instanceof Boolean) {
                            retvalBool = (Boolean) res;
                        } else if (!hasRuntimeError) {
                            hasNumericRetval = true;
                            retval = toDouble(res);
                        }
                    } else if (sign.charAt(0) == '+') {
                        if (!hasRuntimeError) {
                            if (isResString) {
                                // Use StringBuilder for concatenation
                                if (sb.length() == 0) {
                                    sb.append(retvalStr);
                                }
                                String addStr = res.toString();
                                if (addStr.endsWith(".0")) {
                                    sb.append(addStr, 0, addStr.length() - 2);
                                } else {
                                    sb.append(addStr);
                                }
                            } else {
                                retval += toDouble(res);
                            }
                        }
                    } else { // '-'
                        if (!hasRuntimeError) {
                            if (isResString) {
                                app.handleRuntimeError("Line: " + ctx.start.getLine() + ", Invalid '-' operator in String expression");
                            } else {
                                retval -= toDouble(res);
                            }
                        }
                    }
                } else {
                    sign = ctx.getChild(i).getText();
                }
            }

            if (isResString) {
                if (sb.length() > 0) {
                    retvalStr = sb.toString();
                    sb.setLength(0); // reset for reuse
                }
                res = retvalStr;
                return retvalStr;
            } else if (isObject) {
                return retvalObj;
            } else if (isResBool) {
                return retvalBool;
            } else {
                res = retval;
                return retval;
            }
        }

        // ── Unary expression ───────────────────────────────────────────
        public Object visitUnaryExpression(SceneMaxParser.UnaryExpressionContext ctx) {
            ctx.primaryExpression().accept(this);
            if (ctx.NOT() != null) {
                res = !(Boolean) res;
            }
            return res;
        }

        // ── Value (leaf node) ──────────────────────────────────────────
        public Object visitValue(SceneMaxParser.ValueContext ctx) {

            if (ctx.BOOLEAN() != null) {
                // Avoid getText() + parseBoolean() — just check the token directly
                res = ctx.BOOLEAN().getText().charAt(0) == 't' || ctx.BOOLEAN().getText().charAt(0) == 'T';
                return res;
            }

            if (ctx.number_expr() != null) {
                res = Double.parseDouble(ctx.getText());
                return res;
            }

            if (ctx.QUOTED_STRING() != null) {
                String tmp = ctx.QUOTED_STRING().getText();
                res = tmp.length() >= 3 ? tmp.substring(1, tmp.length() - 1) : "";
                turnOnIsString();
                return res;
            }

            if (ctx.variable_data_field() != null) {
                SceneMaxParser.Variable_data_fieldContext dataFieldCtx = ctx.variable_data_field();
                String var = dataFieldCtx.var_decl().getText();
                RunTimeVarDef vd1 = app.findVarRuntime(null, scope, var);
                if (vd1 == null) {
                    res = null;
                } else {
                    String fieldName = dataFieldCtx.field_name().getText();
                    res = app.getUserDataFieldValue(vd1.varName, fieldName);
                    if (res instanceof String) {
                        turnOnIsString();
                    }
                }
                return res;
            }

            if (ctx.variable_field() != null) {
                String var = ctx.variable_field().var_decl().getText();
                RunTimeVarDef vd1 = app.findVarRuntime(null, scope, var);
                if (vd1 == null) {
                    GroupInst ginst = scope.getGroup(var);
                    if (ginst != null) {
                        String field = ctx.variable_field().var_field().getText();
                        if (field.equalsIgnoreCase("hit")) {
                            res = ginst.lastClosestRayCheck;
                            isObject = true;
                            return res;
                        }
                    } else {
                        return null;
                    }
                }
                String field = ctx.variable_field().var_field().getText();
                res = app.getFieldValue(vd1.varName, field);
                if (res instanceof EntityInstBase) {
                    isObject = true;
                }
                return res;
            }

            if (ctx.function_value() != null) {
                FunctionInvoker fi = new FunctionInvoker(ctx.function_value(), app, scope);
                if (fi.invoke()) {
                    res = fi.retval;
                    if (fi.retvalType == VariableDef.VAR_TYPE_STRING) {
                        turnOnIsString();
                    }
                } else {
                    hasRuntimeError = true;
                    app.handleRuntimeError("Line: " + ctx.start.getLine() + ", " + fi.runtimeError);
                }
                return res;
            }

            if (ctx.logical_expression_pointer() != null) {
                String varName = ctx.logical_expression_pointer().var_decl().getText();
                VarInst vi = scope.getVar(varName);
                if (vi == null) {
                    hasRuntimeError = true;
                    app.handleRuntimeError("Line " + ctx.start.getLine() + ": Logical expression pointer '" + varName + "' not found");
                    return null;
                }
                res = evaluateNested((ParserRuleContext) vi.value, scope);
                if (res instanceof Boolean) {
                    isResBool = true;
                } else if (res instanceof String) {
                    turnOnIsString();
                } else if (res instanceof List || res instanceof EntityInstBase) {
                    isObject = true;
                }
                return res;
            }

            if (ctx.fetch_array_value() != null) {
                String varName = ctx.fetch_array_value().var_decl().getText();
                VarInst vi = scope.getVar(varName);
                if (vi == null) {
                    hasRuntimeError = true;
                    app.handleRuntimeError("Line " + ctx.start.getLine() + ": Array variable '" + varName + "' not found");
                    return null;
                }
                Object indexObj = evaluateNested(ctx.fetch_array_value().logical_expression(), scope);
                int index = (int) toDouble(indexObj);
                if (vi.values == null || vi.values.size() <= index || index < 0) {
                    hasRuntimeError = true;
                    app.handleRuntimeError("Line " + ctx.start.getLine() + ": Array '" + varName + "' index out of bound");
                    return null;
                }
                res = vi.values.get(index);
                if (res instanceof String) {
                    turnOnIsString();
                } else if (res instanceof List || res instanceof EntityInstBase) {
                    isObject = true;
                }
                return res;
            }

            if (ctx.get_array_length() != null) {
                String varName = ctx.get_array_length().var_decl().getText();
                VarInst vi = scope.getVar(varName);
                if (vi == null) {
                    hasRuntimeError = true;
                    app.handleRuntimeError("Line " + ctx.start.getLine() + ": Array variable '" + varName + "' not found");
                    return null;
                }
                if (vi.values == null) {
                    hasRuntimeError = true;
                    app.handleRuntimeError("Line " + ctx.start.getLine() + ": Array '" + varName + "' is not initialized");
                    return null;
                }
                res = (double) vi.values.size();
                return res;
            }

            if (ctx.calc_distance_value() != null) {
                String obj1 = ctx.calc_distance_value().first_object().getText();
                String obj2 = ctx.calc_distance_value().second_object().getText();
                RunTimeVarDef vd1 = app.findVarRuntime(null, scope, obj1);
                if (vd1 == null) return null;
                RunTimeVarDef vd2 = app.findVarRuntime(null, scope, obj2);
                if (vd2 == null) return null;
                res = app.calcDistance(vd1.varName, vd2.varName);
                return res;
            }

            if (ctx.calc_angle_value() != null) {
                String obj1 = ctx.calc_angle_value().first_object().getText();
                String obj2 = ctx.calc_angle_value().second_object().getText();
                RunTimeVarDef vd1 = app.findVarRuntime(null, scope, obj1);
                if (vd1 == null) return null;
                RunTimeVarDef vd2 = app.findVarRuntime(null, scope, obj2);
                if (vd2 == null) return null;
                res = app.calcAngle(vd1.varName, vd2.varName);
                return res;
            }

            if (ctx.get_json_value() != null) {
                return visitJsonValue(ctx);
            }

            // Fall-through: identifier or newline
            res = ctx.getText();
            if (res.toString().equalsIgnoreCase(NEW_LINE_STRING)) {
                res = "\n";
                turnOnIsString();
            } else {
                String name = res.toString();
                VarInst varInst = this.scope.getVar(name);
                if (varInst != null) {
                    if (varInst.varType == VariableDef.VAR_TYPE_ARRAY) {
                        isObject = true;
                        res = varInst.values;
                    } else {
                        res = varInst.value;
                        if (varInst.varType == VariableDef.VAR_TYPE_STRING) {
                            turnOnIsString();
                        } else if (res instanceof EntityInstBase) {
                            isObject = true;
                        }
                    }
                } else {
                    decideWhichObjectIsRes(ctx, name);
                }
            }

            return res;
        }

        private Object visitJsonValue(SceneMaxParser.ValueContext ctx) {
            String varName = ctx.get_json_value().var_decl().getText();
            VarInst vi = scope.getVar(varName);
            if (vi == null) {
                hasRuntimeError = true;
                app.handleRuntimeError("Line " + ctx.start.getLine() + ": Array variable '" + varName + "' not found");
                return null;
            }

            JSONObject obj = null;
            JSONArray objArr = null;

            if (vi.value instanceof JSONObject) {
                obj = (JSONObject) vi.value;
            } else if (vi.value instanceof String) {
                String buff = (String) vi.value;
                try {
                    obj = new JSONObject(buff);
                } catch (JSONException e) {
                    try {
                        objArr = new JSONArray(buff);
                    } catch (JSONException err) {
                        err.printStackTrace();
                    }
                }
            }

            if (obj == null && objArr == null) {
                res = null;
                return null;
            }

            for (SceneMaxParser.Json_element_acceessorContext elem : ctx.get_json_value().json_accessor_expression().json_element_acceessor()) {
                if (elem.json_field_accessor() != null) {
                    if (obj == null) return null;
                    String fieldName = elem.json_field_accessor().var_decl().getText();
                    Object fieldVal = null;
                    try {
                        fieldVal = obj.get(fieldName);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    if (fieldVal instanceof JSONObject) {
                        obj = (JSONObject) fieldVal;
                        objArr = null;
                    } else if (fieldVal instanceof JSONArray) {
                        objArr = (JSONArray) fieldVal;
                        obj = null;
                    } else {
                        res = fieldVal;
                        if (res instanceof String) turnOnIsString();
                        return fieldVal;
                    }
                } else if (elem.json_array_item_accessor() != null) {
                    if (objArr == null) return null;
                    double index = toDouble(evaluateNested(elem.json_array_item_accessor().logical_expression(), scope));
                    Object fieldVal = null;
                    try {
                        fieldVal = objArr.get((int) index);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    if (fieldVal instanceof JSONObject) {
                        obj = (JSONObject) fieldVal;
                        objArr = null;
                    } else if (fieldVal instanceof JSONArray) {
                        objArr = (JSONArray) fieldVal;
                        obj = null;
                    } else {
                        res = fieldVal;
                        if (res instanceof String) turnOnIsString();
                        return fieldVal;
                    }
                }
            }

            res = null;
            return null;
        }

        /**
         * Resolve an identifier to a scene object. Accepts pre-computed name
         * to avoid a redundant res.toString() call.
         */
        private void decideWhichObjectIsRes(SceneMaxParser.ValueContext ctx, String name) {
            isObject = true;
            EntityInstBase found = this.scope.getEntityInst(name);

            if (found != null) {
                res = found;
            } else {
                isObject = false;
                hasRuntimeError = true;
                app.handleRuntimeError("Line: " + ctx.start.getLine() + ", '" + name + "' is not a valid number or variable");
            }
        }

        private void turnOnIsString() {
            isResString = true;
            if (hasNumericRetval) {
                // Convert accumulated numeric to string - use efficient formatting
                long l = (long) retval;
                retvalStr = (retval == l) ? Long.toString(l) : Double.toString(retval);
                hasNumericRetval = false;
            }
        }
    }
}
