package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import com.scenemaxeng.compiler.UIEaseCommand;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class UIEaseParsingTest {

    @Test
    public void parsesUiEaseCommandForActiveUiWidget() {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(
                "UI.layer1.my_panel.ease(\"EaseInQuad\", Up, 0.5)"
        );

        assertTrue(prg.syntaxErrors.isEmpty());
        assertTrue(prg.actions.get(0) instanceof UIEaseCommand);

        UIEaseCommand cmd = (UIEaseCommand) prg.actions.get(0);
        assertNull(cmd.uiName);
        assertEquals("layer1", cmd.layerName);
        assertEquals("my_panel", cmd.widgetPath);
        assertEquals("Up", cmd.directionName);
        assertEquals("\"EaseInQuad\"", cmd.easingExpr.getText());
        assertEquals("0.5", cmd.durationExpr.getText());
    }

    @Test
    public void parsesUiEaseCommandForNamedUiLayer() {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(
                "UI.hud.layer1.ease(\"EaseOutBounce\", Left, 1)"
        );

        assertTrue(prg.syntaxErrors.isEmpty());
        assertTrue(prg.actions.get(0) instanceof UIEaseCommand);

        UIEaseCommand cmd = (UIEaseCommand) prg.actions.get(0);
        assertEquals("hud", cmd.uiName);
        assertEquals("layer1", cmd.layerName);
        assertEquals("", cmd.widgetPath);
        assertEquals("Left", cmd.directionName);
        assertEquals("\"EaseOutBounce\"", cmd.easingExpr.getText());
        assertEquals("1", cmd.durationExpr.getText());
    }

    @Test
    public void parsesUiEaseCommandForNestedWidgetPath() {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(
                "UI.menu.layer1.dialog.panel1.ease(\"EaseOutElastic\", Down, 1.25)"
        );

        assertTrue(prg.syntaxErrors.isEmpty());
        assertTrue(prg.actions.get(0) instanceof UIEaseCommand);

        UIEaseCommand cmd = (UIEaseCommand) prg.actions.get(0);
        assertEquals("menu", cmd.uiName);
        assertEquals("layer1", cmd.layerName);
        assertEquals("dialog.panel1", cmd.widgetPath);
        assertEquals("Down", cmd.directionName);
    }
}
