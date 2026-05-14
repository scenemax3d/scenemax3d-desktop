package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProcessEndCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ProcessEndParsingTest {

    @Test
    public void parsesProcessEndStatement() {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse("process.end");

        assertTrue(prg.syntaxErrors.isEmpty());
        assertEquals(1, prg.actions.size());
        assertTrue(prg.actions.get(0) instanceof ProcessEndCommand);
    }
}
