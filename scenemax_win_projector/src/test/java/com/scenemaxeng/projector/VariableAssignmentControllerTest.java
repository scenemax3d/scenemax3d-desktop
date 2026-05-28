package com.scenemaxeng.projector;

import com.abware.scenemaxlang.parser.SceneMaxLexer;
import com.abware.scenemaxlang.parser.SceneMaxParser;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.VariableAssignmentCommand;
import com.scenemaxeng.compiler.VariableDef;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VariableAssignmentControllerTest {

    @Test
    public void assignsVarWithoutDeclarationMetadata() {
        SceneMaxScope scope = new SceneMaxScope();

        VariableDef varDef = new VariableDef();
        varDef.varName = "x";

        VarInst varInst = new VarInst(varDef, scope);
        scope.vars_index.put("x", varInst);

        VariableAssignmentCommand cmd = new VariableAssignmentCommand();
        cmd.vars.add(varDef);
        cmd.values.add(parseExpression("5"));

        VariableAssignmentController controller = new VariableAssignmentController(null, scope, new ProgramDef(), cmd);
        controller.run(0f);

        assertEquals(5d, ((Double) varInst.value).doubleValue(), 0.0);
    }

    @Test
    public void arraySetOutOfBoundsReportsRuntimeErrorWithoutThrowing() {
        CapturingSceneMaxApp app = new CapturingSceneMaxApp();
        SceneMaxScope scope = new SceneMaxScope();

        VariableDef varDef = new VariableDef();
        varDef.varName = "items";
        varDef.varType = VariableDef.VAR_TYPE_ARRAY;

        VarInst varInst = new VarInst(varDef, scope);
        varInst.varType = VariableDef.VAR_TYPE_ARRAY;
        varInst.values = new ArrayList<Object>(Arrays.<Object>asList(0d, 1d, 2d, 3d, 4d));
        scope.vars_index.put("items", varInst);

        VariableAssignmentCommand cmd = new VariableAssignmentCommand();
        cmd.vars.add(varDef);
        cmd.arrayIndexes.put(varDef, parseExpression("\n\n5"));
        cmd.values.add(parseExpression("99"));

        VariableAssignmentController controller = new VariableAssignmentController(app, scope, new ProgramDef(), cmd);

        assertTrue(controller.run(0f));
        assertEquals(Arrays.<Object>asList(0d, 1d, 2d, 3d, 4d), varInst.values);
        assertEquals("Line 3: Array 'items' index 5 out of bounds for length 5", app.lastRuntimeError);
    }

    private SceneMaxParser.Logical_expressionContext parseExpression(String expr) {
        SceneMaxLexer lexer = new SceneMaxLexer(new ANTLRInputStream(expr));
        SceneMaxParser parser = new SceneMaxParser(new CommonTokenStream(lexer));
        return parser.logical_expression();
    }

    private static class CapturingSceneMaxApp extends SceneMaxApp {
        String lastRuntimeError;

        @Override
        public void handleRuntimeError(String err) {
            lastRuntimeError = err;
        }
    }
}
