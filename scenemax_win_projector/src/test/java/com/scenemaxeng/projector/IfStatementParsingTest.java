package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class IfStatementParsingTest {

    @Test
    public void parsesIfElseIfElseWithBraces() {
        ProgramDef prg = parse(
                "if score == 5 {\n"
                        + "} else if score == 10 {\n"
                        + "} else {\n"
                        + "}");

        assertTrue(String.join("\n", prg.syntaxErrors), prg.syntaxErrors.isEmpty());
    }

    @Test
    public void reportsMalformedIfBlockInsteadOfThrowing() {
        ProgramDef prg = parse(
                "flash = {\n"
                        + "  if score == 5\n"
                        + "}");

        assertNotNull(prg);
        assertFalse(prg.syntaxErrors.toString(), prg.syntaxErrors.isEmpty());
    }

    @Test
    public void reportsMalformedElseBlockInsteadOfThrowing() {
        ProgramDef prg = parse(
                "if score == 5 {\n"
                        + "} else");

        assertNotNull(prg);
        assertFalse(prg.syntaxErrors.toString(), prg.syntaxErrors.isEmpty());
    }

    @Test
    public void parsesKeywordUserDataFieldInIfExpression() {
        ProgramDef prg = parse(
                "crystal_box=>box\n"
                        + "crystal_box.data.flash = 1\n"
                        + "if (crystal_box.data.flash == 1) {\n"
                        + "  crystal_box.data.flash = 0\n"
                        + "} else {\n"
                        + "  crystal_box.data.flash = 1\n"
                        + "}");

        assertTrue(String.join("\n", prg.syntaxErrors), prg.syntaxErrors.isEmpty());
    }

    private ProgramDef parse(String code) {
        return new SceneMaxLanguageParser(null, "").parse(code);
    }
}
