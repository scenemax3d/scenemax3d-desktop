package com.scenemaxeng.projector;

import com.abware.scenemaxlang.parser.SceneMaxLexer;
import com.abware.scenemaxlang.parser.SceneMaxParser;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.VariableAssignmentCommand;
import com.scenemaxeng.compiler.VariableDef;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

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

    private SceneMaxParser.Logical_expressionContext parseExpression(String expr) {
        SceneMaxLexer lexer = new SceneMaxLexer(new ANTLRInputStream(expr));
        SceneMaxParser parser = new SceneMaxParser(new CommonTokenStream(lexer));
        return parser.logical_expression();
    }
}
