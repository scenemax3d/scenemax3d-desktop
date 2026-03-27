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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertTrue;

public class ActionLogicalExpressionBenchmarkTest {

    private static final int WARMUP_ITERATIONS = 20_000;
    private static final int MEASURE_ITERATIONS = 100_000;
    private static final int ROUNDS = 5;

    private static StubSceneMaxApp app;

    @BeforeClass
    public static void setUpApp() {
        app = new StubSceneMaxApp();
        ActionLogicalExpression.setApp(app);
        ActionLogicalExpressionVm.setApp(app);
    }

    @Test
    public void benchmarkLegacyVsVm() throws Exception {
        SceneMaxScope scope = createScope();

        List<BenchmarkCase> cases = Arrays.asList(
                new BenchmarkCase("arithmetic", "num + whole * 2 - 3 / 1"),
                new BenchmarkCase("boolean", "(num + 1 == 3.5) && (whole == 4)"),
                new BenchmarkCase("string_concat", "\"x\" + words[0] + nums[0]"),
                new BenchmarkCase("array_json", "nums[@exprIndex] + JSON(jsonDoc).arr[@exprIndex]"),
                new BenchmarkCase("pointer_bool", "@exprNested")
        );

        StringBuilder report = new StringBuilder();
        report.append("ActionLogicalExpression benchmark\n");
        report.append("warmup_iterations=").append(WARMUP_ITERATIONS).append('\n');
        report.append("measure_iterations=").append(MEASURE_ITERATIONS).append('\n');
        report.append("rounds=").append(ROUNDS).append("\n\n");

        for (BenchmarkCase c : cases) {
            Measurement cold = measureCold(c.expression, scope);
            Measurement hot = measureHot(c.expression, scope);

            report.append("CASE ").append(c.name).append('\n');
            report.append("expression=").append(c.expression).append('\n');
            report.append(formatMeasurement("cold_first_eval_ns", cold));
            report.append(formatMeasurement("hot_ns_per_op", hot));
            report.append('\n');
        }

        Path outDir = Paths.get("build", "benchmark-results");
        Files.createDirectories(outDir);
        Path outFile = outDir.resolve("action-logical-expression-benchmark.txt");
        Files.write(outFile, report.toString().getBytes(StandardCharsets.UTF_8));

        assertTrue("Benchmark report should have been written", Files.exists(outFile));
    }

    private static Measurement measureCold(String expression, SceneMaxScope scope) {
        List<Long> legacyTimes = new ArrayList<Long>();
        List<Long> vmTimes = new ArrayList<Long>();
        long checksum = 0;

        for (int i = 0; i < ROUNDS; i++) {
            ParserRuleContext legacyCtx = parseExpression(expression);
            long startLegacy = System.nanoTime();
            Object legacy = new ActionLogicalExpression(legacyCtx, scope).evaluate();
            legacyTimes.add(Long.valueOf(System.nanoTime() - startLegacy));
            checksum += blackhole(legacy);

            ParserRuleContext vmCtx = parseExpression(expression);
            long startVm = System.nanoTime();
            Object vm = new ActionLogicalExpressionVm(vmCtx, scope).evaluate();
            vmTimes.add(Long.valueOf(System.nanoTime() - startVm));
            checksum += blackhole(vm);
        }

        return new Measurement(legacyTimes, vmTimes, checksum);
    }

    private static Measurement measureHot(String expression, SceneMaxScope scope) {
        ParserRuleContext legacyCtx = parseExpression(expression);
        ParserRuleContext vmCtx = parseExpression(expression);

        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            blackhole(new ActionLogicalExpression(legacyCtx, scope).evaluate());
            blackhole(new ActionLogicalExpressionVm(vmCtx, scope).evaluate());
        }

        List<Long> legacyTimes = new ArrayList<Long>();
        List<Long> vmTimes = new ArrayList<Long>();
        long checksum = 0;

        for (int round = 0; round < ROUNDS; round++) {
            long startLegacy = System.nanoTime();
            for (int i = 0; i < MEASURE_ITERATIONS; i++) {
                checksum += blackhole(new ActionLogicalExpression(legacyCtx, scope).evaluate());
            }
            legacyTimes.add(Long.valueOf(System.nanoTime() - startLegacy));

            long startVm = System.nanoTime();
            for (int i = 0; i < MEASURE_ITERATIONS; i++) {
                checksum += blackhole(new ActionLogicalExpressionVm(vmCtx, scope).evaluate());
            }
            vmTimes.add(Long.valueOf(System.nanoTime() - startVm));
        }

        return new Measurement(legacyTimes, vmTimes, checksum, MEASURE_ITERATIONS);
    }

    private static String formatMeasurement(String label, Measurement measurement) {
        double legacyMedian = measurement.legacyMedian();
        double vmMedian = measurement.vmMedian();
        double speedup = vmMedian == 0.0 ? 0.0 : legacyMedian / vmMedian;

        return String.format(Locale.US,
                "%s: legacy=%.1f vm=%.1f speedup=%.2fx checksum=%d%n",
                label, legacyMedian, vmMedian, speedup, measurement.checksum);
    }

    private static long blackhole(Object value) {
        if (value == null) {
            return 1;
        }
        return value.toString().hashCode();
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
        addVar(scope, "jsonDoc", VariableDef.VAR_TYPE_STRING, "{\"a\":7,\"text\":\"hello\",\"arr\":[5,6],\"nested\":{\"n\":9}}");
        addArrayVar(scope, "nums", Arrays.<Object>asList(Double.valueOf(10.0), Integer.valueOf(20), Long.valueOf(30)));
        addArrayVar(scope, "words", Arrays.<Object>asList("alpha", "beta"));
        addExprPointerVar(scope, "exprTrue", "1 == 1");
        addExprPointerVar(scope, "exprIndex", "1");
        addExprPointerVar(scope, "exprNested", "@exprTrue && nums[1] == 20");

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

    private static final class BenchmarkCase {
        final String name;
        final String expression;

        BenchmarkCase(String name, String expression) {
            this.name = name;
            this.expression = expression;
        }
    }

    private static final class Measurement {
        final List<Long> legacyTimes;
        final List<Long> vmTimes;
        final long checksum;
        final int divisor;

        Measurement(List<Long> legacyTimes, List<Long> vmTimes, long checksum) {
            this(legacyTimes, vmTimes, checksum, 1);
        }

        Measurement(List<Long> legacyTimes, List<Long> vmTimes, long checksum, int divisor) {
            this.legacyTimes = legacyTimes;
            this.vmTimes = vmTimes;
            this.checksum = checksum;
            this.divisor = divisor;
        }

        double legacyMedian() {
            return medianPerOp(legacyTimes, divisor);
        }

        double vmMedian() {
            return medianPerOp(vmTimes, divisor);
        }

        private static double medianPerOp(List<Long> values, int divisor) {
            List<Long> copy = new ArrayList<Long>(values);
            copy.sort(null);
            long median = copy.get(copy.size() / 2).longValue();
            return ((double) median) / divisor;
        }
    }

    private static class StubSceneMaxApp extends SceneMaxApp {
        private final Map<String, Map<String, Object>> userData = new LinkedHashMap<String, Map<String, Object>>();

        StubSceneMaxApp() {
            Map<String, Object> playerData = new LinkedHashMap<String, Object>();
            playerData.put("score", Integer.valueOf(99));
            playerData.put("title", "hero");
            userData.put("player", playerData);
        }

        @Override
        public void handleRuntimeError(String err) {
            // Benchmarks intentionally ignore runtime-error bookkeeping cost.
        }

        @Override
        public Object getFieldValue(String varName, String fieldName) {
            if (varName.startsWith("group") && "hit".equalsIgnoreCase(fieldName)) {
                return new EntityInstBase();
            }
            return Integer.valueOf(0);
        }

        @Override
        public Object getUserDataFieldValue(String varName, String fieldName) {
            String normalized = varName.contains("@") ? varName.substring(0, varName.indexOf('@')) : varName;
            Map<String, Object> data = userData.get(normalized);
            return data != null && data.containsKey(fieldName) ? data.get(fieldName) : Integer.valueOf(0);
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
