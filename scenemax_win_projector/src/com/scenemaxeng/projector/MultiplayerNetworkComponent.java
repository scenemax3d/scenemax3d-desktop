package com.scenemaxeng.projector;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.scenemaxeng.compiler.VariableDef;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MultiplayerNetworkComponent {

    private static final int MAGIC = 0x504d5853;
    private static final byte VERSION = 1;
    private static final byte LOGIN_REQUEST = 1;
    private static final byte LOGIN_ACCEPTED = 2;
    private static final byte LOGIN_REJECTED = 3;
    private static final byte HEARTBEAT = 4;
    private static final byte JOIN_SCENE = 5;
    private static final byte CREATE_ENTITY_REQUEST = 10;
    private static final byte CREATE_ENTITY_ACCEPTED = 11;
    private static final byte DESTROY_ENTITY = 12;
    private static final byte COMMAND_DISPATCH = 20;
    private static final byte TRANSFORM_CORRECTION = 21;
    private static final byte SNAPSHOT = 30;
    private static final byte DISCONNECT = 40;
    private static final int MAX_PACKET_SIZE = 1200;
    private static final int SPAWN_COMMAND_SIZE = 128;
    private static final int SNAPSHOT_ENTITY_SIZE = 228 + SPAWN_COMMAND_SIZE;
    private static final float CORRECTION_INTERVAL_SECONDS = 0.25f;
    private static final float POSITION_EPSILON_SQUARED = 0.0004f;
    private static final float ROTATION_EPSILON = 0.002f;

    private final SceneMaxApp app;
    private final Logger logger = Logger.getLogger(MultiplayerNetworkComponent.class.getName());
    private final ByteBuffer receiveBuffer = ByteBuffer.allocateDirect(MAX_PACKET_SIZE).order(ByteOrder.LITTLE_ENDIAN);
    private final Map<String, RegisteredEntity> localEntities = new HashMap<>();
    private final Map<Integer, RemoteEntity> remoteEntities = new HashMap<>();
    private final Map<Integer, TransformState> pendingTransforms = new HashMap<>();
    private final Map<Integer, List<String>> pendingRemoteCommands = new HashMap<>();
    private final Map<String, TransformState> lastSentCorrections = new HashMap<>();
    private final Queue<String> pendingCreateAcks = new ArrayDeque<>();
    private DatagramChannel channel;
    private int clientId;
    private long sessionId;
    private String sessionName = "";
    private String activeSceneId = "main";
    private String playerName = "";
    private String projectGuid = "";
    private float heartbeatTimer;
    private float correctionTimer;

    public MultiplayerNetworkComponent(SceneMaxApp app) {
        this.app = app;
    }

    public boolean isActive() {
        return channel != null && channel.isOpen();
    }

    public void startFromSystemProperties() {
        String server = readSetting("scenemax.multiplayer.server", "SCENEMAX_MULTIPLAYER_SERVER");
        if (server == null || server.trim().isEmpty()) {
            return;
        }
        int port = parseInt(readSetting("scenemax.multiplayer.port", "SCENEMAX_MULTIPLAYER_PORT"), 9001);
        String password = readSetting("scenemax.multiplayer.password", "SCENEMAX_MULTIPLAYER_PASSWORD");
        long requestedSessionId = parseLong(readSetting("scenemax.multiplayer.sessionId", "SCENEMAX_MULTIPLAYER_SESSION_ID"), 0);
        String requestedSessionName = readSetting("scenemax.multiplayer.sessionName", "SCENEMAX_MULTIPLAYER_SESSION_NAME");
        boolean createSession = requestedSessionId == 0 || Boolean.parseBoolean(readSetting("scenemax.multiplayer.createSession", "SCENEMAX_MULTIPLAYER_CREATE_SESSION"));
        playerName = readSetting("scenemax.multiplayer.player", "SCENEMAX_MULTIPLAYER_PLAYER");
        projectGuid = resolveProjectGuid();
        activeSceneId = normalizeSceneId(readSetting("scenemax.multiplayer.scene", "SCENEMAX_MULTIPLAYER_SCENE"));
        try {
            channel = DatagramChannel.open();
            channel.configureBlocking(false);
            channel.connect(new InetSocketAddress(server.trim(), port));
            sendLogin(password == null ? "" : password, createSession, requestedSessionId, requestedSessionName);
            logger.info("SceneMax multiplayer UDP client connecting to " + server + ":" + port);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Failed to start SceneMax multiplayer client", ex);
            close();
        }
    }

    public void registerEntity(String runtimeName, VariableDef varDef, String archetypeName) {
        registerEntity(runtimeName, varDef, archetypeName, "");
    }

    public void registerEntity(String runtimeName, VariableDef varDef, String archetypeName, String spawnCommand) {
        if (runtimeName == null || varDef == null || !varDef.isMultiplayer) {
            return;
        }
        RegisteredEntity entity = new RegisteredEntity();
        entity.runtimeName = runtimeName;
        entity.varDef = varDef;
        entity.archetypeName = archetypeName == null ? "" : archetypeName;
        entity.spawnCommand = spawnCommand == null ? "" : spawnCommand;
        localEntities.put(runtimeName, entity);
        logNet("registered local entity runtime=" + runtimeName
                + " type=" + varDef.varType
                + " archetype=" + entity.archetypeName
                + " spawn=\"" + entity.spawnCommand + "\"");
        if (clientId != 0) {
            sendCreateEntity(entity);
        }
    }

    public void update(float tpf) {
        if (!isActive()) {
            return;
        }
        readPackets();
        applyPendingTransforms();
        applyPendingRemoteCommands();
        applyPendingRemoteDestroys();
        if (clientId == 0) {
            return;
        }
        heartbeatTimer += tpf;
        correctionTimer += tpf;
        if (heartbeatTimer >= 1.0f) {
            heartbeatTimer = 0;
            sendHeaderOnly(HEARTBEAT);
        }
        if (correctionTimer >= CORRECTION_INTERVAL_SECONDS) {
            correctionTimer = 0;
            sendTransformCorrections();
        }
    }

    public void sendCommand(int networkEntityId, String commandText) {
        if (!isActive() || clientId == 0 || commandText == null) {
            return;
        }
        byte[] commandBytes = commandText.getBytes(StandardCharsets.UTF_8);
        ByteBuffer packet = packet(COMMAND_DISPATCH, Math.min(commandBytes.length + 4, MAX_PACKET_SIZE - 8));
        packet.putInt(networkEntityId);
        packet.put(commandBytes, 0, Math.min(commandBytes.length, MAX_PACKET_SIZE - 12));
        logNet("send command entity=" + networkEntityId + " command=\"" + commandText + "\"");
        send(packet);
    }

    public void dispatchCommand(String runtimeName, String commandText) {
        if (runtimeName == null || commandText == null || commandText.trim().isEmpty()) {
            return;
        }
        RegisteredEntity entity = localEntities.get(runtimeName);
        if (entity == null) {
            logNet("skip command for unregistered runtime=" + runtimeName + " command=\"" + commandText + "\"");
            return;
        }
        if (entity.networkEntityId == 0) {
            logNet("queue command until create ack runtime=" + runtimeName + " command=\"" + commandText + "\"");
            entity.pendingCommands.add(commandText);
            return;
        }
        sendCommand(entity.networkEntityId, commandText);
    }

    public void destroyEntity(String runtimeName) {
        if (runtimeName == null) {
            return;
        }
        RegisteredEntity entity = localEntities.get(runtimeName);
        if (entity != null && entity.networkEntityId == 0) {
            entity.destroyAfterCreateAck = true;
            entity.pendingCommands.clear();
            logNet("queue destroy until create ack runtime=" + runtimeName);
            return;
        }
        entity = localEntities.remove(runtimeName);
        lastSentCorrections.remove(runtimeName);
        if (entity == null || !isActive() || clientId == 0) {
            logNet("skip destroy runtime=" + runtimeName + " registered=" + (entity != null));
            return;
        }
        sendDestroyEntity(entity.networkEntityId, runtimeName);
    }

    public void close() {
        if (channel != null) {
            try {
                if (channel.isOpen() && clientId != 0) {
                    ByteBuffer packet = packet(DISCONNECT, 0);
                    packet.flip();
                    channel.write(packet);
                }
                channel.close();
            } catch (IOException ignored) {
            }
        }
        channel = null;
        clientId = 0;
        pendingCreateAcks.clear();
        removeRemoteEntities();
        pendingTransforms.clear();
        pendingRemoteCommands.clear();
        lastSentCorrections.clear();
    }

    public void joinScene(String sceneId) {
        activeSceneId = normalizeSceneId(sceneId);
        removeRemoteEntities();
        pendingTransforms.clear();
        pendingRemoteCommands.clear();
        lastSentCorrections.clear();
        localEntities.clear();
        pendingCreateAcks.clear();
        if (isActive() && clientId != 0) {
            ByteBuffer packet = packet(JOIN_SCENE, 128);
            putFixedString(packet, activeSceneId, 128);
            send(packet);
        }
    }

    private void sendLogin(String password, boolean createSession, long requestedSessionId, String requestedSessionName) {
        ByteBuffer packet = packet(LOGIN_REQUEST, 357);
        packet.put(sha256(password == null ? "" : password));
        putFixedString(packet, projectGuid, 64);
        packet.put((byte) (createSession ? 1 : 0));
        packet.putInt((int) requestedSessionId);
        putFixedString(packet, requestedSessionName, 64);
        putFixedString(packet, activeSceneId, 128);
        putFixedString(packet, playerName, 64);
        send(packet);
    }

    private String resolveProjectGuid() {
        String guid = readSetting("scenemax.multiplayer.projectGuid", "SCENEMAX_MULTIPLAYER_PROJECT_GUID");
        if (guid != null && !guid.trim().isEmpty()) {
            return guid.trim();
        }
        return app == null ? "" : app.getProjectGuidForNetwork();
    }

    private void sendCreateEntity(RegisteredEntity entity) {
        ByteBuffer packet = packet(CREATE_ENTITY_REQUEST, 128 + SPAWN_COMMAND_SIZE);
        putFixedString(packet, entity.archetypeName, 64);
        putFixedString(packet, playerName, 64);
        putFixedString(packet, entity.spawnCommand, SPAWN_COMMAND_SIZE);
        pendingCreateAcks.add(entity.runtimeName);
        logNet("send create runtime=" + entity.runtimeName
                + " archetype=" + entity.archetypeName
                + " spawn=\"" + entity.spawnCommand + "\"");
        send(packet);
    }

    private void sendTransformCorrections() {
        for (RegisteredEntity entity : localEntities.values()) {
            if (entity.networkEntityId == 0) {
                continue;
            }
            Spatial spatial = app.getEntitySpatial(entity.runtimeName, entity.varDef.varType);
            if (spatial == null) {
                continue;
            }
            Vector3f position = spatial.getLocalTranslation();
            Quaternion rotation = spatial.getLocalRotation();
            if (!shouldSendCorrection(entity.runtimeName, position, rotation)) {
                continue;
            }
            ByteBuffer packet = packet(TRANSFORM_CORRECTION, 32);
            packet.putInt(entity.networkEntityId);
            packet.putFloat(position.x);
            packet.putFloat(position.y);
            packet.putFloat(position.z);
            packet.putFloat(rotation.getX());
            packet.putFloat(rotation.getY());
            packet.putFloat(rotation.getZ());
            packet.putFloat(rotation.getW());
            send(packet);
        }
    }

    private boolean shouldSendCorrection(String runtimeName, Vector3f position, Quaternion rotation) {
        if (runtimeName == null || position == null || rotation == null) {
            return false;
        }
        TransformState previous = lastSentCorrections.get(runtimeName);
        if (previous != null
                && previous.position != null
                && previous.rotation != null
                && previous.position.distanceSquared(position) < POSITION_EPSILON_SQUARED
                && Math.abs(previous.rotation.dot(rotation)) > 1.0f - ROTATION_EPSILON) {
            return false;
        }
        TransformState current = new TransformState();
        current.position = position.clone();
        current.rotation = rotation.clone();
        lastSentCorrections.put(runtimeName, current);
        return true;
    }

    private void readPackets() {
        try {
            while (true) {
                receiveBuffer.clear();
                if (channel.receive(receiveBuffer) == null) {
                    return;
                }
                receiveBuffer.flip();
                if (receiveBuffer.remaining() < 8 || receiveBuffer.getInt() != MAGIC || receiveBuffer.get() != VERSION) {
                    continue;
                }
                byte type = receiveBuffer.get();
                int senderClientId = Short.toUnsignedInt(receiveBuffer.getShort());
                handlePacket(type, senderClientId, receiveBuffer);
            }
        } catch (IOException ex) {
            logger.log(Level.WARNING, "SceneMax multiplayer receive failed", ex);
            close();
        }
    }

    private void handlePacket(byte type, int senderClientId, ByteBuffer payload) {
        if (type == LOGIN_ACCEPTED && payload.remaining() >= 70) {
            clientId = Short.toUnsignedInt(payload.getShort());
            sessionId = Integer.toUnsignedLong(payload.getInt());
            sessionName = readFixedString(payload, 64);
            logger.info("SceneMax multiplayer joined session " + sessionId + " (" + sessionName + "), scene " + activeSceneId);
            for (RegisteredEntity entity : localEntities.values()) {
                sendCreateEntity(entity);
            }
        } else if (type == LOGIN_REJECTED) {
            logger.warning("SceneMax multiplayer login rejected.");
            close();
        } else if (type == CREATE_ENTITY_ACCEPTED && payload.remaining() >= 4) {
            int networkId = payload.getInt();
            if (senderClientId == clientId) {
                String runtimeName = pendingCreateAcks.poll();
                RegisteredEntity entity = runtimeName == null ? null : localEntities.get(runtimeName);
                if (entity != null) {
                    entity.networkEntityId = networkId;
                    logNet("create accepted runtime=" + runtimeName + " networkId=" + networkId);
                    if (entity.destroyAfterCreateAck) {
                        sendDestroyEntity(networkId, runtimeName);
                        localEntities.remove(runtimeName);
                        return;
                    }
                    flushPendingCommands(entity);
                }
            } else if (payload.remaining() >= 128) {
                String archetype = readFixedString(payload, 64);
                String remotePlayerName = readFixedString(payload, 64);
                String spawnCommand = payload.remaining() >= SPAWN_COMMAND_SIZE ? readFixedString(payload, SPAWN_COMMAND_SIZE) : "";
                logNet("receive remote create networkId=" + networkId
                        + " sender=" + senderClientId
                        + " archetype=" + archetype
                        + " spawn=\"" + spawnCommand + "\"");
                createOrUpdateRemoteEntity(networkId, archetype, remotePlayerName, transformOrNull(networkId), 0, "", spawnCommand);
            }
        } else if (type == DESTROY_ENTITY && payload.remaining() >= 4) {
            int networkId = payload.getInt();
            logNet("receive destroy networkId=" + networkId + " sender=" + senderClientId);
            destroyRemoteEntity(networkId);
        } else if (type == COMMAND_DISPATCH && payload.remaining() > 4) {
            int networkId = payload.getInt();
            byte[] bytes = new byte[payload.remaining()];
            payload.get(bytes);
            String command = new String(bytes, StandardCharsets.UTF_8).trim();
            if (!command.isEmpty()) {
                handleRemoteCommand(networkId, command);
            }
        } else if (type == TRANSFORM_CORRECTION && payload.remaining() >= 32) {
            handleTransformCorrection(payload);
        } else if (type == SNAPSHOT) {
            handleSnapshot(payload);
        }
    }

    private void flushPendingCommands(RegisteredEntity entity) {
        if (entity == null || entity.networkEntityId == 0 || entity.pendingCommands.isEmpty()) {
            return;
        }
        for (String command : new ArrayList<>(entity.pendingCommands)) {
            sendCommand(entity.networkEntityId, command);
        }
        entity.pendingCommands.clear();
    }

    private void sendDestroyEntity(int networkEntityId, String runtimeName) {
        if (networkEntityId == 0 || !isActive() || clientId == 0) {
            return;
        }
        ByteBuffer packet = packet(DESTROY_ENTITY, 4);
        packet.putInt(networkEntityId);
        logNet("send destroy entity=" + networkEntityId + " runtime=" + runtimeName);
        send(packet);
    }

    private void handleSnapshot(ByteBuffer payload) {
        if (payload.remaining() < 2) {
            return;
        }
        int count = Short.toUnsignedInt(payload.getShort());
        for (int i = 0; i < count && payload.remaining() >= SNAPSHOT_ENTITY_SIZE; i++) {
            int networkId = payload.getInt();
            int ownerClientId = Short.toUnsignedInt(payload.getShort());
            String archetype = readFixedString(payload, 64);
            String remotePlayerName = readFixedString(payload, 64);
            TransformState transform = readTransformState(payload);
            int animationIndex = Short.toUnsignedInt(payload.getShort());
            String animation = readFixedString(payload, 64);
            String spawnCommand = payload.remaining() >= SPAWN_COMMAND_SIZE ? readFixedString(payload, SPAWN_COMMAND_SIZE) : "";
            if (ownerClientId == clientId || isLocalNetworkEntity(networkId)) {
                continue;
            }
            createOrUpdateRemoteEntity(networkId, archetype, remotePlayerName, transform, animationIndex, animation, spawnCommand);
        }
    }

    private TransformState transformOrNull(int networkId) {
        return pendingTransforms.get(networkId);
    }

    private void handleTransformCorrection(ByteBuffer payload) {
        int networkId = payload.getInt();
        TransformState transform = readTransformState(payload);
        if (isLocalNetworkEntity(networkId)) {
            return;
        }
        if (!applyRemoteTransform(networkId, transform)) {
            pendingTransforms.put(networkId, transform);
        }
    }

    private void handleRemoteCommand(int networkId, String command) {
        if (networkId == 0 || command == null || command.trim().isEmpty()) {
            return;
        }
        RemoteEntity entity = remoteEntities.get(networkId);
        if (entity == null || !isRemoteEntityReady(entity)) {
            pendingRemoteCommands.computeIfAbsent(networkId, id -> new ArrayList<>()).add(command);
            logNet("queue remote command networkId=" + networkId + " command=\"" + command + "\"");
            return;
        }
        runRemoteCommand(networkId, command);
    }

    private void runRemoteCommand(int networkId, String command) {
        String resolved = resolveRemoteCommandTarget(networkId, command);
        logNet("receive command networkId=" + networkId + " command=\"" + resolved + "\"");
        app.runNetworkMultiplayerCommand(resolved);
    }

    private TransformState readTransformState(ByteBuffer payload) {
        TransformState transform = new TransformState();
        transform.position = new Vector3f(payload.getFloat(), payload.getFloat(), payload.getFloat());
        transform.rotation = new Quaternion(payload.getFloat(), payload.getFloat(), payload.getFloat(), payload.getFloat());
        return transform;
    }

    private void createOrUpdateRemoteEntity(int networkId, String archetype, String remotePlayerName,
                                            TransformState transform, int animationIndex, String animation) {
        createOrUpdateRemoteEntity(networkId, archetype, remotePlayerName, transform, animationIndex, animation, "");
    }

    private void createOrUpdateRemoteEntity(int networkId, String archetype, String remotePlayerName,
                                            TransformState transform, int animationIndex, String animation,
                                            String spawnCommand) {
        if (networkId == 0 || archetype == null || archetype.trim().isEmpty()) {
            return;
        }
        RemoteEntity entity = remoteEntities.get(networkId);
        if (entity == null) {
            entity = new RemoteEntity();
            entity.networkEntityId = networkId;
            entity.sourceName = "mp_remote_" + networkId;
            entity.archetypeName = sanitizeIdentifier(archetype);
            entity.varType = archetypeVarType(entity.archetypeName);
            entity.playerName = remotePlayerName == null ? "" : remotePlayerName;
            entity.spawnCommand = spawnCommand == null ? "" : spawnCommand.trim();
            remoteEntities.put(networkId, entity);
            createRemoteModel(entity, transform);
        }
        entity.animationIndex = animationIndex;
        entity.animation = animation == null ? "" : animation;
        if (transform != null && !applyRemoteTransform(networkId, transform)) {
            pendingTransforms.put(networkId, transform);
        }
    }

    private void createRemoteModel(RemoteEntity entity, TransformState transform) {
        if (entity.archetypeName.isEmpty()) {
            return;
        }
        Vector3f position = transform == null ? Vector3f.ZERO : transform.position;
        String code;
        if (entity.spawnCommand != null && !entity.spawnCommand.isBlank()) {
            code = resolveRemoteCommandTarget(entity.networkEntityId, entity.spawnCommand);
        } else {
            code = entity.sourceName + " => " + entity.archetypeName
                    + ": pos (" + position.x + "," + position.y + "," + position.z + ");";
        }
        logNet("create remote runtime source=" + entity.sourceName
                + " type=" + entity.varType
                + " code=\"" + code + "\"");
        app.runNetworkMultiplayerCommand(code);
        int scopeId = app.getMainScopeIdForNetwork();
        entity.runtimeName = entity.sourceName + "@" + scopeId;
    }

    private boolean applyRemoteTransform(int networkId, TransformState transform) {
        RemoteEntity entity = remoteEntities.get(networkId);
        if (entity == null || entity.runtimeName == null || transform == null) {
            return false;
        }
        Spatial spatial = app.getEntitySpatial(entity.runtimeName);
        if (spatial == null) {
            return false;
        }
        spatial.setLocalTranslation(transform.position);
        spatial.setLocalRotation(transform.rotation);
        return true;
    }

    private void applyPendingTransforms() {
        for (Map.Entry<Integer, TransformState> entry : new HashMap<>(pendingTransforms).entrySet()) {
            if (applyRemoteTransform(entry.getKey(), entry.getValue())) {
                pendingTransforms.remove(entry.getKey());
            }
        }
    }

    private void applyPendingRemoteCommands() {
        for (Map.Entry<Integer, List<String>> entry : new HashMap<>(pendingRemoteCommands).entrySet()) {
            RemoteEntity entity = remoteEntities.get(entry.getKey());
            if (entity == null) {
                pendingRemoteCommands.remove(entry.getKey());
                continue;
            }
            if (entity.destroyPending || !isRemoteEntityReady(entity)) {
                continue;
            }
            for (String command : new ArrayList<>(entry.getValue())) {
                runRemoteCommand(entry.getKey(), command);
            }
            pendingRemoteCommands.remove(entry.getKey());
        }
    }

    private void applyPendingRemoteDestroys() {
        for (Map.Entry<Integer, RemoteEntity> entry : new HashMap<>(remoteEntities).entrySet()) {
            RemoteEntity entity = entry.getValue();
            if (entity != null && entity.destroyPending && isRemoteEntityReady(entity)) {
                removeRemoteEntity(entry.getKey());
            }
        }
    }

    private void removeRemoteEntities() {
        for (RemoteEntity entity : remoteEntities.values()) {
            removeRemoteRuntimeEntity(entity);
        }
        remoteEntities.clear();
        pendingRemoteCommands.clear();
    }

    private void removeRemoteEntity(int networkId) {
        RemoteEntity entity = remoteEntities.remove(networkId);
        pendingTransforms.remove(networkId);
        pendingRemoteCommands.remove(networkId);
        removeRemoteRuntimeEntity(entity);
    }

    private void destroyRemoteEntity(int networkId) {
        RemoteEntity entity = remoteEntities.get(networkId);
        if (entity == null) {
            pendingRemoteCommands.remove(networkId);
            pendingTransforms.remove(networkId);
            return;
        }
        if (!isRemoteEntityReady(entity)) {
            entity.destroyPending = true;
            pendingRemoteCommands.remove(networkId);
            pendingTransforms.remove(networkId);
            logNet("defer destroy until remote entity exists networkId=" + networkId);
            return;
        }
        removeRemoteEntity(networkId);
    }

    private boolean isRemoteEntityReady(RemoteEntity entity) {
        return entity != null && entity.runtimeName != null && app.getEntitySpatial(entity.runtimeName) != null;
    }

    private void removeRemoteRuntimeEntity(RemoteEntity entity) {
        if (entity == null || entity.runtimeName == null) {
            return;
        }
        if (entity.varType == VariableDef.VAR_TYPE_SPHERE) {
            app.killSphere(entity.runtimeName);
        } else if (entity.varType == VariableDef.VAR_TYPE_BOX) {
            app.killBox(entity.runtimeName);
        } else {
            app.killModel(entity.runtimeName);
        }
    }

    private boolean isLocalNetworkEntity(int networkId) {
        if (networkId == 0) {
            return false;
        }
        for (RegisteredEntity entity : localEntities.values()) {
            if (entity.networkEntityId == networkId) {
                return true;
            }
        }
        return false;
    }

    private String resolveRemoteCommandTarget(int networkId, String command) {
        RemoteEntity entity = remoteEntities.get(networkId);
        if (entity == null || entity.sourceName == null) {
            return command;
        }
        return command
                .replace("{network_entity}", entity.sourceName)
                .replace("{networkEntity}", entity.sourceName)
                .replace("$network_entity", entity.sourceName)
                .replace("$networkEntity", entity.sourceName);
    }

    private void sendHeaderOnly(byte type) {
        send(packet(type, 0));
    }

    private ByteBuffer packet(byte type, int payloadSize) {
        ByteBuffer packet = ByteBuffer.allocate(8 + payloadSize).order(ByteOrder.LITTLE_ENDIAN);
        packet.putInt(MAGIC);
        packet.put(VERSION);
        packet.put(type);
        packet.putShort((short) clientId);
        return packet;
    }

    private void send(ByteBuffer packet) {
        if (!isActive()) {
            return;
        }
        try {
            packet.flip();
            channel.write(packet);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "SceneMax multiplayer send failed", ex);
            close();
        }
    }

    private void logNet(String message) {
        String line = "[SceneMax MP] " + message;
        logger.info(line);
        System.err.println(line);
        try {
            Files.writeString(Path.of("scenemax-multiplayer-client.log"),
                    line + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    private void putFixedString(ByteBuffer packet, String value, int length) {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        int count = Math.min(bytes.length, length - 1);
        packet.put(bytes, 0, count);
        for (int i = count; i < length; i++) {
            packet.put((byte) 0);
        }
    }

    private String readSetting(String property, String env) {
        String value = System.getProperty(property);
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }
        value = System.getenv(env);
        return value == null ? "" : value.trim();
    }

    private int parseInt(String raw, int fallback) {
        try {
            return raw == null || raw.trim().isEmpty() ? fallback : Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private long parseLong(String raw, long fallback) {
        try {
            return raw == null || raw.trim().isEmpty() ? fallback : Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private byte[] sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is unavailable.", ex);
        }
    }

    private String normalizeSceneId(String sceneId) {
        if (sceneId == null) {
            return "main";
        }
        String normalized = sceneId.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isEmpty() ? "main" : normalized;
    }

    private String readFixedString(ByteBuffer buffer, int length) {
        byte[] bytes = new byte[Math.min(length, buffer.remaining())];
        buffer.get(bytes);
        int count = 0;
        while (count < bytes.length && bytes[count] != 0) {
            count++;
        }
        return new String(bytes, 0, count, StandardCharsets.UTF_8);
    }

    private String sanitizeIdentifier(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '_') {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private int archetypeVarType(String archetype) {
        if (archetype == null) {
            return VariableDef.VAR_TYPE_3D;
        }
        String normalized = archetype.trim().toLowerCase();
        if ("sphere".equals(normalized)) {
            return VariableDef.VAR_TYPE_SPHERE;
        }
        if ("box".equals(normalized)) {
            return VariableDef.VAR_TYPE_BOX;
        }
        return VariableDef.VAR_TYPE_3D;
    }

    private static class RegisteredEntity {
        String runtimeName;
        VariableDef varDef;
        String archetypeName;
        String spawnCommand;
        int networkEntityId;
        boolean destroyAfterCreateAck;
        List<String> pendingCommands = new ArrayList<>();
    }

    private static class RemoteEntity {
        int networkEntityId;
        String sourceName;
        String runtimeName;
        String archetypeName;
        int varType;
        String spawnCommand;
        String playerName;
        int animationIndex;
        String animation;
        boolean destroyPending;
    }

    private static class TransformState {
        Vector3f position;
        Quaternion rotation;
    }
}
