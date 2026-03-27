package com.scenemaxeng.projector;

import com.abware.scenemaxlang.parser.SceneMaxBaseVisitor;
import com.abware.scenemaxlang.parser.SceneMaxParser;
import com.scenemaxeng.compiler.ActionStatementBase;
import com.scenemaxeng.compiler.VariableDef;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bytecode-VM replacement for ActionLogicalExpression.
 *
 * Java 11 compatible version.
 */
public class ActionLogicalExpressionVm extends ActionStatementBase {

    private final SceneMaxScope scope;
    private final ParserRuleContext ctx;
    private final CompiledProgram program;

    private static SceneMaxApp app;

    private static final Map<ParserRuleContext, CompiledProgram> PROGRAM_CACHE =
            Collections.synchronizedMap(new IdentityHashMap<ParserRuleContext, CompiledProgram>());

    private static final ThreadLocal<ExecutionEngine> ENGINE_POOL =
            ThreadLocal.withInitial(new java.util.function.Supplier<ExecutionEngine>() {
                @Override
                public ExecutionEngine get() {
                    return new ExecutionEngine();
                }
            });

    public ActionLogicalExpressionVm(ParserRuleContext ctx, SceneMaxScope scope) {
        this.scope = scope;
        this.ctx = ctx;
        this.program = compileCached(ctx);
    }

    public static void setApp(SceneMaxApp app) {
        ActionLogicalExpressionVm.app = app;
    }

    public Object evaluate() {
        return ENGINE_POOL.get().execute(program, scope);
    }

    static Object evaluateNested(ParserRuleContext ctx, SceneMaxScope scope) {
        return ENGINE_POOL.get().execute(compileCached(ctx), scope);
    }

    static double toDouble(Object obj) {
        if (obj instanceof Double) {
            return ((Double) obj).doubleValue();
        }
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        if (obj == null) {
            return 0.0;
        }
        return Double.parseDouble(obj.toString());
    }

    private static CompiledProgram compileCached(ParserRuleContext ctx) {
        CompiledProgram cached = PROGRAM_CACHE.get(ctx);
        if (cached != null) {
            return cached;
        }
        CompiledProgram compiled = new Compiler().compile(ctx);
        PROGRAM_CACHE.put(ctx, compiled);
        return compiled;
    }

    private enum OpCode {
        PUSH_CONST,
        LOAD_IDENTIFIER,
        LOAD_VARIABLE_FIELD,
        LOAD_VARIABLE_DATA_FIELD,
        LOAD_FUNCTION_VALUE,
        LOAD_EXPR_POINTER,
        LOAD_ARRAY_VALUE,
        LOAD_ARRAY_LENGTH,
        LOAD_DISTANCE,
        LOAD_ANGLE,
        LOAD_JSON_VALUE,

        NOT,
        NEGATE,

        ADD,
        SUB,
        MUL,
        DIV,
        MOD,

        EQ,
        NE,
        LT,
        LE,
        GT,
        GE,
        COLLIDES,

        JUMP,
        JUMP_IF_TRUE,
        JUMP_IF_FALSE,

        RETURN
    }

    private static final class Instruction {
        final OpCode op;
        final Object a;
        final Object b;
        final int line;

        Instruction(OpCode op) {
            this(op, null, null, -1);
        }

        Instruction(OpCode op, Object a) {
            this(op, a, null, -1);
        }

        Instruction(OpCode op, Object a, int line) {
            this(op, a, null, line);
        }

        Instruction(OpCode op, Object a, Object b, int line) {
            this.op = op;
            this.a = a;
            this.b = b;
            this.line = line;
        }
    }

    private static final class CompiledProgram {
        final List<Instruction> code;

        CompiledProgram(List<Instruction> code) {
            this.code = code;
        }
    }

    private static final class JsonAccessorStep {
        final boolean array;
        final String fieldName;
        final CompiledProgram indexProgram;

        private JsonAccessorStep(boolean array, String fieldName, CompiledProgram indexProgram) {
            this.array = array;
            this.fieldName = fieldName;
            this.indexProgram = indexProgram;
        }

        static JsonAccessorStep field(String fieldName) {
            return new JsonAccessorStep(false, fieldName, null);
        }

        static JsonAccessorStep array(CompiledProgram indexProgram) {
            return new JsonAccessorStep(true, null, indexProgram);
        }
    }

    private static final class JsonAccessorSpec {
        final String varName;
        final List<JsonAccessorStep> steps;

        JsonAccessorSpec(String varName, List<JsonAccessorStep> steps) {
            this.varName = varName;
            this.steps = steps;
        }
    }

    private static final class Compiler extends SceneMaxBaseVisitor<Void> {
        private static final String NEW_LINE_STRING = "newline";
        private final List<Instruction> code = new ArrayList<Instruction>();

        CompiledProgram compile(ParserRuleContext ctx) {
            ctx.accept(this);
            code.add(new Instruction(OpCode.RETURN));
            return new CompiledProgram(new ArrayList<Instruction>(code));
        }

        private int lineOf(ParseTree t) {
            if (t instanceof ParserRuleContext) {
                ParserRuleContext prc = (ParserRuleContext) t;
                return prc.start != null ? prc.start.getLine() : -1;
            }
            return -1;
        }

        @Override
        public Void visitLogical_expression(SceneMaxParser.Logical_expressionContext ctx) {
            int count = ctx.getChildCount();
            if (count == 1) {
                ctx.getChild(0).accept(this);
                return null;
            }

            List<Integer> exitJumps = new ArrayList<Integer>();
            int i;
            for (i = 0; i < count; i += 2) {
                ctx.getChild(i).accept(this);
                code.add(new Instruction(OpCode.JUMP_IF_TRUE, Integer.valueOf(-1), lineOf(ctx)));
                exitJumps.add(Integer.valueOf(code.size() - 1));
            }
            code.add(new Instruction(OpCode.PUSH_CONST, Boolean.FALSE, lineOf(ctx)));
            code.add(new Instruction(OpCode.JUMP, Integer.valueOf(-1), lineOf(ctx)));
            int jumpToFalse = code.size() - 1;
            int trueLabel = code.size();
            code.add(new Instruction(OpCode.PUSH_CONST, Boolean.TRUE, lineOf(ctx)));
            int endLabel = code.size();
            patch(exitJumps, trueLabel);
            patch(jumpToFalse, endLabel);
            return null;
        }

        @Override
        public Void visitBooleanAndExpression(SceneMaxParser.BooleanAndExpressionContext ctx) {
            int count = ctx.getChildCount();
            if (count == 1) {
                ctx.getChild(0).accept(this);
                return null;
            }

            List<Integer> falseJumps = new ArrayList<Integer>();
            int i;
            for (i = 0; i < count; i += 2) {
                ctx.getChild(i).accept(this);
                code.add(new Instruction(OpCode.JUMP_IF_FALSE, Integer.valueOf(-1), lineOf(ctx)));
                falseJumps.add(Integer.valueOf(code.size() - 1));
            }
            code.add(new Instruction(OpCode.PUSH_CONST, Boolean.TRUE, lineOf(ctx)));
            code.add(new Instruction(OpCode.JUMP, Integer.valueOf(-1), lineOf(ctx)));
            int jumpToTrue = code.size() - 1;
            int falseLabel = code.size();
            code.add(new Instruction(OpCode.PUSH_CONST, Boolean.FALSE, lineOf(ctx)));
            int endLabel = code.size();
            patch(falseJumps, falseLabel);
            patch(jumpToTrue, endLabel);
            return null;
        }

        @Override
        public Void visitRelationalExpression(SceneMaxParser.RelationalExpressionContext ctx) {
            int count = ctx.getChildCount();
            if (count == 1) {
                ctx.getChild(0).accept(this);
                return null;
            }

            ctx.getChild(0).accept(this);
            int i;
            for (i = 1; i < count; i += 2) {
                String sign = ctx.getChild(i).getText();
                ctx.getChild(i + 1).accept(this);
                code.add(new Instruction(mapRelational(sign), null, lineOf(ctx)));
            }
            return null;
        }

        @Override
        public Void visitMultiplicativeExpression(SceneMaxParser.MultiplicativeExpressionContext ctx) {
            int count = ctx.getChildCount();
            if (count == 1) {
                ctx.getChild(0).accept(this);
                return null;
            }

            ctx.getChild(0).accept(this);
            int i;
            for (i = 1; i < count; i += 2) {
                String sign = ctx.getChild(i).getText();
                ctx.getChild(i + 1).accept(this);
                code.add(new Instruction(mapMultiplicative(sign), null, lineOf(ctx)));
            }
            return null;
        }

        @Override
        public Void visitAdditiveExpression(SceneMaxParser.AdditiveExpressionContext ctx) {
            int count = ctx.getChildCount();
            if (count == 1) {
                ctx.getChild(0).accept(this);
                return null;
            }

            ctx.getChild(0).accept(this);
            int i;
            for (i = 1; i < count; i += 2) {
                String sign = ctx.getChild(i).getText();
                ctx.getChild(i + 1).accept(this);
                code.add(new Instruction("+".equals(sign) ? OpCode.ADD : OpCode.SUB, null, lineOf(ctx)));
            }
            return null;
        }

        @Override
        public Void visitUnaryExpression(SceneMaxParser.UnaryExpressionContext ctx) {
            ctx.primaryExpression().accept(this);
            if (ctx.NOT() != null) {
                code.add(new Instruction(OpCode.NOT, null, lineOf(ctx)));
            }
            return null;
        }

        @Override
        public Void visitValue(SceneMaxParser.ValueContext ctx) {
            int line = lineOf(ctx);

            if (ctx.BOOLEAN() != null) {
                boolean value = ctx.BOOLEAN().getText().charAt(0) == 't' || ctx.BOOLEAN().getText().charAt(0) == 'T';
                code.add(new Instruction(OpCode.PUSH_CONST, Boolean.valueOf(value), line));
                return null;
            }

            if (ctx.number_expr() != null) {
                code.add(new Instruction(OpCode.PUSH_CONST, Double.valueOf(Double.parseDouble(ctx.getText())), line));
                return null;
            }

            if (ctx.QUOTED_STRING() != null) {
                String tmp = ctx.QUOTED_STRING().getText();
                String value = tmp.length() >= 3 ? tmp.substring(1, tmp.length() - 1) : "";
                code.add(new Instruction(OpCode.PUSH_CONST, value, line));
                return null;
            }

            if (ctx.variable_data_field() != null) {
                String var = ctx.variable_data_field().var_decl().getText();
                String field = ctx.variable_data_field().field_name().getText();
                code.add(new Instruction(OpCode.LOAD_VARIABLE_DATA_FIELD, var, field, line));
                return null;
            }

            if (ctx.variable_field() != null) {
                String var = ctx.variable_field().var_decl().getText();
                String field = ctx.variable_field().var_field().getText();
                code.add(new Instruction(OpCode.LOAD_VARIABLE_FIELD, var, field, line));
                return null;
            }

            if (ctx.function_value() != null) {
                code.add(new Instruction(OpCode.LOAD_FUNCTION_VALUE, ctx.function_value(), line));
                return null;
            }

            if (ctx.logical_expression_pointer() != null) {
                String varName = ctx.logical_expression_pointer().var_decl().getText();
                code.add(new Instruction(OpCode.LOAD_EXPR_POINTER, varName, line));
                return null;
            }

            if (ctx.fetch_array_value() != null) {
                String varName = ctx.fetch_array_value().var_decl().getText();
                CompiledProgram indexProgram = compileCached(ctx.fetch_array_value().logical_expression());
                code.add(new Instruction(OpCode.LOAD_ARRAY_VALUE, varName, indexProgram, line));
                return null;
            }

            if (ctx.get_array_length() != null) {
                String varName = ctx.get_array_length().var_decl().getText();
                code.add(new Instruction(OpCode.LOAD_ARRAY_LENGTH, varName, line));
                return null;
            }

            if (ctx.calc_distance_value() != null) {
                String obj1 = ctx.calc_distance_value().first_object().getText();
                String obj2 = ctx.calc_distance_value().second_object().getText();
                code.add(new Instruction(OpCode.LOAD_DISTANCE, obj1, obj2, line));
                return null;
            }

            if (ctx.calc_angle_value() != null) {
                String obj1 = ctx.calc_angle_value().first_object().getText();
                String obj2 = ctx.calc_angle_value().second_object().getText();
                code.add(new Instruction(OpCode.LOAD_ANGLE, obj1, obj2, line));
                return null;
            }

            if (ctx.get_json_value() != null) {
                String varName = ctx.get_json_value().var_decl().getText();
                List<JsonAccessorStep> steps = new ArrayList<JsonAccessorStep>();
                for (SceneMaxParser.Json_element_acceessorContext elem : ctx.get_json_value().json_accessor_expression().json_element_acceessor()) {
                    if (elem.json_field_accessor() != null) {
                        steps.add(JsonAccessorStep.field(elem.json_field_accessor().var_decl().getText()));
                    } else if (elem.json_array_item_accessor() != null) {
                        steps.add(JsonAccessorStep.array(compileCached(elem.json_array_item_accessor().logical_expression())));
                    }
                }
                code.add(new Instruction(OpCode.LOAD_JSON_VALUE, new JsonAccessorSpec(varName, steps), line));
                return null;
            }

            String name = ctx.getText();
            if (name.equalsIgnoreCase(NEW_LINE_STRING)) {
                code.add(new Instruction(OpCode.PUSH_CONST, "\n", line));
            } else {
                code.add(new Instruction(OpCode.LOAD_IDENTIFIER, name, line));
            }
            return null;
        }

        private OpCode mapMultiplicative(String sign) {
            if ("*".equals(sign)) {
                return OpCode.MUL;
            }
            if ("/".equals(sign)) {
                return OpCode.DIV;
            }
            return OpCode.MOD;
        }

        private OpCode mapRelational(String sign) {
            if ("==".equals(sign)) {
                return OpCode.EQ;
            }
            if ("!=".equals(sign) || "<>".equals(sign)) {
                return OpCode.NE;
            }
            if (">".equals(sign)) {
                return OpCode.GT;
            }
            if (">=".equals(sign)) {
                return OpCode.GE;
            }
            if ("<".equals(sign)) {
                return OpCode.LT;
            }
            if ("<=".equals(sign)) {
                return OpCode.LE;
            }
            if ("collides".equals(sign)) {
                return OpCode.COLLIDES;
            }
            throw new IllegalArgumentException("Unsupported relational operator: " + sign);
        }

        private void patch(List<Integer> indexes, int target) {
            for (Integer idx : indexes) {
                patch(idx.intValue(), target);
            }
        }

        private void patch(int index, int target) {
            Instruction old = code.get(index);
            code.set(index, new Instruction(old.op, Integer.valueOf(target), old.b, old.line));
        }

    }

    private static final class ExecutionEngine {
        private static final Object NULL_SENTINEL = new Object();

        Object execute(CompiledProgram program, SceneMaxScope scope) {
            return execute(program, scope, new ArrayDeque<Object>(32));
        }

        private Object execute(CompiledProgram program, SceneMaxScope scope, Deque<Object> stack) {
            List<Instruction> code = program.code;

            for (int ip = 0; ip < code.size(); ip++) {
                Instruction ins = code.get(ip);
                switch (ins.op) {
                    case PUSH_CONST:
                        pushValue(stack, normalizeNumber(ins.a));
                        break;
                    case LOAD_IDENTIFIER:
                        pushValue(stack, loadIdentifier(scope, (String) ins.a, ins.line));
                        break;
                    case LOAD_VARIABLE_FIELD:
                        pushValue(stack, loadVariableField(scope, (String) ins.a, (String) ins.b, ins.line));
                        break;
                    case LOAD_VARIABLE_DATA_FIELD:
                        pushValue(stack, loadVariableDataField(scope, (String) ins.a, (String) ins.b));
                        break;
                    case LOAD_FUNCTION_VALUE:
                        pushValue(stack, loadFunctionValue(scope, (SceneMaxParser.Function_valueContext) ins.a, ins.line));
                        break;
                    case LOAD_EXPR_POINTER:
                        pushValue(stack, loadExpressionPointer(scope, (String) ins.a, ins.line));
                        break;
                    case LOAD_ARRAY_VALUE:
                        pushValue(stack, loadArrayValue(scope, (String) ins.a, (CompiledProgram) ins.b, ins.line));
                        break;
                    case LOAD_ARRAY_LENGTH:
                        pushValue(stack, loadArrayLength(scope, (String) ins.a, ins.line));
                        break;
                    case LOAD_DISTANCE:
                        pushValue(stack, loadDistance(scope, (String) ins.a, (String) ins.b));
                        break;
                    case LOAD_ANGLE:
                        pushValue(stack, loadAngle(scope, (String) ins.a, (String) ins.b));
                        break;
                    case LOAD_JSON_VALUE:
                        pushValue(stack, loadJsonValue(scope, (JsonAccessorSpec) ins.a, ins.line));
                        break;

                    case NOT:
                        pushValue(stack, Boolean.valueOf(!requireBoolean(popValue(stack), ins.line)));
                        break;
                    case NEGATE:
                        pushValue(stack, Double.valueOf(-toDouble(popValue(stack))));
                        break;

                    case ADD: {
                        Object right = popValue(stack);
                        Object left = popValue(stack);
                        pushValue(stack, add(left, right));
                        break;
                    }
                    case SUB: {
                        Object right = popValue(stack);
                        Object left = popValue(stack);
                        pushValue(stack, Double.valueOf(toDouble(left) - toDouble(right)));
                        break;
                    }
                    case MUL: {
                        Object right = popValue(stack);
                        Object left = popValue(stack);
                        pushValue(stack, Double.valueOf(toDouble(left) * toDouble(right)));
                        break;
                    }
                    case DIV: {
                        Object right = popValue(stack);
                        Object left = popValue(stack);
                        pushValue(stack, Double.valueOf(toDouble(left) / toDouble(right)));
                        break;
                    }
                    case MOD: {
                        Object right = popValue(stack);
                        Object left = popValue(stack);
                        pushValue(stack, Double.valueOf(toDouble(left) % toDouble(right)));
                        break;
                    }

                    case EQ: {
                        Object right = popValue(stack);
                        Object left = popValue(stack);
                        pushValue(stack, Boolean.valueOf(equalsStrict(left, right, ins.line)));
                        break;
                    }
                    case NE: {
                        Object right = popValue(stack);
                        Object left = popValue(stack);
                        pushValue(stack, Boolean.valueOf(!equalsStrict(left, right, ins.line)));
                        break;
                    }
                    case LT: {
                        Object right = popValue(stack);
                        Object left = popValue(stack);
                        pushValue(stack, Boolean.valueOf(compareStrict(left, right, ins.line) < 0));
                        break;
                    }
                    case LE: {
                        Object right = popValue(stack);
                        Object left = popValue(stack);
                        pushValue(stack, Boolean.valueOf(compareStrict(left, right, ins.line) <= 0));
                        break;
                    }
                    case GT: {
                        Object right = popValue(stack);
                        Object left = popValue(stack);
                        pushValue(stack, Boolean.valueOf(compareStrict(left, right, ins.line) > 0));
                        break;
                    }
                    case GE: {
                        Object right = popValue(stack);
                        Object left = popValue(stack);
                        pushValue(stack, Boolean.valueOf(compareStrict(left, right, ins.line) >= 0));
                        break;
                    }
                    case COLLIDES: {
                        Object right = popValue(stack);
                        Object left = popValue(stack);
                        boolean v = false;
                        if (left instanceof EntityInstBase && right instanceof EntityInstBase) {
                            v = app.checkCollision((EntityInstBase) left, (EntityInstBase) right);
                        }
                        pushValue(stack, Boolean.valueOf(v));
                        break;
                    }
                    case JUMP:
                        ip = ((Integer) ins.a).intValue() - 1;
                        break;
                    case JUMP_IF_TRUE: {
                        Object v = popValue(stack);
                        if (requireBoolean(v, ins.line)) {
                            ip = ((Integer) ins.a).intValue() - 1;
                        }
                        break;
                    }
                    case JUMP_IF_FALSE: {
                        Object v = popValue(stack);
                        if (!requireBoolean(v, ins.line)) {
                            ip = ((Integer) ins.a).intValue() - 1;
                        }
                        break;
                    }
                    case RETURN: {
                        Object out = stack.isEmpty() ? null : popValue(stack);
                        return normalizeNumber(out);
                    }
                    default:
                        break;
                }
            }
            return null;
        }

        private static void pushValue(Deque<Object> stack, Object value) {
            stack.push(value == null ? NULL_SENTINEL : value);
        }

        private static Object popValue(Deque<Object> stack) {
            Object value = stack.pop();
            return value == NULL_SENTINEL ? null : value;
        }

        private static Object loadIdentifier(SceneMaxScope scope, String name, int line) {
            VarInst varInst = scope.getVar(name);
            if (varInst != null) {
                if (varInst.varType == VariableDef.VAR_TYPE_ARRAY) {
                    return varInst.values;
                }
                return normalizeNumber(varInst.value);
            }

            EntityInstBase found = scope.getEntityInst(name);
            if (found != null) {
                return found;
            }

            app.handleRuntimeError("Line: " + line + ", '" + name + "' is not a valid number or variable");
            return null;
        }

        private static Object loadVariableField(SceneMaxScope scope, String var, String field, int line) {
            RunTimeVarDef vd1 = app.findVarRuntime(null, scope, var);
            if (vd1 == null) {
                GroupInst ginst = scope.getGroup(var);
                if (ginst != null && field.equalsIgnoreCase("hit")) {
                    return ginst.lastClosestRayCheck;
                }
                return null;
            }
            return normalizeNumber(app.getFieldValue(vd1.varName, field));
        }

        private static Object loadVariableDataField(SceneMaxScope scope, String var, String field) {
            RunTimeVarDef vd1 = app.findVarRuntime(null, scope, var);
            if (vd1 == null) {
                return null;
            }
            return normalizeNumber(app.getUserDataFieldValue(vd1.varName, field));
        }

        private static Object loadFunctionValue(SceneMaxScope scope, SceneMaxParser.Function_valueContext fnCtx, int line) {
            FunctionInvoker fi = new FunctionInvoker(fnCtx, app, scope);
            if (fi.invoke()) {
                return normalizeNumber(fi.retval);
            }
            app.handleRuntimeError("Line: " + line + ", " + fi.runtimeError);
            return null;
        }

        private static Object loadExpressionPointer(SceneMaxScope scope, String varName, int line) {
            VarInst vi = scope.getVar(varName);
            if (vi == null) {
                app.handleRuntimeError("Line " + line + ": Logical expression pointer '" + varName + "' not found");
                return null;
            }
            return evaluateNested((ParserRuleContext) vi.value, scope);
        }

        private Object loadArrayValue(SceneMaxScope scope, String varName, CompiledProgram indexProgram, int line) {
            VarInst vi = scope.getVar(varName);
            if (vi == null) {
                app.handleRuntimeError("Line " + line + ": Array variable '" + varName + "' not found");
                return null;
            }
            Object indexObj = execute(indexProgram, scope, new ArrayDeque<Object>(16));
            int index = (int) toDouble(indexObj);
            if (vi.values == null || vi.values.size() <= index || index < 0) {
                app.handleRuntimeError("Line " + line + ": Array '" + varName + "' index out of bound");
                return null;
            }
            return normalizeNumber(vi.values.get(index));
        }

        private static Object loadArrayLength(SceneMaxScope scope, String varName, int line) {
            VarInst vi = scope.getVar(varName);
            if (vi == null) {
                app.handleRuntimeError("Line " + line + ": Array variable '" + varName + "' not found");
                return null;
            }
            if (vi.values == null) {
                app.handleRuntimeError("Line " + line + ": Array '" + varName + "' is not initialized");
                return null;
            }
            return Double.valueOf((double) vi.values.size());
        }

        private static Object loadDistance(SceneMaxScope scope, String obj1, String obj2) {
            RunTimeVarDef vd1 = app.findVarRuntime(null, scope, obj1);
            if (vd1 == null) {
                return null;
            }
            RunTimeVarDef vd2 = app.findVarRuntime(null, scope, obj2);
            if (vd2 == null) {
                return null;
            }
            return normalizeNumber(app.calcDistance(vd1.varName, vd2.varName));
        }

        private static Object loadAngle(SceneMaxScope scope, String obj1, String obj2) {
            RunTimeVarDef vd1 = app.findVarRuntime(null, scope, obj1);
            if (vd1 == null) {
                return null;
            }
            RunTimeVarDef vd2 = app.findVarRuntime(null, scope, obj2);
            if (vd2 == null) {
                return null;
            }
            return normalizeNumber(app.calcAngle(vd1.varName, vd2.varName));
        }

        private Object loadJsonValue(SceneMaxScope scope, JsonAccessorSpec spec, int line) {
            VarInst vi = scope.getVar(spec.varName);
            if (vi == null) {
                app.handleRuntimeError("Line " + line + ": Array variable '" + spec.varName + "' not found");
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
                app.handleRuntimeError("Line " + line + ": JSON source '" + spec.varName + "' is empty or invalid");
                return null;
            }

            for (JsonAccessorStep step : spec.steps) {
                if (!step.array) {
                    if (obj == null) {
                        app.handleRuntimeError("Line " + line + ": JSON path '" + spec.varName + "." + step.fieldName + "' is not an object");
                        return null;
                    }
                    Object fieldVal;
                    try {
                        fieldVal = obj.get(step.fieldName);
                    } catch (JSONException e) {
                        app.handleRuntimeError("Line " + line + ": JSON field '" + step.fieldName + "' not found in '" + spec.varName + "'");
                        return null;
                    }
                    if (fieldVal instanceof JSONObject) {
                        obj = (JSONObject) fieldVal;
                        objArr = null;
                    } else if (fieldVal instanceof JSONArray) {
                        objArr = (JSONArray) fieldVal;
                        obj = null;
                    } else {
                        return normalizeNumber(fieldVal);
                    }
                } else {
                    if (objArr == null) {
                        app.handleRuntimeError("Line " + line + ": JSON path '" + spec.varName + "' is not an array");
                        return null;
                    }
                    int index = (int) toDouble(execute(step.indexProgram, scope, new ArrayDeque<Object>(16)));
                    Object fieldVal;
                    try {
                        fieldVal = objArr.get(index);
                    } catch (JSONException e) {
                        app.handleRuntimeError("Line " + line + ": JSON array index out of bound for '" + spec.varName + "'");
                        return null;
                    }
                    if (fieldVal instanceof JSONObject) {
                        obj = (JSONObject) fieldVal;
                        objArr = null;
                    } else if (fieldVal instanceof JSONArray) {
                        objArr = (JSONArray) fieldVal;
                        obj = null;
                    } else {
                        return normalizeNumber(fieldVal);
                    }
                }
            }
            app.handleRuntimeError("Line " + line + ": JSON path '" + spec.varName + "' resolved to null");
            return null;
        }

        private static Object add(Object left, Object right) {
            if (left instanceof String || right instanceof String) {
                return normalizeConcat(left) + normalizeConcat(right);
            }
            if (left instanceof Boolean || right instanceof Boolean) {
                throw runtimeTypeError("Boolean values cannot participate in arithmetic addition", -1);
            }
            if (left instanceof List || right instanceof List || left instanceof EntityInstBase || right instanceof EntityInstBase) {
                return left != null ? left : right;
            }
            return Double.valueOf(toDouble(left) + toDouble(right));
        }

        private static String normalizeConcat(Object value) {
            if (value == null) {
                return "null";
            }
            String s = value.toString();
            return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
        }

        private static boolean equalsStrict(Object left, Object right, int line) {
            if (left == null || right == null) {
                return left == right;
            }
            if (left instanceof Boolean || right instanceof Boolean) {
                if (!(left instanceof Boolean) || !(right instanceof Boolean)) {
                    throw runtimeTypeError("Cannot compare boolean with non-boolean using '=='", line);
                }
                return ((Boolean) left).booleanValue() == ((Boolean) right).booleanValue();
            }
            if (left instanceof String || right instanceof String) {
                if (!(left instanceof String) || !(right instanceof String)) {
                    throw runtimeTypeError("Cannot compare string with non-string using '=='", line);
                }
                return left.equals(right);
            }
            if (left instanceof Number || right instanceof Number) {
                return toDouble(left) == toDouble(right);
            }
            return left.equals(right);
        }

        private static int compareStrict(Object left, Object right, int line) {
            if (left == null || right == null) {
                throw runtimeTypeError("Cannot compare null values", line);
            }
            if (left instanceof String || right instanceof String) {
                throw runtimeTypeError("String ordering comparisons are not supported; use equality or compare explicitly", line);
            }
            if (left instanceof Boolean || right instanceof Boolean) {
                throw runtimeTypeError("Boolean ordering comparisons are not supported", line);
            }
            double l = toDouble(left);
            double r = toDouble(right);
            return Double.compare(l, r);
        }

        private static boolean requireBoolean(Object value, int line) {
            if (value instanceof Boolean) {
                return ((Boolean) value).booleanValue();
            }
            throw runtimeTypeError("Logical operators require boolean operands", line);
        }

        private static IllegalArgumentException runtimeTypeError(String message, int line) {
            String prefix = line >= 0 ? "Line " + line + ": " : "";
            app.handleRuntimeError(prefix + message);
            return new IllegalArgumentException(prefix + message);
        }

        private static Object normalizeNumber(Object value) {
            if (value instanceof Number && !(value instanceof Double)) {
                return Double.valueOf(((Number) value).doubleValue());
            }
            return value;
        }
    }
}
