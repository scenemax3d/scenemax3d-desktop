package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.InputStatementCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import com.jme3.input.KeyInput;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InputParsingTest {

    @Test
    public void parsesEnterKeyPressedOnceHandler() {
        ProgramDef prg = parse("when key Enter is pressed once do\nend do");

        assertTrue(prg.syntaxErrors.toString(), prg.syntaxErrors.isEmpty());
        assertEquals(1, prg.actions.size());
        InputStatementCommand cmd = (InputStatementCommand) prg.actions.get(0);
        assertEquals("key", cmd.inputType);
        assertEquals("enter", cmd.inputKey);
        assertTrue(cmd.once);
    }

    @Test
    public void mapsEnterKeyToRuntimeReturnKey() {
        SceneMaxApp app = new SceneMaxApp();

        assertEquals(Integer.valueOf(KeyInput.KEY_RETURN), app.getKeyMapping().get("enter"));
    }

    private ProgramDef parse(String code) {
        return new SceneMaxLanguageParser(null, "").parse(code);
    }
}
