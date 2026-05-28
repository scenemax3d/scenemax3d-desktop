package com.scenemaxeng.projector;

import com.abware.scenemaxlang.parser.SceneMaxLexer;
import com.abware.scenemaxlang.parser.SceneMaxParser;
import com.scenemaxeng.compiler.DoBlockCommand;
import com.scenemaxeng.compiler.FunctionBlockDef;
import com.scenemaxeng.compiler.FunctionInvocationCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.VariableDef;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FunctionInvocationParameterCaptureTest {

    @Test
    public void asyncFunctionInvocationCapturesPrimitiveParameterAtControllerCreation() {
        SceneMaxApp app = new SceneMaxApp();
        ProgramDef program = programWithSingleParamFunction("launch", "slot");
        SceneMaxScope scope = scopeWithNumber("index", 0d);

        FunctionInvocationCommand command = new FunctionInvocationCommand();
        command.funcName = "launch";
        command.params.add(parseExpression("index"));
        command.isAsync = true;

        SceneMaxBaseController controller = app.runFunctionInvocationCommand(program, scope, command);
        scope.getVar("index").value = 5d;

        assertTrue(controller instanceof DoBlockController);
        DoBlockController doBlock = (DoBlockController) controller;
        Object captured = doBlock.funcScopeParams.get("slot");

        assertTrue(captured instanceof VarInst);
        assertEquals(0d, ((Double) ((VarInst) captured).value).doubleValue(), 0.0);
    }

    @Test
    public void synchronousFunctionInvocationKeepsLazyParameterEvaluation() {
        SceneMaxApp app = new SceneMaxApp();
        ProgramDef program = programWithSingleParamFunction("launch", "slot");
        SceneMaxScope scope = scopeWithNumber("index", 0d);

        FunctionInvocationCommand command = new FunctionInvocationCommand();
        command.funcName = "launch";
        command.params.add(parseExpression("index"));

        SceneMaxBaseController controller = app.runFunctionInvocationCommand(program, scope, command);

        assertTrue(controller instanceof DoBlockController);
        assertNull(((DoBlockController) controller).funcScopeParams);
    }

    private ProgramDef programWithSingleParamFunction(String functionName, String paramName) {
        ProgramDef program = new ProgramDef();
        FunctionBlockDef function = new FunctionBlockDef();
        function.name = functionName;
        function.doBlock = new DoBlockCommand();
        function.doBlock.inParams = Collections.singletonList(paramName);
        function.doBlock.prg = new ProgramDef();
        program.functions.put(functionName, function);
        return program;
    }

    private SceneMaxScope scopeWithNumber(String name, Double value) {
        SceneMaxScope scope = new SceneMaxScope();
        VariableDef varDef = new VariableDef();
        varDef.varName = name;
        varDef.varType = VariableDef.VAR_TYPE_NUMBER;
        VarInst varInst = new VarInst(varDef, scope);
        varInst.varType = VariableDef.VAR_TYPE_NUMBER;
        varInst.value = value;
        scope.vars_index.put(name, varInst);
        return scope;
    }

    private SceneMaxParser.Logical_expressionContext parseExpression(String expr) {
        SceneMaxLexer lexer = new SceneMaxLexer(new ANTLRInputStream(expr));
        SceneMaxParser parser = new SceneMaxParser(new CommonTokenStream(lexer));
        return parser.logical_expression();
    }
}
