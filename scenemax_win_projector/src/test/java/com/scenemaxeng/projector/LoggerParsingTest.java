package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.LoggerCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LoggerParsingTest {

    @Test
    public void parsesLoggerStatements() {
        ProgramDef prg = parse("Logger.info \"ready\"\nLogger.debug \"details\"\nLogger.error \"boom\"");

        assertTrue(prg.syntaxErrors.isEmpty());
        assertEquals(3, prg.actions.size());
        assertLogger(prg, 0, LoggerCommand.INFO);
        assertLogger(prg, 1, LoggerCommand.DEBUG);
        assertLogger(prg, 2, LoggerCommand.ERROR);
    }

    private void assertLogger(ProgramDef prg, int index, String level) {
        assertTrue(prg.actions.get(index) instanceof LoggerCommand);
        assertEquals(level, ((LoggerCommand) prg.actions.get(index)).level);
    }

    private ProgramDef parse(String code) {
        return new SceneMaxLanguageParser(null, "").parse(code);
    }
}
