package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.AttachToCommand;
import com.scenemaxeng.compiler.LabelTextCommand;
import com.scenemaxeng.compiler.LabelVariableDef;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import com.scenemaxeng.compiler.VariableDef;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LabelSyntaxParsingTest {

    @Test
    public void parsesRuntimeLabelAndBoneAttachment() {
        String code = "fighter1 => fighter\n"
                + "lbl => label : multiplayer, text \"Pilot\", font \"font1\", style \"holo_glass\", size (50,10), scale 1.4, transparency 30\n"
                + "lbl.text = \"Ace Pilot\"\n"
                + "lbl.attach to fighter1.\"mixamo:head\" : pos (2,5,0)";

        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(code);

        assertTrue(prg.syntaxErrors.toString(), prg.syntaxErrors.isEmpty());
        assertEquals(VariableDef.VAR_TYPE_LABEL, prg.getVar("lbl").varType);

        LabelVariableDef label = (LabelVariableDef) prg.getVar("lbl");
        assertEquals("\"Pilot\"", label.textExpr.getText());
        assertEquals("font1", label.font);
        assertEquals("holo_glass", label.style);
        assertEquals("50", label.widthExpr.getText());
        assertEquals("10", label.heightExpr.getText());
        assertEquals("1.4", label.scaleExpr.getText());
        assertEquals("30", label.transparencyExpr.getText());
        assertTrue(label.isMultiplayer);

        LabelTextCommand textCommand = (LabelTextCommand) prg.actions.get(2);
        assertEquals("lbl", textCommand.targetVar);
        assertEquals("\"Ace Pilot\"", textCommand.textExpr.getText());

        AttachToCommand attach = (AttachToCommand) prg.actions.get(3);
        assertEquals("lbl", attach.entityNameToAttach);
        assertEquals("fighter1", attach.targetVar);
        assertEquals("mixamo:head", attach.jointName);
        assertEquals("2", attach.xExpr.getText());
        assertEquals("5", attach.yExpr.getText());
        assertEquals("0", attach.zExpr.getText());
    }
}
