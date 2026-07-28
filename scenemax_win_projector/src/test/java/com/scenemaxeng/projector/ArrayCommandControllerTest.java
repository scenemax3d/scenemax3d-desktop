package com.scenemaxeng.projector;

import com.abware.scenemaxlang.parser.SceneMaxLexer;
import com.abware.scenemaxlang.parser.SceneMaxParser;
import com.scenemaxeng.compiler.ArrayCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import com.scenemaxeng.compiler.VariableDef;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ArrayCommandControllerTest {

    @Test
    public void parsesArrayResetCommand() {
        ProgramDef program = new SceneMaxLanguageParser(null, "")
                .parse("var recorded_actions = [1, 2, 3]\nrecorded_actions.reset(0)");

        assertTrue(program.syntaxErrors.toString(), program.syntaxErrors.isEmpty());
        ArrayCommand command = (ArrayCommand) program.actions.get(1);
        assertEquals(ArrayCommand.ArrayAction.Reset, command.action);
        assertEquals("recorded_actions", command.varName);
    }

    @Test
    public void clearsArrayAndSyncsNetworkState() {
        CapturingSceneMaxApp app = new CapturingSceneMaxApp();
        SceneMaxScope scope = new SceneMaxScope();
        VariableDef varDef = networkArrayVar("recorded_actions");
        VarInst varInst = arrayInst(varDef, scope, 1d, 2d, 3d);

        ArrayCommand command = new ArrayCommand();
        command.action = ArrayCommand.ArrayAction.Clear;
        command.varName = "recorded_actions";

        ArrayCommandController controller = new ArrayCommandController(app, new ProgramDef(), scope, command);
        assertTrue(controller.run(0f));

        assertTrue(varInst.values.isEmpty());
        assertEquals("recorded_actions", app.syncedVarName);
        assertEquals(new ArrayList<Object>(), app.syncedValue);
    }

    @Test
    public void resetsArrayValuesAndSyncsNetworkState() {
        CapturingSceneMaxApp app = new CapturingSceneMaxApp();
        SceneMaxScope scope = new SceneMaxScope();
        VariableDef varDef = networkArrayVar("recorded_actions");
        VarInst varInst = arrayInst(varDef, scope, 1d, 2d, 3d);

        ArrayCommand command = new ArrayCommand();
        command.action = ArrayCommand.ArrayAction.Reset;
        command.varName = "recorded_actions";
        command.expr = parseExpression("0");

        ArrayCommandController controller = new ArrayCommandController(app, new ProgramDef(), scope, command);
        assertTrue(controller.run(0f));

        assertEquals(Arrays.<Object>asList(0d, 0d, 0d), varInst.values);
        assertEquals("recorded_actions", app.syncedVarName);
        assertEquals(Arrays.<Object>asList(0d, 0d, 0d), app.syncedValue);
    }

    private static VariableDef networkArrayVar(String name) {
        VariableDef varDef = new VariableDef();
        varDef.varName = name;
        varDef.varType = VariableDef.VAR_TYPE_ARRAY;
        varDef.isNetwork = true;
        return varDef;
    }

    private static VarInst arrayInst(VariableDef varDef, SceneMaxScope scope, Object... values) {
        VarInst varInst = new VarInst(varDef, scope);
        varInst.varType = VariableDef.VAR_TYPE_ARRAY;
        varInst.values = new ArrayList<>(Arrays.asList(values));
        scope.vars_index.put(varDef.varName, varInst);
        return varInst;
    }

    private SceneMaxParser.Logical_expressionContext parseExpression(String expr) {
        SceneMaxLexer lexer = new SceneMaxLexer(new ANTLRInputStream(expr));
        SceneMaxParser parser = new SceneMaxParser(new CommonTokenStream(lexer));
        return parser.logical_expression();
    }

    private static class CapturingSceneMaxApp extends SceneMaxApp {
        String syncedVarName;
        Object syncedValue;

        @Override
        public void syncNetworkVariable(String varName, Object value, boolean declarationInit) {
            syncedVarName = varName;
            if (value instanceof List) {
                syncedValue = new ArrayList<>((List<?>) value);
            } else {
                syncedValue = value;
            }
        }
    }
}
