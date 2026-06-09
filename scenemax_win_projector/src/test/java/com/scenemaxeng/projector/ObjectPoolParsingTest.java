package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ObjectPoolAcquireCommand;
import com.scenemaxeng.compiler.ObjectPoolCreateCommand;
import com.scenemaxeng.compiler.ObjectPoolReleaseCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import com.scenemaxeng.compiler.VariableAssignmentCommand;
import com.scenemaxeng.compiler.VariableDef;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ObjectPoolParsingTest {

    @Test
    public void parsesCreateAcquireAndReleaseCommands() {
        String code = "rocks_pool => Object.Pool(meshy_rock, size 5)\n"
                + "var rock = rocks_pool.acquire\n"
                + "rocks_pool.release rock";

        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(code);

        assertTrue(String.join("\n", prg.syntaxErrors), prg.syntaxErrors.isEmpty());
        assertEquals(3, prg.actions.size());
        assertTrue(prg.actions.get(0) instanceof ObjectPoolCreateCommand);
        assertTrue(prg.actions.get(1) instanceof ObjectPoolAcquireCommand);
        assertTrue(prg.actions.get(2) instanceof ObjectPoolReleaseCommand);
        assertEquals(VariableDef.VAR_TYPE_OBJECT_POOL, prg.getVar("rocks_pool").varType);
        assertEquals(VariableDef.VAR_TYPE_OBJECT, prg.getVar("rock").varType);

        ObjectPoolCreateCommand create = (ObjectPoolCreateCommand) prg.actions.get(0);
        assertEquals("meshy_rock", create.sourceName);
        assertEquals("5", create.initialSizeExpr.getText());

        ObjectPoolAcquireCommand acquire = (ObjectPoolAcquireCommand) prg.actions.get(1);
        assertEquals("rocks_pool", acquire.poolVarName);
        assertEquals("rock", acquire.resultVarName);

        ObjectPoolReleaseCommand release = (ObjectPoolReleaseCommand) prg.actions.get(2);
        assertEquals("rocks_pool", release.poolVarName);
        assertEquals("rock", release.objectVarName);
    }

    @Test
    public void parsesFreeAlias() {
        String code = "rocks_pool => Object.Pool(meshy_rock, 2)\n"
                + "var rock = rocks_pool.acquire\n"
                + "rocks_pool.free rock";

        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(code);

        assertTrue(String.join("\n", prg.syntaxErrors), prg.syntaxErrors.isEmpty());
        assertNotNull(prg.getVar("rocks_pool"));
        assertTrue(prg.actions.get(2) instanceof ObjectPoolReleaseCommand);
    }

    @Test
    public void parsesFunctionFactoryPoolSource() {
        SceneMaxLanguageParser.modelsUsed = new ArrayList<>();
        String code = "create_rock = do\n"
                + "  rock1 => meshy_rock : pos (22.532026,-51.0,148.68306), scale 2, rotate(0.0,0.0,0.0), shadow mode on, collision shape box, mass 3.0\n"
                + "  return rock1\n"
                + "end do\n"
                + "rocks_pool => Object.Pool(create_rock, size 5)\n"
                + "var rock = rocks_pool.acquire";

        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(code);

        assertTrue(String.join("\n", prg.syntaxErrors), prg.syntaxErrors.isEmpty());
        assertTrue(prg.actions.get(0) instanceof ObjectPoolCreateCommand);
        ObjectPoolCreateCommand create = (ObjectPoolCreateCommand) prg.actions.get(0);
        assertEquals("create_rock", create.sourceName);
        assertTrue(create.sourceIsFunction);
        assertTrue(SceneMaxLanguageParser.modelsUsed.contains("meshy_rock"));
        assertFalse(SceneMaxLanguageParser.modelsUsed.contains("create_rock"));
    }

    @Test
    public void preservesMixedDeclarationOrder() {
        String code = "rocks_pool => Object.Pool(meshy_rock, 2)\n"
                + "var count = 1, rock = rocks_pool.acquire, label = \"done\"";

        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(code);

        assertTrue(String.join("\n", prg.syntaxErrors), prg.syntaxErrors.isEmpty());
        assertEquals(4, prg.actions.size());
        assertTrue(prg.actions.get(1) instanceof VariableAssignmentCommand);
        assertTrue(prg.actions.get(2) instanceof ObjectPoolAcquireCommand);
        assertTrue(prg.actions.get(3) instanceof VariableAssignmentCommand);
    }

    @Test
    public void invokesFunctionReturnValueSynchronously() throws Exception {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse("make_number = do\n"
                + "  return 7\n"
                + "end do");
        assertTrue(String.join("\n", prg.syntaxErrors), prg.syntaxErrors.isEmpty());

        SceneMaxApp app = new SceneMaxApp();
        setProgram(app, prg);

        Object value = app.invokeFunctionValueNow("make_number", Collections.emptyList(), new SceneMaxScope());

        assertEquals(7d, ((Double) value).doubleValue(), 0.0);
    }

    private void setProgram(SceneMaxApp app, ProgramDef prg) throws Exception {
        Field field = SceneMaxApp.class.getDeclaredField("prg");
        field.setAccessible(true);
        field.set(app, prg);
    }
}
