package com.scenemaxeng.projector;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.scenemaxeng.compiler.VariableDef;

import java.io.IOException;
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
    private static final int SNAPSHOT_ENTITY_SIZE = 228;

    private final SceneMaxApp app;
    private final Logger logger = Logger.getLogger(MultiplayerNetworkComponent.class.getName());
    private final ByteBuffer receiveBuffer = ByteBuffer.allocateDirect(MAX_PACKET_SIZE).order(ByteOrder.LITTLE_ENDIAN);
    private final Map<String, RegisteredEntity> localEntities = new HashMap<>();
    private final Map<Integer, RemoteEntity> remoteEntities = new HashMap<>();
    private final Map<Integer, TransformState> pendingTransforms = new HashMap<>();
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
        if (runtimeName == null || varDef == null || !varDef.isMultiplayer) {
            return;
        }
        RegisteredEntity entity = new RegisteredEntity();
        entity.runtimeName = runtimeName;
        entity.varDef = varDef;
        entity.archetypeName = archetypeName == null ? "" : archetypeName;
        localEntities.put(runtimeName, entity);
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
        if (clientId == 0) {
            return;
        }
        heartbeatTimer += tpf;
        correctionTimer += tpf;
        if (heartbeatTimer >= 1.0f) {
            heartbeatTimer = 0;
            sendHeaderOnly(HEARTBEAT);
        }
        if (correctionTimer >= 0.10f) {
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
        send(packet);
    }

    public void dispatchCommand(String runtimeName, String commandText) {
        if (runtimeName == null || commandText == null || commandText.trim().isEmpty()) {
            return;
        }
        RegisteredEntity entity = localEntities.get(runtimeName);
        if (entity == null) {
            return;
        }
        if (entity.networkEntityId == 0) {
            entity.pendingCommands.add(commandText);
            return;
        }
        sendCommand(entity.networkEntityId, commandText);
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
    }

    public void joinScene(String sceneId) {
        activeSceneId = normalizeSceneId(sceneId);
        removeRemoteEntities();
        pendingTransforms.clear();
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
        ByteBuffer packet = packet(CREATE_ENTITY_REQUEST, 128);
        putFixedString(packet, entity.archetypeName, 64);
        putFixedString(packet, playerName, 64);
        pendingCreateAcks.add(entity.runtimeName);
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
                    flushPendingCommands(entity);
                }
            } else if (payload.remaining() >= 128) {
                String archetype = readFixedString(payload, 64);
                String remotePlayerName = readFixedString(payload, 64);
                createOrUpdateRemoteEntity(networkId, archetype, remotePlayerName, null, 0, "");
            }
        } else if (type == DESTROY_ENTITY && payload.remaining() >= 4) {
            removeRemoteEntity(payload.getInt());
        } else if (type == COMMAND_DISPATCH && payload.remaining() > 4) {
            int networkId = payload.getInt();
            byte[] bytes = new byte[payload.remaining()];
            payload.get(bytes);
            String command = new String(bytes, StandardCharsets.UTF_8).trim();
            if (!command.isEmpty()) {
                command = resolveRemoteCommandTarget(networkId, command);
                app.runPartialCode(command, null, false);
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
            if (ownerClientId == clientId || isLocalNetworkEntity(networkId)) {
                continue;
            }
            createOrUpdateRemoteEntity(networkId, archetype, remotePlayerName, transform, animationIndex, animation);
        }
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

    private TransformState readTransformState(ByteBuffer payload) {
        TransformState transform = new TransformState();
        transform.position = new Vector3f(payload.getFloat(), payload.getFloat(), payload.getFloat());
        transform.rotation = new Quaternion(payload.getFloat(), payload.getFloat(), payload.getFloat(), payload.getFloat());
        return transform;
    }

    private void createOrUpdateRemoteEntity(int networkId, String archetype, String remotePlayerName,
                                            TransformState transform, int animationIndex, String animation) {
        if (networkId == 0 || archetype == null || archetype.trim().isEmpty()) {
            return;
        }
        RemoteEntity entity = remoteEntities.get(networkId);
        if (entity == null) {
            entity = new RemoteEntity();
            entity.networkEntityId = networkId;
            entity.sourceName = "mp_remote_" + networkId;
            entity.archetypeName = sanitizeIdentifier(archetype);
            entity.playerName = remotePlayerName == null ? "" : remotePlayerName;
            createRemoteModel(entity, transform);
            remoteEntities.put(networkId, entity);
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
        String code = entity.sourceName + " => " + entity.archetypeName
                + ": pos (" + position.x + "," + position.y + "," + position.z + ");";
        app.runPartialCode(code, null, false);
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

    private void removeRemoteEntities() {
        for (RemoteEntity entity : remoteEntities.values()) {
            if (entity.runtimeName != null) {
                app.killModel(entity.runtimeName);
            }
        }
        remoteEntities.clear();
    }

    private void removeRemoteEntity(int networkId) {
        RemoteEntity entity = remoteEntities.remove(networkId);
        pendingTransforms.remove(networkId);
        if (entity != null && entity.runtimeName != null) {
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

    private static class RegisteredEntity {
        String runtimeName;
        VariableDef varDef;
        String archetypeName;
        int networkEntityId;
        List<String> pendingCommands = new ArrayList<>();
    }

    private static class RemoteEntity {
        int networkEntityId;
        String sourceName;
        String runtimeName;
        String archetypeName;
        String playerName;
        int animationIndex;
        String animation;
    }

    private static class TransformState {
        Vector3f position;
        Quaternion rotation;
    }
}
