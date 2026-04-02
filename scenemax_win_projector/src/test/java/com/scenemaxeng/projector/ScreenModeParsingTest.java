package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ScreenModeParsingTest {

    @Test
    public void parsesFullScreenMode() {
        ProgramDef prg = parse("screen.mode full");
        assertEquals(ProgramDef.ScreenMode.FULL, prg.screenMode);
    }

    @Test
    public void parsesBorderlessScreenMode() {
        ProgramDef prg = parse("screen.mode borderless");
        assertEquals(ProgramDef.ScreenMode.BORDERLESS, prg.screenMode);
    }

    @Test
    public void keepsFirstScreenModeOccurrence() {
        ProgramDef prg = parse("screen.mode borderless\nscreen.mode full\nscreen.mode window");
        assertEquals(ProgramDef.ScreenMode.BORDERLESS, prg.screenMode);
    }

    private ProgramDef parse(String code) {
        return new SceneMaxLanguageParser(null, "").parse(code);
    }
}
