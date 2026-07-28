package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.DoBlockCommand;
import com.scenemaxeng.compiler.CollisionStatementCommand;
import com.scenemaxeng.compiler.NetworkBroadcastCommand;
import com.scenemaxeng.compiler.NetworkEntitySendCommand;
import com.scenemaxeng.compiler.NetworkEventHandlerCommand;
import com.scenemaxeng.compiler.NetworkJoinSessionCommand;
import com.scenemaxeng.compiler.NetworkSendCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import com.scenemaxeng.compiler.SetUserDataCommand;
import com.scenemaxeng.compiler.StatementDef;
import com.scenemaxeng.compiler.VariableDeclarationCommand;
import com.scenemaxeng.compiler.VariableDef;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MultiplayerCommandDispatchParsingTest {

    private static final int MAGIC = 0x504d5853;
    private static final byte VERSION = 1;
    private static final byte LOGIN_ACCEPTED = 2;
    private static final byte LOGIN_REJECTED = 3;
    private static final byte CREATE_ENTITY_REQUEST = 10;
    private static final byte CREATE_ENTITY_ACCEPTED = 11;
    private static final byte DESTROY_ENTITY = 12;
    private static final byte COMMAND_DISPATCH = 20;
    private static final byte ACTIVE_ACTION_START = 22;
    private static final byte NETWORK_EVENT = 24;
    private static final byte INITIAL_SYNC_COMPLETE = 32;

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
    public void parsesGeneratedPosDispatchCommands() {
        assertParses("mp_remote_1 => sinbad\n"
                + "mp_remote_1.pos (0,5,7)");
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
    public void throttlesCharacterModeTransformCorrectionsToFiveSeconds() throws Exception {
        CapturingSceneMaxApp app = new CapturingSceneMaxApp();
        app.characterControlled = true;
        MultiplayerNetworkComponent component = new MultiplayerNetworkComponent(app);

        VariableDef varDef = new VariableDef();
        varDef.varName = "player";
        varDef.varType = VariableDef.VAR_TYPE_3D;
        varDef.isMultiplayer = true;
        component.registerEntity("player@1", varDef, "fighter1_native");
        Object entity = registeredEntity(component, "player@1");

        Method method = MultiplayerNetworkComponent.class.getDeclaredMethod("shouldSendCorrection",
                entity.getClass(),
                com.jme3.math.Vector3f.class,
                com.jme3.math.Quaternion.class,
                float.class);
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(component, entity,
                new com.jme3.math.Vector3f(0f, 0f, 0f),
                new com.jme3.math.Quaternion(),
                0.25f));
        assertFalse((Boolean) method.invoke(component, entity,
                new com.jme3.math.Vector3f(1f, 0f, 0f),
                new com.jme3.math.Quaternion(),
                0.25f));
        assertTrue((Boolean) method.invoke(component, entity,
                new com.jme3.math.Vector3f(1f, 0f, 0f),
                new com.jme3.math.Quaternion(),
                4.75f));
    }

    @Test
    public void sendsClearCharacterModeAsCommandAndPersistentActionForRegisteredNetworkEntity() throws Exception {
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

            VariableDef varDef = new VariableDef();
            varDef.varName = "player";
            varDef.varType = VariableDef.VAR_TYPE_3D;
            varDef.isMultiplayer = true;

            component.registerEntity("player@1", varDef, "fighter1_native");
            setField(component, "channel", client);
            setField(component, "clientId", 7);
            setField(component, "networkReady", true);
            setField(component, "initialSyncPending", false);
            setRegisteredEntityNetworkId(component, "player@1", 101);

            String clearCommand = "{network_entity}.clear character mode";
            component.dispatchCommand("player@1", clearCommand);
            component.startPersistentCommand("player@1",
                    SceneMaxBaseController.MULTIPLAYER_ACTION_SLOT_STRUCTURAL_BASE + 189,
                    clearCommand);

            ByteBuffer commandPacket = receiveRequiredPacket(server);
            assertHeader(commandPacket, COMMAND_DISPATCH);
            assertEquals(101, commandPacket.getInt());
            byte[] commandBytes = new byte[commandPacket.remaining()];
            commandPacket.get(commandBytes);
            assertEquals(clearCommand, new String(commandBytes, StandardCharsets.UTF_8).trim());

            ByteBuffer actionPacket = receiveRequiredPacket(server);
            assertHeader(actionPacket, ACTIVE_ACTION_START);
            assertEquals(101, actionPacket.getInt());
            assertEquals(SceneMaxBaseController.MULTIPLAYER_ACTION_SLOT_STRUCTURAL_BASE + 189,
                    Byte.toUnsignedInt(actionPacket.get()));
            actionPacket.get();
            assertEquals(1, Short.toUnsignedInt(actionPacket.getShort()));
            assertEquals(Integer.MAX_VALUE, actionPacket.getInt());
            assertEquals(clearCommand, readFixedString(actionPacket, 192));
        } finally {
            component.close();
            client.close();
            server.close();
        }
    }

    @Test
    public void deactivatedEntityRepublishesBeforeSendingQueuedCommand() throws Exception {
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

            VariableDef varDef = new VariableDef();
            varDef.varName = "fx";
            varDef.varType = VariableDef.VAR_TYPE_EFFEKSEER;
            varDef.isMultiplayer = true;

            component.registerEntity("fx@1", varDef, "effekseer",
                    "{network_entity} => effects.effekseer.burst");
            setField(component, "channel", client);
            setField(component, "clientId", 7);
            setField(component, "networkReady", true);
            setField(component, "initialSyncPending", false);
            setRegisteredEntityNetworkId(component, "fx@1", 101);

            component.deactivateEntity("fx@1");

            ByteBuffer destroyPacket = receiveRequiredPacket(server);
            assertHeader(destroyPacket, DESTROY_ENTITY);
            assertEquals(101, destroyPacket.getInt());
            assertNotNull(registeredEntity(component, "fx@1"));

            String command = "{network_entity}.play pos (1,2,3)";
            component.dispatchCommand("fx@1", command);

            ByteBuffer createPacket = receiveRequiredPacket(server);
            assertHeader(createPacket, CREATE_ENTITY_REQUEST);
            int createRequestId = createPacket.getInt();
            assertTrue(createRequestId > 0);

            sendCreateAccepted(server, 7, 202, createRequestId);
            component.update(0f);

            ByteBuffer commandPacket = receiveRequiredPacket(server);
            assertHeader(commandPacket, COMMAND_DISPATCH);
            assertEquals(202, commandPacket.getInt());
            byte[] commandBytes = new byte[commandPacket.remaining()];
            commandPacket.get(commandBytes);
            assertEquals(command, new String(commandBytes, StandardCharsets.UTF_8).trim());
        } finally {
            component.close();
            client.close();
            server.close();
        }
    }

    @Test
    public void treatsPosAsStructuralMultiplayerState() throws Exception {
        MultiplayerNetworkComponent component = new MultiplayerNetworkComponent(null);
        Method method = MultiplayerNetworkComponent.class.getDeclaredMethod("isStructuralCommand", String.class);
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(component, "{network_entity}.pos (0,5,7)"));
        assertTrue((Boolean) method.invoke(component, "{network_entity}.pos(0,5,7)"));
    }

    @Test
    public void parsesGeneratedMultiplayerSpawnCommandsWithScaleAndColliders() {
        assertParses("mp_remote_1 => horse1_native: pos (0.482243,0,1.164553), scale 3.7, collision shape none");
        assertParses("mp_remote_2 => collider sphere: pos (-5.174184,1.964885,4.311819), radius 0.5, scale 0.3");
    }

    @Test
    public void parsesGeneratedMultiplayerEffekseerSpawnAndPlayCommands() {
        assertParses("mp_remote_fx => effects.effekseer.Homing_Laser01_3: pos (1,2,3), rotate(0,90,0), scale 2");
        assertParses("mp_remote_fx => effects.effekseer.Homing_Laser01_3\n"
                + "mp_remote_fx.play pos (1,2,3), loop, attr = [\"play_back_speed\" 1.2, \"input0\" 0.9]");
    }

    @Test
    public void keepsMultiplayerFlagOnEffekseerDeclarations() {
        ProgramDef program = new SceneMaxLanguageParser(null, "").parse(
                "laser_effect => effects.effekseer.Homing_Laser01_3 : multiplayer, pos (0,0,0)");

        assertTrue(program.syntaxErrors == null || program.syntaxErrors.isEmpty());
        VariableDef var = program.getVar("laser_effect");
        assertNotNull(var);
        assertEquals(VariableDef.VAR_TYPE_EFFEKSEER, var.varType);
        assertTrue(var.isMultiplayer);
    }

    @Test
    public void treatsEffekseerArchetypesAsEffekseerRemoteEntities() throws Exception {
        MultiplayerNetworkComponent component = new MultiplayerNetworkComponent(null);
        Method method = MultiplayerNetworkComponent.class.getDeclaredMethod("archetypeVarType", String.class);
        method.setAccessible(true);

        assertEquals(VariableDef.VAR_TYPE_EFFEKSEER, method.invoke(component, "effekseer"));
        assertEquals(VariableDef.VAR_TYPE_EFFEKSEER,
                method.invoke(component, "effects.effekseer.Homing_Laser01_3"));
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
    public void parsesServerInvokedNetworkEventHandler() {
        ProgramDef program = new SceneMaxLanguageParser(null, "").parse(
                "network.on (\"count\", 5) = do\n"
                        + "end do");

        assertTrue(program.syntaxErrors == null || program.syntaxErrors.isEmpty());
        assertEquals(1, program.actions.size());
        NetworkEventHandlerCommand handler = (NetworkEventHandlerCommand) program.actions.get(0);
        assertNotNull(handler.eventNameExpr);
        assertNotNull(handler.serverIntervalSecondsExpr);
    }

    @Test
    public void parsesNetworkBroadcastWithOptionalMessage() {
        ProgramDef program = new SceneMaxLanguageParser(null, "").parse(
                "network.broadcast (\"new_player\", \"some message\")\n"
                        + "network.broadcast (\"new_player\")");

        assertTrue(program.syntaxErrors == null || program.syntaxErrors.isEmpty());
        assertEquals(2, program.actions.size());
        NetworkBroadcastCommand withMessage = (NetworkBroadcastCommand) program.actions.get(0);
        NetworkBroadcastCommand withoutMessage = (NetworkBroadcastCommand) program.actions.get(1);
        assertNotNull(withMessage.eventNameExpr);
        assertNotNull(withMessage.messageExpr);
        assertNotNull(withoutMessage.eventNameExpr);
        assertNull(withoutMessage.messageExpr);
    }

    @Test
    public void parsesNetworkEventHandlerMessageParameter() {
        ProgramDef program = new SceneMaxLanguageParser(null, "").parse(
                "network.on (\"new_player\", msg) = do\n"
                        + "end do");

        assertTrue(program.syntaxErrors == null || program.syntaxErrors.isEmpty());
        assertEquals(1, program.actions.size());
        NetworkEventHandlerCommand handler = (NetworkEventHandlerCommand) program.actions.get(0);
        assertEquals("msg", handler.messageParamName);
        assertNull(handler.serverIntervalSecondsExpr);
    }

    @Test
    public void parsesGuardedServerInvokedNetworkEventHandler() {
        ProgramDef program = new SceneMaxLanguageParser(null, "").parse(
                "[player.data.is_fighter == 1]\n"
                        + "network.on (\"count\", 5) = do\n"
                        + "end do");

        assertTrue(program.syntaxErrors == null || program.syntaxErrors.isEmpty());
        assertEquals(1, program.actions.size());
        NetworkEventHandlerCommand handler = (NetworkEventHandlerCommand) program.actions.get(0);
        assertNotNull(handler.goExpr);
        assertNotNull(handler.doBlock.goExpr);
    }

    @Test
    public void dispatchesNetworkEventHandlerWithGoCondition() {
        ProgramDef program = new SceneMaxLanguageParser(null, "").parse(
                "[player.data.is_fighter == 1]\n"
                        + "network.on (\"count\", 5) = do\n"
                        + "end do");
        NetworkEventHandlerCommand handler = (NetworkEventHandlerCommand) program.actions.get(0);
        CapturingSceneMaxApp app = new CapturingSceneMaxApp();
        SceneMaxScope scope = new SceneMaxScope();

        app.registerNetworkEventHandler("count", scope, handler.doBlock);
        app.receiveNetworkEvent("count");

        assertNotNull(app.registeredController);
        assertTrue(app.registeredController instanceof DoBlockController);
        assertNotNull(((DoBlockController) app.registeredController).goExpr);
    }

    @Test
    public void dispatchesNetworkEventHandlerWithMessageParameter() {
        ProgramDef program = new SceneMaxLanguageParser(null, "").parse(
                "network.on (\"new_player\", msg) = do\n"
                        + "end do");
        NetworkEventHandlerCommand handler = (NetworkEventHandlerCommand) program.actions.get(0);
        CapturingSceneMaxApp app = new CapturingSceneMaxApp();
        SceneMaxScope scope = new SceneMaxScope();

        app.registerNetworkEventHandler("new_player", scope, handler.doBlock, handler.messageParamName);
        app.receiveNetworkEvent("new_player", "some message");

        assertNotNull(app.registeredController);
        DoBlockController controller = (DoBlockController) app.registeredController;
        VarInst msg = (VarInst) controller.funcScopeParams.get("msg");
        assertNotNull(msg);
        assertEquals(VariableDef.VAR_TYPE_STRING, msg.varType);
        assertEquals("some message", msg.value);
    }

    @Test
    public void encodesNetworkEventMessagePayloadWithoutChangingPlainEvents() {
        byte[] plain = MultiplayerNetworkComponent.encodeNetworkEventPayload("new_player", null, 100);
        MultiplayerNetworkComponent.NetworkEventPayload plainDecoded =
                MultiplayerNetworkComponent.decodeNetworkEventPayload(plain);
        assertEquals("new_player", plainDecoded.name);
        assertNull(plainDecoded.message);

        byte[] withMessage = MultiplayerNetworkComponent.encodeNetworkEventPayload(
                "new_player", "some message", 100);
        MultiplayerNetworkComponent.NetworkEventPayload decoded =
                MultiplayerNetworkComponent.decodeNetworkEventPayload(withMessage);
        assertEquals("new_player", decoded.name);
        assertEquals("some message", decoded.message);
    }

    @Test
    public void parsesNetworkEntityIdAndDirectSend() {
        ProgramDef program = new SceneMaxLanguageParser(null, "").parse(
                "player => fighter1_native: multiplayer\n"
                        + "var player_id = player.network.id\n"
                        + "player.network.send(\"assign_fighter\", \"slot1\")");

        assertTrue(program.syntaxErrors == null || program.syntaxErrors.isEmpty());
        assertEquals(3, program.actions.size());
        assertTrue(program.actions.get(2) instanceof NetworkEntitySendCommand);
        NetworkEntitySendCommand send = (NetworkEntitySendCommand) program.actions.get(2);
        assertEquals("player", send.targetVar);
        assertNotNull(send.eventNameExpr);
        assertNotNull(send.messageExpr);
    }

    @Test
    public void parsesSynchronizedNetworkEntityDataAssignment() {
        ProgramDef program = new SceneMaxLanguageParser(null, "").parse(
                "player => fighter1_native: multiplayer\n"
                        + "player.data.slot# = 1");

        assertTrue(program.syntaxErrors == null || program.syntaxErrors.isEmpty());
        assertEquals(2, program.actions.size());
        SetUserDataCommand command = (SetUserDataCommand) program.actions.get(1);
        assertEquals("player", command.varName);
        assertEquals("slot", command.fieldName);
        assertTrue(command.syncNetworkEntityData);
    }

    @Test
    public void sendsDirectNetworkEventToRegisteredEntityOwnerWithMessage() throws Exception {
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

            VariableDef varDef = new VariableDef();
            varDef.varName = "player";
            varDef.varType = VariableDef.VAR_TYPE_3D;
            varDef.isMultiplayer = true;

            component.registerEntity("player@1", varDef, "fighter1_native");
            setField(component, "channel", client);
            setField(component, "clientId", 7);
            setField(component, "networkReady", true);
            setField(component, "initialSyncPending", false);
            setRegisteredEntityNetworkId(component, "player@1", 101);

            assertEquals(101, component.networkEntityId("player@1"));
            component.sendNetworkEventToEntity("player@1", "assign_fighter", "slot1");

            ByteBuffer packet = receiveRequiredPacket(server);
            assertHeader(packet, NETWORK_EVENT);
            assertEquals(7, Short.toUnsignedInt(packet.getShort()));
            byte[] eventBytes = new byte[packet.remaining()];
            packet.get(eventBytes);
            MultiplayerNetworkComponent.NetworkEventPayload decoded =
                    MultiplayerNetworkComponent.decodeNetworkEventPayload(eventBytes);
            assertEquals("assign_fighter", decoded.name);
            assertEquals("slot1", decoded.message);
        } finally {
            component.close();
            client.close();
            server.close();
        }
    }

    @Test
    public void sendsDirectNetworkEventToRemoteEntityOwnerWithMessage() throws Exception {
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

            setField(component, "channel", client);
            setField(component, "clientId", 7);
            setField(component, "networkReady", true);
            setField(component, "initialSyncPending", false);
            addRemoteEntity(component, "mp_remote_101@1", 101, 11);

            assertEquals(101, component.networkEntityId("mp_remote_101@1"));
            component.sendNetworkEventToEntity("mp_remote_101@1", "assign_fighter", "slot1");

            ByteBuffer packet = receiveRequiredPacket(server);
            assertHeader(packet, NETWORK_EVENT);
            assertEquals(11, Short.toUnsignedInt(packet.getShort()));
            byte[] eventBytes = new byte[packet.remaining()];
            packet.get(eventBytes);
            MultiplayerNetworkComponent.NetworkEventPayload decoded =
                    MultiplayerNetworkComponent.decodeNetworkEventPayload(eventBytes);
            assertEquals("assign_fighter", decoded.name);
            assertEquals("slot1", decoded.message);
        } finally {
            component.close();
            client.close();
            server.close();
        }
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
    public void parsesNetworkVariableDeclaration() {
        ProgramDef program = new SceneMaxLanguageParser(null, "").parse("network var fighters_count = 0");

        assertTrue(program.syntaxErrors == null || program.syntaxErrors.isEmpty());
        assertEquals(1, program.actions.size());
        assertTrue(program.actions.get(0) instanceof com.scenemaxeng.compiler.VariableAssignmentCommand);
        VariableDef var = program.getVar("fighters_count");
        assertTrue(var.isNetwork);
        VariableDeclarationCommand declaration = var.declaration;
        assertTrue(declaration.isNetwork);
    }

    @Test
    public void encodesAndDecodesNetworkVariableArrays() throws Exception {
        MultiplayerNetworkComponent component = new MultiplayerNetworkComponent(null);
        Method encode = MultiplayerNetworkComponent.class.getDeclaredMethod("encodeNetworkVariableValue", Object.class);
        Method decode = MultiplayerNetworkComponent.class.getDeclaredMethod("decodeNetworkVariableValue", String.class);
        encode.setAccessible(true);
        decode.setAccessible(true);

        List<Object> value = Arrays.<Object>asList(1d, "two", true, null, Arrays.<Object>asList(2d, "nested"));
        String encoded = (String) encode.invoke(component, value);
        Object decoded = decode.invoke(component, encoded);

        assertTrue(decoded instanceof List);
        assertEquals(value, decoded);
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

    @Test
    public void joinSessionWaitsForInitialSyncCompletePacket() throws Exception {
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

            setField(component, "channel", client);
            setField(component, "clientId", 7);

            component.joinSession(42);
            assertFalse(component.isJoinSessionComplete(42));

            sendLoginAccepted(server, 7, 42, "selected_session");
            component.update(0f);
            assertFalse(component.isReady());
            assertFalse(component.isJoinSessionComplete(42));

            sendHeaderOnly(server, INITIAL_SYNC_COMPLETE, 7);
            component.update(0f);
            assertTrue(component.isReady());
            assertTrue(component.isJoinSessionComplete(42));
        } finally {
            component.close();
            client.close();
            server.close();
        }
    }

    @Test
    public void networkReadyFallsBackWhenServerDoesNotSendInitialSyncComplete() throws Exception {
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

            setField(component, "channel", client);

            sendLoginAccepted(server, 7, 1, "initial_session");
            component.update(0f);
            assertFalse(component.isReady());

            component.update(0.2f);
            assertTrue(component.isReady());
        } finally {
            component.close();
            client.close();
            server.close();
        }
    }

    @Test
    public void joinSessionFallsBackWhenServerDoesNotSendInitialSyncComplete() throws Exception {
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

            setField(component, "channel", client);
            setField(component, "clientId", 7);

            component.joinSession(42);
            sendLoginAccepted(server, 7, 42, "selected_session");
            component.update(0f);
            assertFalse(component.isJoinSessionComplete(42));

            component.update(0.2f);
            assertTrue(component.isJoinSessionComplete(42));
        } finally {
            component.close();
            client.close();
            server.close();
        }
    }

    @Test
    public void pendingJoinIgnoresInitialSessionSyncComplete() throws Exception {
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

            setField(component, "channel", client);
            component.joinSession(42);

            sendLoginAccepted(server, 7, 1, "initial_session");
            component.update(0f);
            sendHeaderOnly(server, INITIAL_SYNC_COMPLETE, 7);
            component.update(0f);
            assertFalse(component.isJoinSessionComplete(42));

            sendLoginAccepted(server, 7, 42, "selected_session");
            component.update(0f);
            assertFalse(component.isReady());
            assertFalse(component.isJoinSessionComplete(42));

            sendHeaderOnly(server, INITIAL_SYNC_COMPLETE, 7);
            component.update(0f);
            assertTrue(component.isJoinSessionComplete(42));
        } finally {
            component.close();
            client.close();
            server.close();
        }
    }

    @Test
    public void localCreatesWaitForInitialSceneSyncToComplete() throws Exception {
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

            setField(component, "channel", client);
            setField(component, "clientId", 7);
            setField(component, "networkReady", true);
            setField(component, "initialSyncPending", true);

            VariableDef varDef = new VariableDef();
            varDef.varName = "player";
            varDef.varType = VariableDef.VAR_TYPE_3D;
            varDef.isMultiplayer = true;

            component.registerEntity("player@1", varDef, "fighter1_native",
                    "{network_entity} => fighter1_native: pos (0,0,0)");

            ByteBuffer received = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);
            assertNull(server.receive(received));

            sendHeaderOnly(server, INITIAL_SYNC_COMPLETE, 7);
            component.update(0f);

            received.clear();
            assertNotNull(server.receive(received));
            received.flip();
            assertEquals(MAGIC, received.getInt());
            assertEquals(VERSION, received.get());
            assertEquals(CREATE_ENTITY_REQUEST, received.get());
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

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void setRegisteredEntityNetworkId(MultiplayerNetworkComponent component,
                                              String runtimeName,
                                              int networkId) throws Exception {
        Object entity = registeredEntity(component, runtimeName);
        setField(entity, "networkEntityId", networkId);
    }

    private Object registeredEntity(MultiplayerNetworkComponent component, String runtimeName) throws Exception {
        Field field = MultiplayerNetworkComponent.class.getDeclaredField("localEntities");
        field.setAccessible(true);
        Map<String, Object> localEntities = (Map<String, Object>) field.get(component);
        Object entity = localEntities.get(runtimeName);
        assertNotNull(entity);
        return entity;
    }

    private void addRemoteEntity(MultiplayerNetworkComponent component,
                                 String runtimeName,
                                 int networkId,
                                 int ownerClientId) throws Exception {
        Class<?> remoteEntityClass = Class.forName(
                "com.scenemaxeng.projector.MultiplayerNetworkComponent$RemoteEntity");
        java.lang.reflect.Constructor<?> constructor = remoteEntityClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object entity = constructor.newInstance();
        setField(entity, "runtimeName", runtimeName);
        setField(entity, "networkEntityId", networkId);
        setField(entity, "ownerClientId", ownerClientId);

        Field field = MultiplayerNetworkComponent.class.getDeclaredField("remoteEntities");
        field.setAccessible(true);
        Map<Integer, Object> remoteEntities = (Map<Integer, Object>) field.get(component);
        remoteEntities.put(networkId, entity);
    }

    private ByteBuffer receiveRequiredPacket(DatagramChannel server) throws Exception {
        ByteBuffer packet = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 20; i++) {
            if (server.receive(packet) != null) {
                packet.flip();
                return packet;
            }
            Thread.sleep(10);
        }
        fail("Expected multiplayer packet");
        return packet;
    }

    private void assertHeader(ByteBuffer packet, byte expectedType) {
        assertEquals(MAGIC, packet.getInt());
        assertEquals(VERSION, packet.get());
        assertEquals(expectedType, packet.get());
        assertEquals(7, Short.toUnsignedInt(packet.getShort()));
    }

    private String readFixedString(ByteBuffer packet, int length) {
        byte[] bytes = new byte[Math.min(length, packet.remaining())];
        packet.get(bytes);
        int end = 0;
        while (end < bytes.length && bytes[end] != 0) {
            end++;
        }
        return new String(bytes, 0, end, StandardCharsets.UTF_8);
    }

    private void sendHeaderOnly(DatagramChannel server, byte type, int senderClientId) throws Exception {
        ByteBuffer packet = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        writeHeader(packet, type, senderClientId);
        packet.flip();
        server.write(packet);
    }

    private void sendLoginAccepted(DatagramChannel server, int clientId, int sessionId, String sessionName) throws Exception {
        ByteBuffer packet = ByteBuffer.allocate(78).order(ByteOrder.LITTLE_ENDIAN);
        writeHeader(packet, LOGIN_ACCEPTED, clientId);
        packet.putShort((short) clientId);
        packet.putInt(sessionId);
        putFixedString(packet, sessionName, 64);
        packet.flip();
        server.write(packet);
    }

    private void sendCreateAccepted(DatagramChannel server,
                                    int clientId,
                                    int networkId,
                                    int createRequestId) throws Exception {
        ByteBuffer packet = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        writeHeader(packet, CREATE_ENTITY_ACCEPTED, clientId);
        packet.putInt(networkId);
        packet.putInt(createRequestId);
        packet.flip();
        server.write(packet);
    }

    private void writeHeader(ByteBuffer packet, byte type, int senderClientId) {
        packet.putInt(MAGIC);
        packet.put(VERSION);
        packet.put(type);
        packet.putShort((short) senderClientId);
    }

    private void putFixedString(ByteBuffer packet, String value, int length) {
        byte[] bytes = (value == null ? "" : value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int count = Math.min(bytes.length, length - 1);
        packet.put(bytes, 0, count);
        for (int i = count; i < length; i++) {
            packet.put((byte) 0);
        }
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

    private static class CapturingSceneMaxApp extends SceneMaxApp {
        SceneMaxBaseController registeredController;
        boolean characterControlled;

        @Override
        public int registerController(SceneMaxBaseController c) {
            registeredController = c;
            return 0;
        }

        @Override
        public boolean isCharacterControlledModel(String runtimeName) {
            return characterControlled;
        }
    }
}
