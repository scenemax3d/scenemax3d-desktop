package com.scenemaxeng.projector;

import com.abware.scenemaxlang.parser.SceneMaxLexer;
import com.abware.scenemaxlang.parser.SceneMaxParser;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.VariableDef;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ActionLogicalExpressionCompatibilityHarnessTest {

    private static StubSceneMaxApp app;

    @BeforeClass
    public static void setUpApp() {
        app = new StubSceneMaxApp();
        ActionLogicalExpression.setApp(app);
        ActionLogicalExpressionVm.setApp(app);
    }

    @Test
    public void conventionalSubsetStillMatchesLegacy() {
        List<String> expressions = Arrays.asList(
                "1 + 2 * 3",
                "\"a\" + 2",
                "1 == 1",
                "1 != 2",
                "1 == 1 && 2 == 2",
                "1 == 0 || 2 == 2",
                "num + whole",
                "text + num",
                "jsonNumber == 7",
                "jsonText == \"hello\""
        );

        SceneMaxScope scope = createScope();
        List<String> mismatches = new ArrayList<String>();

        for (String expression : expressions) {
            EvaluationPair pair = evaluateBoth(expression, scope);
            if (!pair.sameOutcome()) {
                mismatches.add(pair.describe(expression));
            }
        }

        assertTrue("Expected matching behavior for the conventional subset, mismatches:\n" + join(mismatches), mismatches.isEmpty());
    }

    @Test
    public void vmUsesStrictBooleanAndComparisonSemantics() {
        SceneMaxScope scope = createScope();

        assertVmValue("1 == 0 || 2 == 2", scope, "true");
        assertVmValue("1 == 1 && 2 == 2", scope, "true");
        assertVmValue("!(1 == 0)", scope, "true");
        assertVmValue("(1 == 1) != (1 == 0)", scope, "true");

        assertVmTypeError("\"a\" > \"b\"", scope, "String ordering comparisons are not supported");
        assertVmTypeError("\"b\" < \"a\"", scope, "String ordering comparisons are not supported");
        assertVmTypeError("!1", scope, "Logical operators require boolean operands");
        assertVmTypeError("1 || 0", scope, "Logical operators require boolean operands");
        assertVmTypeError("0 && 2", scope, "Logical operators require boolean operands");
        assertVmTypeError("1 + (1 == 1)", scope, "Boolean values cannot participate in arithmetic addition");
        assertVmTypeError("group.hit || 0", scope, "Logical operators require boolean operands");
        assertVmTypeError("(1 == 1) == 1", scope, "Cannot compare boolean with non-boolean using '=='");
        assertVmTypeError("\"x\" == 1", scope, "Cannot compare string with non-string using '=='");
        assertVmTypeError("1 == \"x\"", scope, "Cannot compare string with non-string using '=='");
        assertVmTypeError("group.hit && (1 == 1)", scope, "Logical operators require boolean operands");
    }

    @Test
    public void vmHandlesArithmeticPrecedenceAndStringConcat() {
        SceneMaxScope scope = createScope();

        assertVmValue("1 + 2 * 3", scope, "7.0");
        assertVmValue("(1 + 2) * 3", scope, "9.0");
        assertVmValue("10 / 2 + 3", scope, "8.0");
        assertVmValue("10 % 3", scope, "1.0");
        assertVmValue("num + whole * 2", scope, "10.5");
        assertVmValue("\"x\" + 2 + 3", scope, "x23");
        assertVmValue("\"x\" + (2 + 3)", scope, "x5");
        assertVmValue("\"score: \" + 10", scope, "score: 10");
        assertVmValue("\"score: \" + floor(10.9)", scope, "score: 10");
        assertVmValue("newline + \"x\"", scope, "\nx");
    }

    @Test
    public void stringFormattingHelperTrimsOnlyWholeNumberFractions() {
        assertEquals("10", ActionLogicalExpressionVm.formatValueForStringContext(Double.valueOf(10.0)));
        assertEquals("10.5", ActionLogicalExpressionVm.formatValueForStringContext(Double.valueOf(10.5)));
        assertEquals("10", ActionLogicalExpressionVm.formatValueForStringContext(Integer.valueOf(10)));
    }

    @Test
    public void vmHandlesArraysJsonAndExpressionPointers() {
        SceneMaxScope scope = createScope();

        assertVmValue("nums[0]", scope, "10.0");
        assertVmValue("nums[1 + 1]", scope, "30.0");
        assertVmValue("nums[@exprIndex]", scope, "20.0");
        assertVmValue("nums.Length", scope, "3.0");
        assertVmValue("words[0] + words[1]", scope, "alphabeta");

        assertVmValue("JSON(jsonDoc).a", scope, "7.0");
        assertVmValue("JSON(jsonDoc).arr[1]", scope, "6.0");
        assertVmValue("JSON(jsonDoc).arr[@exprIndex]", scope, "6.0");
        assertVmValue("JSON(jsonDoc).nested.n", scope, "9.0");
        assertVmValue("JSON(jsonDoc).text", scope, "hello");

        assertVmValue("@exprTrue", scope, "true");
        assertVmValue("@exprFalse", scope, "false");
        assertVmValue("@exprNested", scope, "true");
    }

    @Test
    public void vmHandlesNullResultsFromNestedExpressionPointersWithoutCrashing() {
        SceneMaxScope scope = createScope();

        EvaluationResult vm = evaluateVm(parseExpression("@exprIndirectMissing"), scope);
        assertEquals("VM should not throw when a nested expression pointer evaluates to null", "", vm.exception);
        assertTrue("VM should report the lookup failure as a runtime error instead of crashing: " + vm,
                vm.error.contains("missingExpr"));
        assertEquals("Null nested expression results should propagate as null", "null", vm.value);
    }

    @Test
    public void vmHandlesObjectsAndIdentityEquality() {
        SceneMaxScope scope = createScope();

        assertVmValue("player == player", scope, "true");
        assertVmValue("group.hit == group.hit", scope, "true");
        assertVmValue("player != enemy", scope, "true");
        assertVmValue("group.hit == player", scope, "false");
        assertVmValue("Distance(player,enemy)", scope, "12.5");
        assertVmValue("Angle(player,enemy)", scope, "45.0");
    }

    @Test
    public void vmHandlesBuiltInFunctionsAndUserData() {
        SceneMaxScope scope = createScope();

        assertVmValue("abs(-5)", scope, "5.0");
        assertVmValue("floor(2.9)", scope, "2.0");
        assertVmValue("ceil(2.1)", scope, "3.0");
        assertVmValue("round(2.6)", scope, "3.0");
        assertVmValue("player.Data.score", scope, "99.0");
        assertVmValue("player.Data.title", scope, "hero");
        assertVmValue("player.Data.score == 99", scope, "true");
        assertVmValue("player.Data.title == \"hero\"", scope, "true");
    }

    @Test
    public void vmReportsRuntimeErrorsForInvalidLookups() {
        SceneMaxScope scope = createScope();

        assertVmRuntimeError("nums[99]", scope, "index out of bound");
        assertVmRuntimeError("missingVar", scope, "is not a valid number or variable");
        assertVmRuntimeError("@missingExpr", scope, "Logical expression pointer 'missingExpr' not found");
        assertVmRuntimeError("missingArray.Length", scope, "Array variable 'missingArray' not found");
        assertVmRuntimeError("JSON(jsonDoc).missing[0]", scope, ""); // should fail safely, null path
        assertVmRuntimeError("round()", scope, "expecting argument");
    }

    @Test
    public void vmShortCircuitsBooleanExpressions() {
        SceneMaxScope scope = createScope();

        assertVmValue("(1 == 1) || nums[99] == 0", scope, "true");
        assertVmValue("(1 == 0) && nums[99] == 0", scope, "false");
        assertVmValue("(1 == 1) || @missingExpr", scope, "true");
        assertVmValue("(1 == 0) && @missingExpr", scope, "false");
        assertVmValue("(1 == 1) || (\"a\" > \"b\")", scope, "true");
        assertVmValue("(1 == 0) && (\"a\" > \"b\")", scope, "false");
    }

    @Test
    public void vmMatchesLegacyOnBroaderValidSubset() {
        List<String> expressions = Arrays.asList(
                "1 + 2 * 3 == 7",
                "\"x\" + 2 == \"x2\"",
                "nums[1] == 20",
                "nums[@exprIndex] == 20",
                "nums.Length == 3",
                "JSON(jsonDoc).a == 7",
                "JSON(jsonDoc).arr[1] == 6",
                "round(2.6) == 3",
                "player.Data.score == 99",
                "(1 == 1) && (2 == 2)",
                "(1 == 0) || (2 == 2)"
        );

        SceneMaxScope scope = createScope();
        List<String> mismatches = new ArrayList<String>();

        for (String expression : expressions) {
            EvaluationPair pair = evaluateBoth(expression, scope);
            if (!pair.sameOutcome()) {
                mismatches.add(pair.describe(expression));
            }
        }

        assertTrue("Expected matching behavior for the broader valid subset, mismatches:\n" + join(mismatches), mismatches.isEmpty());
    }

    @Test
    public void evaluationModesBehaveAsIntended() {
        SceneMaxScope scope = createScope();
        ActionLogicalExpression.EvaluationMode previous = ActionLogicalExpression.getEvaluationMode();
        try {
            ActionLogicalExpression.setEvaluationMode(ActionLogicalExpression.EvaluationMode.LEGACY);
            assertLegacyValue("1 || 0", scope, "0.0");

            ActionLogicalExpression.setEvaluationMode(ActionLogicalExpression.EvaluationMode.VM);
            EvaluationResult vm = evaluateViaWrapper("1 || 0", scope);
            assertTrue("VM mode should reject non-boolean logical expressions but got " + vm, !vm.exception.isEmpty());

            long mismatchStart = ActionLogicalExpression.getDualModeMismatchCount();
            long exceptionStart = ActionLogicalExpression.getDualModeExceptionCount();
            ActionLogicalExpression.setEvaluationMode(ActionLogicalExpression.EvaluationMode.DUAL);
            EvaluationResult dual = evaluateViaWrapper("1 || 0", scope);
            assertEquals("Dual mode should preserve legacy result", "0.0", dual.value);
            assertEquals("Dual mode should preserve legacy error state", "", dual.error);
            assertEquals("Dual mode should preserve legacy exception state", "", dual.exception);
            assertTrue("Dual mode should record a diagnostic for strict VM divergence",
                    ActionLogicalExpression.getDualModeMismatchCount() > mismatchStart
                            || ActionLogicalExpression.getDualModeExceptionCount() > exceptionStart);
        } finally {
            ActionLogicalExpression.setEvaluationMode(previous);
        }
    }

    private EvaluationPair evaluateBoth(String expression, SceneMaxScope scope) {
        ParserRuleContext ctx = parseExpression(expression);
        EvaluationResult legacy = evaluateLegacy(ctx, scope);
        EvaluationResult vm = evaluateVm(ctx, scope);
        return new EvaluationPair(legacy, vm);
    }

    private EvaluationResult evaluateLegacy(ParserRuleContext ctx, SceneMaxScope scope) {
        app.reset();
        try {
            Object value = new ActionLogicalExpression(ctx, scope).evaluate();
            return EvaluationResult.value(value, app.runTimeError);
        } catch (Throwable t) {
            return EvaluationResult.exception(t, app.runTimeError);
        }
    }

    private EvaluationResult evaluateVm(ParserRuleContext ctx, SceneMaxScope scope) {
        app.reset();
        try {
            Object value = new ActionLogicalExpressionVm(ctx, scope).evaluate();
            return EvaluationResult.value(value, app.runTimeError);
        } catch (Throwable t) {
            return EvaluationResult.exception(t, app.runTimeError);
        }
    }

    private EvaluationResult evaluateViaWrapper(String expression, SceneMaxScope scope) {
        ParserRuleContext ctx = parseExpression(expression);
        app.reset();
        try {
            Object value = new ActionLogicalExpression(ctx, scope).evaluate();
            return EvaluationResult.value(value, app.runTimeError);
        } catch (Throwable t) {
            return EvaluationResult.exception(t, app.runTimeError);
        }
    }

    private void assertVmValue(String expression, SceneMaxScope scope, String expectedValue) {
        EvaluationResult vm = evaluateVm(parseExpression(expression), scope);
        assertEquals("Expected successful VM evaluation for " + expression, "", vm.exception);
        assertEquals("Expected no runtime error for " + expression, "", vm.error);
        assertEquals("Unexpected VM value for " + expression, expectedValue, vm.value);
    }

    private void assertLegacyValue(String expression, SceneMaxScope scope, String expectedValue) {
        EvaluationResult legacy = evaluateLegacy(parseExpression(expression), scope);
        assertEquals("Expected successful legacy evaluation for " + expression, "", legacy.exception);
        assertEquals("Expected no runtime error for " + expression, "", legacy.error);
        assertEquals("Unexpected legacy value for " + expression, expectedValue, legacy.value);
    }

    private void assertVmTypeError(String expression, SceneMaxScope scope, String expectedMessagePart) {
        EvaluationResult vm = evaluateVm(parseExpression(expression), scope);
        assertTrue("Expected VM to reject expression " + expression + " but got " + vm, !vm.exception.isEmpty());
        assertTrue("Expected runtime error to mention '" + expectedMessagePart + "' but got " + vm,
                vm.error.contains(expectedMessagePart) || vm.exception.contains(expectedMessagePart));
    }

    private void assertVmRuntimeError(String expression, SceneMaxScope scope, String expectedMessagePart) {
        EvaluationResult vm = evaluateVm(parseExpression(expression), scope);
        assertTrue("Expected VM runtime error for " + expression + " but got " + vm,
                !vm.error.isEmpty() || !vm.exception.isEmpty());
        if (!expectedMessagePart.isEmpty()) {
            assertTrue("Expected runtime error to mention '" + expectedMessagePart + "' but got " + vm,
                    vm.error.contains(expectedMessagePart) || vm.exception.contains(expectedMessagePart));
        }
    }

    private static SceneMaxParser.Logical_expressionContext parseExpression(String expression) {
        SceneMaxLexer lexer = new SceneMaxLexer(new ANTLRInputStream(expression));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        SceneMaxParser parser = new SceneMaxParser(tokens);

        final List<String> errors = new ArrayList<String>();
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                errors.add("line " + line + ":" + charPositionInLine + " " + msg);
            }
        });

        SceneMaxParser.Logical_expressionContext ctx = parser.logical_expression();
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Failed to parse expression '" + expression + "': " + errors.get(0));
        }
        return ctx;
    }

    private static SceneMaxScope createScope() {
        SceneMaxScope scope = new SceneMaxScope();

        addVar(scope, "num", VariableDef.VAR_TYPE_NUMBER, Double.valueOf(2.5));
        addVar(scope, "whole", VariableDef.VAR_TYPE_NUMBER, Integer.valueOf(4));
        addVar(scope, "text", VariableDef.VAR_TYPE_STRING, "value");
        addVar(scope, "truth", VariableDef.VAR_TYPE_NUMBER, Double.valueOf(1.0));
        addVar(scope, "zero", VariableDef.VAR_TYPE_NUMBER, Double.valueOf(0.0));
        addVar(scope, "jsonNumber", VariableDef.VAR_TYPE_NUMBER, Long.valueOf(7));
        addVar(scope, "jsonText", VariableDef.VAR_TYPE_STRING, "hello");
        addVar(scope, "jsonDoc", VariableDef.VAR_TYPE_STRING, "{\"a\":7,\"text\":\"hello\",\"arr\":[5,6],\"nested\":{\"n\":9}}");

        addArrayVar(scope, "nums", Arrays.<Object>asList(Double.valueOf(10.0), Integer.valueOf(20), Long.valueOf(30)));
        addArrayVar(scope, "words", Arrays.<Object>asList("alpha", "beta"));

        addExprPointerVar(scope, "exprTrue", "1 == 1");
        addExprPointerVar(scope, "exprFalse", "1 == 0");
        addExprPointerVar(scope, "exprNested", "@exprTrue && nums[1] == 20");
        addExprPointerVar(scope, "exprIndex", "1");
        addExprPointerVar(scope, "exprIndirectMissing", "@missingExpr");

        addEntity(scope, "player");
        addEntity(scope, "enemy");

        GroupInst group = new GroupInst(new com.scenemaxeng.compiler.GroupDef("group"), scope, null);
        group.lastClosestRayCheck = entity(scope, "player");
        scope.groups.put("group", group);

        return scope;
    }

    private static void addVar(SceneMaxScope scope, String name, int varType, Object value) {
        VariableDef vd = new VariableDef();
        vd.varName = name;
        vd.varType = varType;

        VarInst vi = new VarInst(vd, scope);
        vi.varType = varType;
        vi.value = value;
        scope.vars_index.put(name, vi);
    }

    private static void addArrayVar(SceneMaxScope scope, String name, List<Object> values) {
        VariableDef vd = new VariableDef();
        vd.varName = name;
        vd.varType = VariableDef.VAR_TYPE_ARRAY;

        VarInst vi = new VarInst(vd, scope);
        vi.varType = VariableDef.VAR_TYPE_ARRAY;
        vi.values = values;
        scope.vars_index.put(name, vi);
    }

    private static void addExprPointerVar(SceneMaxScope scope, String name, String expression) {
        VariableDef vd = new VariableDef();
        vd.varName = name;
        vd.varType = VariableDef.VAR_TYPE_EXPR_POINTER;

        VarInst vi = new VarInst(vd, scope);
        vi.varType = VariableDef.VAR_TYPE_EXPR_POINTER;
        vi.value = parseExpression(expression);
        scope.vars_index.put(name, vi);
    }

    private static void addEntity(SceneMaxScope scope, String name) {
        scope.entities.put(name, entity(scope, name));
    }

    private static EntityInstBase entity(SceneMaxScope scope, String name) {
        EntityInstBase entity = new EntityInstBase();
        VariableDef vd = new VariableDef();
        vd.varName = name;
        vd.varType = VariableDef.VAR_TYPE_OBJECT;
        entity.varDef = vd;
        entity.scope = scope;
        return entity;
    }

    private static String join(List<String> lines) {
        if (lines.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static final class EvaluationPair {
        private final EvaluationResult legacy;
        private final EvaluationResult vm;

        private EvaluationPair(EvaluationResult legacy, EvaluationResult vm) {
            this.legacy = legacy;
            this.vm = vm;
        }

        boolean sameOutcome() {
            return legacy.equals(vm);
        }

        String describe(String expression) {
            return expression + "\nlegacy=" + legacy + "\nvm=" + vm;
        }
    }

    private static final class EvaluationResult {
        private final String value;
        private final String error;
        private final String exception;

        private EvaluationResult(String value, String error, String exception) {
            this.value = value;
            this.error = error == null ? "" : error;
            this.exception = exception == null ? "" : exception;
        }

        static EvaluationResult value(Object value, String error) {
            return new EvaluationResult(String.valueOf(value), error, "");
        }

        static EvaluationResult exception(Throwable t, String error) {
            return new EvaluationResult("", error, t.getClass().getSimpleName() + ": " + t.getMessage());
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof EvaluationResult)) {
                return false;
            }
            EvaluationResult rhs = (EvaluationResult) other;
            return value.equals(rhs.value) && error.equals(rhs.error) && exception.equals(rhs.exception);
        }

        @Override
        public int hashCode() {
            return value.hashCode() * 31 + error.hashCode() * 17 + exception.hashCode();
        }

        @Override
        public String toString() {
            return "{value=" + value + ", error=" + error + ", exception=" + exception + "}";
        }
    }

    private static class StubSceneMaxApp extends SceneMaxApp {
        String runTimeError;

        void reset() {
            runTimeError = null;
        }

        @Override
        public void handleRuntimeError(String err) {
            this.runTimeError = err;
        }

        @Override
        public Object getFieldValue(String varName, String fieldName) {
            if ("group".equals(varName) && "hit".equalsIgnoreCase(fieldName)) {
                return new EntityInstBase();
            }
            return Integer.valueOf(0);
        }

        @Override
        public Object getUserDataFieldValue(String varName, String fieldName) {
            if ("player@".equals(varName.substring(0, Math.min(varName.length(), 7))) || "player".equals(varName)) {
                if ("score".equalsIgnoreCase(fieldName)) {
                    return Integer.valueOf(99);
                }
                if ("title".equalsIgnoreCase(fieldName)) {
                    return "hero";
                }
            }
            return Integer.valueOf(0);
        }

        @Override
        public RunTimeVarDef findVarRuntime(ProgramDef prg, SceneMaxScope scope, String varName) {
            EntityInstBase entity = scope.getEntityInst(varName);
            if (entity == null) {
                return null;
            }

            RunTimeVarDef vd = new RunTimeVarDef(entity.varDef);
            vd.varName = entity.getVarRunTimeName();
            return vd;
        }

        @Override
        public boolean checkCollision(EntityInstBase obj1, EntityInstBase obj2) {
            return false;
        }

        @Override
        public Object calcDistance(String varName1, String varName2) {
            return Double.valueOf(12.5);
        }

        @Override
        public Object calcAngle(String varName1, String varName2) {
            return Double.valueOf(45.0);
        }
    }
}
