package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.IKCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class IKSyntaxParsingTest {
    @Test
    public void parsesIKResourceAndExplicitLayerCommands() {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(
                "player=>dragon\n"
                        + "lever=>dragon\n"
                        + "player.ik = \"player_interaction_ik\"\n"
                        + "player.ik.right_hand.target = lever\n"
                        + "player.ik.right_hand.blend = 0.2\n"
                        + "player.ik.right_hand.weight = 0.75\n"
                        + "player.ik.right_hand.play : target lever, blend 0.2, weight 1\n"
                        + "player.ik.right_hand.stop : blend 0.15\n"
                        + "player.ik = empty");

        assertTrue(String.join("\n", prg.syntaxErrors), prg.syntaxErrors.isEmpty());
        assertEquals(9, prg.actions.size());

        IKCommand apply = (IKCommand) prg.actions.get(2);
        assertEquals(IKCommand.ACTION_APPLY, apply.action);
        assertEquals("player", apply.ownerVarName);
        assertNotNull(apply.ikNameExpr);

        IKCommand target = (IKCommand) prg.actions.get(3);
        assertEquals(IKCommand.ACTION_SET_TARGET, target.action);
        assertEquals("right_hand", target.layerId);
        assertEquals("lever", target.targetEntityPos.entityName);

        IKCommand blend = (IKCommand) prg.actions.get(4);
        assertEquals(IKCommand.ACTION_SET_BLEND, blend.action);
        assertNotNull(blend.blendExpr);

        IKCommand weight = (IKCommand) prg.actions.get(5);
        assertEquals(IKCommand.ACTION_SET_WEIGHT, weight.action);
        assertNotNull(weight.weightExpr);

        IKCommand play = (IKCommand) prg.actions.get(6);
        assertEquals(IKCommand.ACTION_PLAY, play.action);
        assertEquals("right_hand", play.layerId);
        assertEquals("lever", play.targetEntityPos.entityName);
        assertNotNull(play.blendExpr);
        assertNotNull(play.weightExpr);

        IKCommand stop = (IKCommand) prg.actions.get(7);
        assertEquals(IKCommand.ACTION_STOP, stop.action);
        assertNotNull(stop.blendExpr);

        IKCommand remove = (IKCommand) prg.actions.get(8);
        assertEquals(IKCommand.ACTION_REMOVE, remove.action);
    }
}
