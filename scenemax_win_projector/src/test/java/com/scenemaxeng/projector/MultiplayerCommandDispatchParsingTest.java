package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.DoBlockCommand;
import com.scenemaxeng.compiler.CollisionStatementCommand;
import com.scenemaxeng.compiler.NetworkEventHandlerCommand;
import com.scenemaxeng.compiler.NetworkJoinSessionCommand;
import com.scenemaxeng.compiler.NetworkSendCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import com.scenemaxeng.compiler.StatementDef;
import com.scenemaxeng.compiler.VariableDef;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MultiplayerCommandDispatchParsingTest {

    private static final int MAGIC = 0x504d5853;
    private static final byte VERSION = 1;
    private static final byte LOGIN_REJECTED = 3;

    @Test
    public void parsesGeneratedMoveAndRotateDispatchCommands() {
        assertParses("mp_remote_1 => sinbad\nmp_remote_1.move right 1 in 0.1 seconds");
        assertParses("mp_remote_1 => sinbad\nmp_remote_1.move (x + 1) in 0.1 seconds");
        assertParses("mp_remote_1 => sinbad\nmp_remote_1.move forward 1 for 0.1 seconds");
        assertParses("mp_remote_1 => sinbad\nmp_remote_1.move to (1,0,0) in 0.1 seconds");
        assertParses("mp_remote_1 => sinbad\nmp_remote_1.rotate (y - 45) in 0.2 seconds");
        assertParses("mp_remote_1 => sinbad\nmp_remote_1.rotate to (y 90) in 0.2 seconds");
        assertParses("mp_remote_1 => sinbad\nmp_remote_1.rotate (0,90,0)");
        assertParses("mp_remote_1 => sinbad\nmp_remote_1.\"Run\" at speed of 1");
        assertParses("mp_remote_1 => sinbad\nmp_remote_1.\"Take 001\"[0-50] at speed of 1.5");
    }

    @Test
    public void parsesGeneratedAttachAndIkDispatchCommands() {
        assertParses("mp_remote_1 => sinbad\n"
                + "mp_remote_2 => sinbad\n"
                + "mp_remote_1.attach to mp_remote_2.\"Bip01 Spine1_04\": pos (-0.12,-1.23,1.03)");
        assertParses("mp_remote_1 => sinbad\n"
                + "mp_remote_3 => sphere\n"
                + "mp_remote_1.ik = \"ik_sit_on_horse\"\n"
                + "mp_remote_1.ik.horse_sit_right_foot.play : target mp_remote_3, blend 0.2, weight 1");
    }

    @Test
    public void parsesGeneratedCharacterModeDispatchCommands() {
        assertParses("mp_remote_1 => sinbad\n"
                + "mp_remote_1.switch to character mode : gravity 60");
        assertParses("mp_remote_1 => sinbad\n"
                + "mp_remote_1.clear character mode");
    }

    @Test
    public void treatsCharacterModeAsStructuralMultiplayerState() throws Exception {
        MultiplayerNetworkComponent component = new MultiplayerNetworkComponent(null);
        Method method = MultiplayerNetworkComponent.class.getDeclaredMethod("isStructuralCommand", String.class);
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(component, "{network_entity}.switch to character mode : gravity 60"));
        assertTrue((Boolean) method.invoke(component, "{network_entity}.clear character mode"));
    }

    @Test
    public void parsesGeneratedMultiplayerSpawnCommandsWithScaleAndColliders() {
        assertParses("mp_remote_1 => horse1_native: pos (0.482243,0,1.164553), scale 3.7, collision shape none");
        assertParses("mp_remote_2 => collider sphere: pos (-5.174184,1.964885,4.311819), radius 0.5, scale 0.3");
    }

    @Test
    public void parsesGeneratedMultiplayerSpawnCommandsForAllPrimitives() {
        assertParses("mp_remote_box => box: pos (1,2,3), rotate(0,45,0), size (2,3,4), material=\"stone\"");
        assertParses("mp_remote_sphere => sphere: pos (1,2,3), radius 0.75, material=\"glass\"");
        assertParses("mp_remote_cylinder => cylinder: pos (1,2,3), radius (0.5,1), height 2.5, material=\"metal\"");
        assertParses("mp_remote_hollow => hollow cylinder: pos (1,2,3), radius (1.2,1), inner radius (0.4,0.3), height 2");
        assertParses("mp_remote_quad => quad: pos (1,2,3), scale 2, size (4,3), material=\"screen\"");
        assertParses("mp_remote_wedge => wedge: pos (1,2,3), size (2,1,3)");
        assertParses("mp_remote_cone => cone: pos (1,2,3), radius (0,1), height 2");
        assertParses("mp_remote_stairs => stairs: pos (1,2,3), size (2,0.25,0.4), steps 6");
        assertParses("mp_remote_arch => arch: pos (1,2,3), size (2,2.5,0.5), thickness 0.35, segments 12");
        assertParses("mp_remote_collider_box => collider box: pos (1,2,3), size (2,3,4)");
        assertParses("mp_remote_collider_hollow => collider hollow cylinder: pos (1,2,3), radius (1,1), inner radius (0.5,0.5), height 2");
    }

    @Test
    public void keepsMultiplayerFlagOnPrimitivesCreatedInsideDoBlock() {
        ProgramDef program = new SceneMaxLanguageParser(null, "").parse(
                "do async\n"
                        + "  s=>sphere : pos (0,0,0), material=\"pond\", multiplayer\n"
                        + "  b=>box : pos (0,0,0), multiplayer\n"
                        + "  c=>cylinder : pos (0,0,0), multiplayer\n"
                        + "  h=>hollow cylinder : pos (0,0,0), multiplayer\n"
                        + "  q=>quad : pos (0,0,0), multiplayer\n"
                        + "  w=>wedge : pos (0,0,0), multiplayer\n"
                        + "  co=>cone : pos (0,0,0), multiplayer\n"
                        + "  st=>stairs : pos (0,0,0), multiplayer\n"
                        + "  a=>arch : pos (0,0,0), multiplayer\n"
                        + "end do");

        assertTrue(program.syntaxErrors == null || program.syntaxErrors.isEmpty());
        assertFalse(program.actions.isEmpty());
        DoBlockCommand block = (DoBlockCommand) program.actions.get(0);

        int multiplayerPrimitiveCount = 0;
        for (StatementDef statement : block.prg.actions) {
            if (statement instanceof com.scenemaxeng.compiler.GraphicEntityCreationCommand) {
                VariableDef var = ((com.scenemaxeng.compiler.GraphicEntityCreationCommand) statement).varDef;
                if (isPrimitiveType(var.varType) && var.isMultiplayer) {
                    multiplayerPrimitiveCount++;
                }
            }
        }

        assertEquals("Expected every primitive to keep the multiplayer flag", 9, multiplayerPrimitiveCount);
    }

    @Test
    public void parsesNetworkCollisionEventModel() {
        ProgramDef program = new SceneMaxLanguageParser(null, "").parse(
                "when left_foot_collider collides with #head_collider do\n"
                        + "  network.send \"head_hit_by_leg\"\n"
                        + "end do\n"
                        + "network.on (\"head_hit_by_leg\") = do\n"
                        + "  man.move backward 3 in 0.1 seconds\n"
                        + "end do");

        assertTrue(program.syntaxErrors == null || program.syntaxErrors.isEmpty());
        assertEquals(2, program.actions.size());
        CollisionStatementCommand collision = (CollisionStatementCommand) program.actions.get(0);
        assertTrue(collision.destEndpoint.networkEntity);
        assertEquals("head_collider", collision.destEndpoint.networkObjectName);
        assertTrue(collision.doBlock.prg.actions.get(0) instanceof NetworkSendCommand);
        assertTrue(program.actions.get(1) instanceof NetworkEventHandlerCommand);
    }

    @Test
    public void parsesNetworkSessionJoinAndRuntimeStateExpressions() {
        ProgramDef program = new SceneMaxLanguageParser(null, "").parse(
                "network.join session \"combat 1\"\n"
                        + "network.join session 1000\n"
                        + "when network.ready do\n"
                        + "  var sessions = network.state.sessions\n"
                        + "end do");

        assertTrue(program.syntaxErrors == null || program.syntaxErrors.isEmpty());
        assertEquals(3, program.actions.size());
        assertTrue(program.actions.get(0) instanceof NetworkJoinSessionCommand);
        assertTrue(program.actions.get(1) instanceof NetworkJoinSessionCommand);
    }

    @Test
    public void preparesSnapshotResumeCommands() throws Exception {
        MultiplayerNetworkComponent component = new MultiplayerNetworkComponent(null);
        Method method = MultiplayerNetworkComponent.class.getDeclaredMethod(
                "commandForSnapshotAction", String.class, int.class);
        method.setAccessible(true);

        assertEquals("{network_entity}.move right 4 in 10 seconds",
                method.invoke(component, "{network_entity}.move right 4 in 10 seconds", 5000));
        assertEquals("{network_entity}.move (x - 6) in 12 seconds",
                method.invoke(component, "{network_entity}.move (x - 6) in 12 seconds", 3000));
        assertEquals("{network_entity}.rotate (y + 90) in 10 seconds",
                method.invoke(component, "{network_entity}.rotate (y + 90) in 10 seconds", 5000));
        assertEquals("{network_entity}.move to (4,0,0) in 5 seconds",
                method.invoke(component, "{network_entity}.move to (4,0,0) in 10 seconds", 5000));
    }

    @Test
    public void receiveLoopStopsWhenPacketHandlerClosesChannel() throws Exception {
        MultiplayerNetworkComponent component = new MultiplayerNetworkComponent(null);
        DatagramChannel client = DatagramChannel.open();
        DatagramChannel server = DatagramChannel.open();
        try {
            client.bind(new InetSocketAddress("127.0.0.1", 0));
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            client.configureBlocking(false);
            server.configureBlocking(false);
            client.connect(server.getLocalAddress());
            server.connect(client.getLocalAddress());

            Field channelField = MultiplayerNetworkComponent.class.getDeclaredField("channel");
            channelField.setAccessible(true);
            channelField.set(component, client);

            ByteBuffer packet = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            packet.putInt(MAGIC);
            packet.put(VERSION);
            packet.put(LOGIN_REJECTED);
            packet.putShort((short) 0);
            packet.flip();
            server.write(packet);

            Method readPackets = MultiplayerNetworkComponent.class.getDeclaredMethod("readPackets");
            readPackets.setAccessible(true);
            readPackets.invoke(component);
        } finally {
            component.close();
            client.close();
            server.close();
        }
    }

    private void assertParses(String code) {
        ProgramDef program = new SceneMaxLanguageParser(null, "").parse(code);
        assertTrue("Expected command to parse without syntax errors:\n" + code,
                program.syntaxErrors == null || program.syntaxErrors.isEmpty());
    }

    private boolean isPrimitiveType(int varType) {
        return varType == VariableDef.VAR_TYPE_SPHERE
                || varType == VariableDef.VAR_TYPE_BOX
                || varType == VariableDef.VAR_TYPE_CYLINDER
                || varType == VariableDef.VAR_TYPE_HOLLOW_CYLINDER
                || varType == VariableDef.VAR_TYPE_QUAD
                || varType == VariableDef.VAR_TYPE_WEDGE
                || varType == VariableDef.VAR_TYPE_CONE
                || varType == VariableDef.VAR_TYPE_STAIRS
                || varType == VariableDef.VAR_TYPE_ARCH;
    }
}
