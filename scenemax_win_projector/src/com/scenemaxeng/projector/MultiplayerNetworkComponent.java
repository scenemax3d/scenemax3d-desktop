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
import java.util.HashMap;
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
    private static final byte COMMAND_DISPATCH = 20;
    private static final byte TRANSFORM_CORRECTION = 21;
    private static final byte SNAPSHOT = 30;
    private static final int MAX_PACKET_SIZE = 1200;

    private final SceneMaxApp app;
    private final Logger logger = Logger.getLogger(MultiplayerNetworkComponent.class.getName());
    private final ByteBuffer receiveBuffer = ByteBuffer.allocateDirect(MAX_PACKET_SIZE).order(ByteOrder.LITTLE_ENDIAN);
    private final Map<String, RegisteredEntity> localEntities = new HashMap<>();
    private final Queue<String> pendingCreateAcks = new ArrayDeque<>();
    private DatagramChannel channel;
    private int stationId;
    private long sessionId;
    private String sessionName = "";
    private String activeSceneId = "main";
    private String playerName = "";
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
        if (stationId != 0) {
            sendCreateEntity(entity);
        }
    }

    public void update(float tpf) {
        if (!isActive()) {
            return;
        }
        readPackets();
        if (stationId == 0) {
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
        if (!isActive() || stationId == 0 || commandText == null) {
            return;
        }
        byte[] commandBytes = commandText.getBytes(StandardCharsets.UTF_8);
        ByteBuffer packet = packet(COMMAND_DISPATCH, Math.min(commandBytes.length + 4, MAX_PACKET_SIZE - 8));
        packet.putInt(networkEntityId);
        packet.put(commandBytes, 0, Math.min(commandBytes.length, MAX_PACKET_SIZE - 12));
        send(packet);
    }

    public void close() {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException ignored) {
            }
        }
        channel = null;
        stationId = 0;
        pendingCreateAcks.clear();
    }

    public void joinScene(String sceneId) {
        activeSceneId = normalizeSceneId(sceneId);
        localEntities.clear();
        pendingCreateAcks.clear();
        if (isActive() && stationId != 0) {
            ByteBuffer packet = packet(JOIN_SCENE, 128);
            putFixedString(packet, activeSceneId, 128);
            send(packet);
        }
    }

    private void sendLogin(String password, boolean createSession, long requestedSessionId, String requestedSessionName) {
        ByteBuffer packet = packet(LOGIN_REQUEST, 293);
        packet.put(sha256(password == null ? "" : password));
        packet.put((byte) (createSession ? 1 : 0));
        packet.putInt((int) requestedSessionId);
        putFixedString(packet, requestedSessionName, 64);
        putFixedString(packet, activeSceneId, 128);
        putFixedString(packet, playerName, 64);
        send(packet);
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
                int senderStationId = Short.toUnsignedInt(receiveBuffer.getShort());
                handlePacket(type, senderStationId, receiveBuffer);
            }
        } catch (IOException ex) {
            logger.log(Level.WARNING, "SceneMax multiplayer receive failed", ex);
            close();
        }
    }

    private void handlePacket(byte type, int senderStationId, ByteBuffer payload) {
        if (type == LOGIN_ACCEPTED && payload.remaining() >= 70) {
            stationId = Short.toUnsignedInt(payload.getShort());
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
            String runtimeName = pendingCreateAcks.poll();
            RegisteredEntity entity = runtimeName == null ? null : localEntities.get(runtimeName);
            if (entity != null) {
                entity.networkEntityId = networkId;
            }
        } else if (type == COMMAND_DISPATCH && payload.remaining() > 4) {
            payload.getInt();
            byte[] bytes = new byte[payload.remaining()];
            payload.get(bytes);
            String command = new String(bytes, StandardCharsets.UTF_8).trim();
            if (!command.isEmpty()) {
                app.runPartialCode(command, null, false);
            }
        } else if (type == TRANSFORM_CORRECTION) {
            logger.fine("Received multiplayer transform correction from station " + senderStationId + ".");
        } else if (type == SNAPSHOT) {
            logger.fine("Received multiplayer snapshot from station " + senderStationId + " with " + payload.remaining() + " bytes.");
        }
    }

    private void sendHeaderOnly(byte type) {
        send(packet(type, 0));
    }

    private ByteBuffer packet(byte type, int payloadSize) {
        ByteBuffer packet = ByteBuffer.allocate(8 + payloadSize).order(ByteOrder.LITTLE_ENDIAN);
        packet.putInt(MAGIC);
        packet.put(VERSION);
        packet.put(type);
        packet.putShort((short) stationId);
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

    private static class RegisteredEntity {
        String runtimeName;
        VariableDef varDef;
        String archetypeName;
        int networkEntityId;
    }
}
