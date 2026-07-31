package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ActionCommandShowHide;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import com.scenemaxeng.compiler.UIEaseCommand;
import com.scenemaxeng.compiler.UIMessageCommand;
import com.scenemaxeng.compiler.UISetPropertyCommand;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UITargetVariableParsingTest {

    @Test
    public void parsesUiTargetsStoredInArraysAndUsedThroughVariables() {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(
                "var arrUI = [UI.layer1.panel1, UI.layer1.panel1.text1]\n"
                        + "var panel = arrUI[0]\n"
                        + "var txt = arrUI[1]\n"
                        + "panel.Show\n"
                        + "txt.text = \"Ready\"\n"
                        + "panel.ease(\"EaseOutBounce\", Right, 2) async\n"
                        + "txt.message(\"Go\", TextEffect.fade_in, 1)\n"
        );

        assertTrue(String.join("\n", prg.syntaxErrors), prg.syntaxErrors.isEmpty());
        assertTrue(prg.actions.get(3) instanceof ActionCommandShowHide);
        assertTrue(prg.actions.get(4) instanceof UISetPropertyCommand);
        assertTrue(prg.actions.get(5) instanceof UIEaseCommand);
        assertTrue(prg.actions.get(6) instanceof UIMessageCommand);

        UISetPropertyCommand setText = (UISetPropertyCommand) prg.actions.get(4);
        assertEquals("txt", setText.targetVarName);
        assertEquals("text", setText.propertyName);

        UIEaseCommand ease = (UIEaseCommand) prg.actions.get(5);
        assertEquals("panel", ease.targetVarName);
        assertEquals("Right", ease.directionName);
        assertTrue(ease.isAsync);

        UIMessageCommand message = (UIMessageCommand) prg.actions.get(6);
        assertEquals("txt", message.targetVarName);
        assertEquals("fade_in", message.effectNames.get(0));
    }
}
