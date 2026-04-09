package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import com.scenemaxeng.compiler.UIMessageCommand;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class UIMessageParsingTest {

    @Test
    public void parsesUiMessageCommandForActiveUi() {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(
                "UI.layer1.panel1.text1.message(\"Hello World\", TextEffect.typewriter, 2)"
        );

        assertTrue(prg.syntaxErrors.isEmpty());
        assertTrue(prg.actions.get(0) instanceof UIMessageCommand);

        UIMessageCommand cmd = (UIMessageCommand) prg.actions.get(0);
        assertNull(cmd.uiName);
        assertEquals("layer1", cmd.layerName);
        assertEquals("panel1.text1", cmd.widgetPath);
        assertEquals(1, cmd.effectNames.size());
        assertEquals("typewriter", cmd.effectNames.get(0));
    }

    @Test
    public void parsesUiMessageCommandForNamedUi() {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(
                "UI.hud.layer1.text1.message(\"Ready\", TextEffect.zoom_in, 1.5)"
        );

        assertTrue(prg.syntaxErrors.isEmpty());
        assertTrue(prg.actions.get(0) instanceof UIMessageCommand);

        UIMessageCommand cmd = (UIMessageCommand) prg.actions.get(0);
        assertEquals("hud", cmd.uiName);
        assertEquals("layer1", cmd.layerName);
        assertEquals("text1", cmd.widgetPath);
        assertEquals(1, cmd.effectNames.size());
        assertEquals("zoom_in", cmd.effectNames.get(0));
    }

    @Test
    public void parsesUiMessageCommandWithCombinedEffects() {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(
                "UI.layer1.text1.message(\"Ready\", TextEffect.fade_in | TextEffect.zoom_in, 1.5)"
        );

        assertTrue(prg.syntaxErrors.isEmpty());
        assertTrue(prg.actions.get(0) instanceof UIMessageCommand);

        UIMessageCommand cmd = (UIMessageCommand) prg.actions.get(0);
        assertEquals(2, cmd.effectNames.size());
        assertEquals("fade_in", cmd.effectNames.get(0));
        assertEquals("zoom_in", cmd.effectNames.get(1));
    }
}
