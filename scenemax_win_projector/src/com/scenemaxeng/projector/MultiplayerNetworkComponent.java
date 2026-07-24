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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

public class MultiplayerNetworkComponent {

    private static final int MAGIC = 0x504d5853;
    private static final byte VERSION = 1;
    private static final byte LOGIN_REQUEST = 1;
    private static final byte LOGIN_ACCEPTED = 2;
    private static final byte LOGIN_REJECTED = 3;
    private static final byte HEARTBEAT = 4;
    private static final byte JOIN_SCENE = 5;
    private static final byte JOIN_SESSION = 6;
    private static final byte CREATE_ENTITY_REQUEST = 10;
    private static final byte CREATE_ENTITY_ACCEPTED = 11;
    private static final byte DESTROY_ENTITY = 12;
    private static final byte COMMAND_DISPATCH = 20;
    private static final byte TRANSFORM_CORRECTION = 21;
    private static final byte ACTIVE_ACTION_START = 22;
    private static final byte ACTIVE_ACTION_END = 23;
    private static final byte NETWORK_EVENT = 24;
    private static final byte NETWORK_VARIABLE_UPDATE = 25;
    private static final byte SNAPSHOT = 30;
    private static final byte SERVER_STATE = 31;
    private static final byte DISCONNECT = 40;
    private static final int MAX_PACKET_SIZE = 1200;
    private static final int SOURCE_OBJECT_NAME_SIZE = 64;
    private static final int NETWORK_VARIABLE_NAME_SIZE = 64;
    private static final int NETWORK_VARIABLE_VALUE_SIZE = 256;
    private static final int NETWORK_VARIABLE_UPDATE_SIZE = NETWORK_VARIABLE_NAME_SIZE + 1 + NETWORK_VARIABLE_VALUE_SIZE;
    private static final int SPAWN_COMMAND_SIZE = 256;
    private static final int ACTIVE_ACTION_COMMAND_SIZE = 192;
    private static final int ACTIVE_ACTION_RECORD_SIZE = 12 + ACTIVE_ACTION_COMMAND_SIZE;
    private static final int SNAPSHOT_ENTITY_SIZE = 228 + SOURCE_OBJECT_NAME_SIZE + SPAWN_COMMAND_SIZE;
    private static final float CORRECTION_INTERVAL_SECONDS = 0.25f;
    private static final float CREATE_RETRY_INTERVAL_SECONDS = 0.35f;
    private static final float POSITION_EPSILON_SQUARED = 0.0004f;
    private static final float ROTATION_EPSILON = 0.002f;
    private static final Pattern TIMED_COMMAND_PATTERN = Pattern.compile(
            "(?i)^(.*)\\s+(in|for)\\s+([+-]?[0-9]+(?:\\.[0-9]+)?)\\s+seconds\\s*$");
    private static final Pattern AXIS_DELTA_ACTION_PATTERN = Pattern.compile(
            "(?i)(\\.(?:move|rotate)\\s*\\(\\s*[xyz]\\s*[+-]\\s*)([+-]?[0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern VERBAL_MOVE_ACTION_PATTERN = Pattern.compile(
            "(?i)(\\.move\\s+(?:left|right|up|down|forward|backward)\\s+)([+-]?[0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern TARGET_ACTION_PATTERN = Pattern.compile("(?i)\\.(?:move|rotate)\\s+to\\b");
    private static final Pattern LOCAL_ENTITY_PLACEHOLDER_PATTERN = Pattern.compile(
            "\\{(?:network_entity|networkEntity):([^}]+)}");
    private static final Pattern REMOTE_ENTITY_ID_PLACEHOLDER_PATTERN = Pattern.compile(
            "\\{(?:network_entity_id|networkEntityId):(\\d+)}");

    private final SceneMaxApp app;
    private final Logger logger = Logger.getLogger(MultiplayerNetworkComponent.class.getName());
    private final ByteBuffer receiveBuffer = ByteBuffer.allocateDirect(MAX_PACKET_SIZE).order(ByteOrder.LITTLE_ENDIAN);
    private final Map<String, RegisteredEntity> localEntities = new HashMap<>();
    private final Map<Integer, RemoteEntity> remoteEntities = new HashMap<>();
    private final Map<Integer, TransformState> pendingTransforms = new HashMap<>();
    private final Map<Integer, List<PendingRemoteCommand>> pendingRemoteCommands = new HashMap<>();
    private final Map<String, TransformState> lastSentCorrections = new HashMap<>();
    private final Map<Integer, String> pendingCreateAcks = new LinkedHashMap<>();
    private final Map<String, PendingNetworkVariable> pendingNetworkVariables = new LinkedHashMap<>();
    private final Set<String> appliedRemoteStructuralCommands = new HashSet<>();
    private DatagramChannel channel;
    private int clientId;
    private long sessionId;
    private String sessionName = "";
    private boolean networkReady;
    private boolean sessionSwitchPending;
    private boolean joinSessionRequested;
    private long pendingJoinSessionId;
    private String pendingJoinSessionName = "";
    private JSONObject networkState = new JSONObject();
    private final List<Object> stateSessions = new ArrayList<>();
    private final Map<Long, Map<String, Object>> stateSessionsById = new LinkedHashMap<>();
    private String activeSceneId = "main";
    private String playerName = "";
    private String projectGuid = "";
    private int nextCreateRequestId = 1;
    private float heartbeatTimer;
    private float correctionTimer;

    public MultiplayerNetworkComponent(SceneMaxApp app) {
        this.app = app;
    }

    public boolean isActive() {
        return channel != null && channel.isOpen();
    }

    public boolean isReady() {
        return isActive() && clientId != 0 && networkReady;
    }

    public JSONObject getState() {
        return networkState;
    }

    public List<Object> getStateSessions() {
        return new ArrayList<>(stateSessions);
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
            networkReady = false;
            logNet("connecting to " + server.trim() + ":" + port
                    + " sessionId=" + requestedSessionId
                    + " sessionName=\"" + (requestedSessionName == null ? "" : requestedSessionName) + "\""
                    + " projectGuidSet=" + (projectGuid != null && !projectGuid.isBlank()));
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
        entity.sourceObjectName = varDef.varName == null ? "" : varDef.varName;
        entity.spawnCommand = spawnCommand == null ? "" : spawnCommand;
        entity.createRequestId = nextCreateRequestId();
        localEntities.put(runtimeName, entity);
        logNet("registered local entity runtime=" + runtimeName
                + " type=" + varDef.varType
                + " archetype=" + entity.archetypeName
                + " spawn=\"" + entity.spawnCommand + "\"");
        if (clientId != 0 && !sessionSwitchPending) {
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
        retryPendingCreates(tpf);
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

    public void sendNetworkEvent(int targetClientId, String eventName) {
        if (!isActive() || clientId == 0 || targetClientId == 0 || eventName == null || eventName.trim().isEmpty()) {
            return;
        }
        byte[] eventBytes = eventName.trim().getBytes(StandardCharsets.UTF_8);
        ByteBuffer packet = packet(NETWORK_EVENT, Math.min(eventBytes.length + 2, MAX_PACKET_SIZE - 8));
        packet.putShort((short) targetClientId);
        packet.put(eventBytes, 0, Math.min(eventBytes.length, MAX_PACKET_SIZE - 10));
        logNet("send network event targetClient=" + targetClientId + " event=\"" + eventName.trim() + "\"");
        send(packet);
    }

    public void syncVariable(String varName, Object value, boolean declarationInit) {
        if (varName == null || varName.trim().isEmpty()) {
            return;
        }
        String encodedValue = encodeNetworkVariableValue(value);
        if (encodedValue == null) {
            return;
        }
        String normalizedName = varName.trim();
        if (!isReady()) {
            PendingNetworkVariable pending = new PendingNetworkVariable();
            pending.name = normalizedName;
            pending.encodedValue = encodedValue;
            pending.declarationInit = declarationInit;
            pendingNetworkVariables.put(normalizedName, pending);
            return;
        }
        sendNetworkVariableUpdate(normalizedName, encodedValue, declarationInit);
    }

    public String remoteSourceObjectName(String runtimeName) {
        RemoteEntity entity = remoteEntityForRuntime(runtimeName);
        return entity == null ? null : entity.sourceObjectName;
    }

    public int remoteOwnerClientId(String runtimeName) {
        RemoteEntity entity = remoteEntityForRuntime(runtimeName);
        return entity == null ? 0 : entity.ownerClientId;
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
        if (!sendCommandIfResolved(entity, commandText)) {
            logNet("queue command until referenced entities have ids runtime=" + runtimeName
                    + " command=\"" + commandText + "\"");
            entity.pendingCommands.add(commandText);
        }
    }

    public int startPersistentCommand(String runtimeName, int slot, String commandText) {
        if (runtimeName == null || slot <= 0 || commandText == null || commandText.trim().isEmpty()) {
            return 0;
        }
        RegisteredEntity entity = localEntities.get(runtimeName);
        if (entity == null) {
            return 0;
        }
        int sequence = entity.nextActionSequence++;
        if (entity.nextActionSequence == 0) {
            entity.nextActionSequence = 1;
        }
        ActiveAction action = new ActiveAction();
        action.slot = slot;
        action.sequence = sequence;
        action.durationMs = Integer.MAX_VALUE;
        action.command = commandText;
        entity.activeActions.put(slot, action);
        if (entity.networkEntityId != 0) {
            action.startSent = sendActiveActionStartIfResolved(entity, action);
        }
        return sequence;
    }

    public int startActiveAction(String runtimeName, int slot, float durationSeconds, String commandText) {
        if (runtimeName == null || slot <= 0 || commandText == null || commandText.trim().isEmpty()) {
            return 0;
        }
        RegisteredEntity entity = localEntities.get(runtimeName);
        if (entity == null) {
            return 0;
        }
        int sequence = entity.nextActionSequence++;
        if (entity.nextActionSequence == 0) {
            entity.nextActionSequence = 1;
        }
        ActiveAction action = new ActiveAction();
        action.slot = slot;
        action.sequence = sequence;
        action.durationMs = Math.max(0, Math.round(durationSeconds * 1000.0f));
        action.command = commandText;
        entity.activeActions.put(slot, action);
        if (entity.networkEntityId == 0) {
            logNet("queue active action runtime=" + runtimeName + " slot=" + slot + " seq=" + sequence);
            return sequence;
        }
        action.startSent = sendActiveActionStartIfResolved(entity, action);
        return sequence;
    }

    public void endActiveAction(String runtimeName, int slot, int sequence) {
        if (runtimeName == null || slot <= 0 || sequence <= 0) {
            return;
        }
        RegisteredEntity entity = localEntities.get(runtimeName);
        if (entity == null) {
            return;
        }
        ActiveAction current = entity.activeActions.get(slot);
        if (current == null || current.sequence != sequence) {
            return;
        }
        entity.activeActions.remove(slot);
        if (entity.networkEntityId != 0) {
            sendActiveActionEnd(entity.networkEntityId, slot, sequence);
        }
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
        networkReady = false;
        sessionSwitchPending = false;
        joinSessionRequested = false;
        pendingJoinSessionId = 0;
        pendingJoinSessionName = "";
        pendingCreateAcks.clear();
        removeRemoteEntities();
        pendingTransforms.clear();
        pendingRemoteCommands.clear();
        lastSentCorrections.clear();
        appliedRemoteStructuralCommands.clear();
        pendingNetworkVariables.clear();
    }

    public void joinSession(long requestedSessionId) {
        if (requestedSessionId <= 0) {
            return;
        }
        joinSessionInternal(requestedSessionId, "");
    }

    public void joinSession(String requestedSessionName) {
        if (requestedSessionName == null || requestedSessionName.trim().isEmpty()) {
            return;
        }
        joinSessionInternal(0, requestedSessionName.trim());
    }

    public void joinScene(String sceneId) {
        activeSceneId = normalizeSceneId(sceneId);
        removeRemoteEntities();
        pendingTransforms.clear();
        pendingRemoteCommands.clear();
        lastSentCorrections.clear();
        appliedRemoteStructuralCommands.clear();
        localEntities.clear();
        pendingCreateAcks.clear();
        pendingNetworkVariables.clear();
        if (isActive() && clientId != 0) {
            ByteBuffer packet = packet(JOIN_SCENE, 128);
            putFixedString(packet, activeSceneId, 128);
            send(packet);
        }
    }

    private void joinSessionInternal(long requestedSessionId, String requestedSessionName) {
        if (!isActive()) {
            return;
        }
        sessionSwitchPending = true;
        networkReady = false;
        rebuildNetworkState();
        removeRemoteEntities();
        pendingTransforms.clear();
        pendingRemoteCommands.clear();
        lastSentCorrections.clear();
        appliedRemoteStructuralCommands.clear();
        pendingCreateAcks.clear();
        pendingNetworkVariables.clear();
        for (RegisteredEntity entity : localEntities.values()) {
            entity.networkEntityId = 0;
            entity.createRequestId = nextCreateRequestId();
            entity.createRetryTimer = 0;
            entity.createSendCount = 0;
            entity.destroyAfterCreateAck = false;
        }
        joinSessionRequested = true;
        pendingJoinSessionId = requestedSessionId;
        pendingJoinSessionName = requestedSessionName == null ? "" : requestedSessionName;
        if (clientId != 0) {
            sendJoinSessionRequest();
        }
    }

    private void sendJoinSessionRequest() {
        ByteBuffer packet = packet(JOIN_SESSION, 68);
        packet.putInt((int) pendingJoinSessionId);
        putFixedString(packet, pendingJoinSessionName, 64);
        logNet("send join session id=" + pendingJoinSessionId + " name=\"" + pendingJoinSessionName + "\"");
        send(packet);
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
        if (entity.createRequestId == 0) {
            entity.createRequestId = nextCreateRequestId();
        }
        ByteBuffer packet = packet(CREATE_ENTITY_REQUEST, 4 + 128 + SOURCE_OBJECT_NAME_SIZE + SPAWN_COMMAND_SIZE);
        packet.putInt(entity.createRequestId);
        putFixedString(packet, entity.archetypeName, 64);
        putFixedString(packet, playerName, 64);
        putFixedString(packet, entity.sourceObjectName, SOURCE_OBJECT_NAME_SIZE);
        putFixedString(packet, entity.spawnCommand, SPAWN_COMMAND_SIZE);
        pendingCreateAcks.put(entity.createRequestId, entity.runtimeName);
        entity.createRetryTimer = 0;
        entity.createSendCount++;
        logNet("send create runtime=" + entity.runtimeName
                + " createRequestId=" + entity.createRequestId
                + " archetype=" + entity.archetypeName
                + " spawn=\"" + entity.spawnCommand + "\"");
        send(packet);
    }

    private void retryPendingCreates(float tpf) {
        for (RegisteredEntity entity : new ArrayList<>(localEntities.values())) {
            if (entity.networkEntityId != 0 || entity.createRequestId == 0) {
                continue;
            }
            entity.createRetryTimer += tpf;
            if (entity.createRetryTimer >= CREATE_RETRY_INTERVAL_SECONDS) {
                logNet("retry create runtime=" + entity.runtimeName
                        + " createRequestId=" + entity.createRequestId
                        + " attempt=" + (entity.createSendCount + 1));
                sendCreateEntity(entity);
            }
        }
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
        DatagramChannel activeChannel = channel;
        if (activeChannel == null || !activeChannel.isOpen()) {
            return;
        }
        try {
            while (true) {
                receiveBuffer.clear();
                if (activeChannel.receive(receiveBuffer) == null) {
                    return;
                }
                receiveBuffer.flip();
                if (receiveBuffer.remaining() < 8 || receiveBuffer.getInt() != MAGIC || receiveBuffer.get() != VERSION) {
                    continue;
                }
                byte type = receiveBuffer.get();
                int senderClientId = Short.toUnsignedInt(receiveBuffer.getShort());
                handlePacket(type, senderClientId, receiveBuffer);
                if (channel != activeChannel || !activeChannel.isOpen()) {
                    return;
                }
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
            sessionSwitchPending = false;
            if (joinSessionRequested && !matchesPendingJoinSession()) {
                sessionSwitchPending = true;
                sendJoinSessionRequest();
                return;
            }
            if (joinSessionRequested) {
                joinSessionRequested = false;
                pendingJoinSessionId = 0;
                pendingJoinSessionName = "";
            }
            upsertCurrentSessionState();
            networkReady = true;
            rebuildNetworkState();
            logNet("joined session " + sessionId + " (\"" + sessionName + "\"), scene " + activeSceneId
                    + ", clientId=" + clientId);
            for (RegisteredEntity entity : localEntities.values()) {
                sendCreateEntity(entity);
            }
            flushPendingNetworkVariables();
        } else if (type == LOGIN_REJECTED) {
            logNet("login rejected by server");
            close();
        } else if (type == CREATE_ENTITY_ACCEPTED && payload.remaining() >= 4) {
            int networkId = payload.getInt();
            if (senderClientId == clientId) {
                int createRequestId = payload.remaining() >= 4 ? payload.getInt() : 0;
                String runtimeName = createRequestId == 0 ? pollFirstPendingCreateAck() : pendingCreateAcks.remove(createRequestId);
                RegisteredEntity entity = runtimeName == null ? null : localEntities.get(runtimeName);
                if (entity != null) {
                    entity.networkEntityId = networkId;
                    entity.createRetryTimer = 0;
                    logNet("create accepted runtime=" + runtimeName
                            + " createRequestId=" + createRequestId
                            + " networkId=" + networkId);
                    if (entity.destroyAfterCreateAck) {
                        sendDestroyEntity(networkId, runtimeName);
                        localEntities.remove(runtimeName);
                        return;
                    }
                    flushPendingCommands(entity);
                    flushPendingActiveActions(entity);
                    flushAllPendingLocalCommands();
                    flushAllPendingLocalActiveActions();
                }
            } else if (payload.remaining() >= 128) {
                String archetype = readFixedString(payload, 64);
                String remotePlayerName = readFixedString(payload, 64);
                String sourceObjectName = "";
                String spawnCommand = "";
                if (payload.remaining() >= SOURCE_OBJECT_NAME_SIZE + SPAWN_COMMAND_SIZE) {
                    sourceObjectName = readFixedString(payload, SOURCE_OBJECT_NAME_SIZE);
                    spawnCommand = readFixedString(payload, SPAWN_COMMAND_SIZE);
                } else if (payload.remaining() >= SPAWN_COMMAND_SIZE) {
                    spawnCommand = readFixedString(payload, SPAWN_COMMAND_SIZE);
                }
                logNet("receive remote create networkId=" + networkId
                        + " sender=" + senderClientId
                        + " archetype=" + archetype
                        + " source=" + sourceObjectName
                        + " spawn=\"" + spawnCommand + "\"");
                createOrUpdateRemoteEntity(networkId, senderClientId, archetype, remotePlayerName,
                        sourceObjectName, transformOrNull(networkId), 0, "", spawnCommand);
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
        } else if (type == ACTIVE_ACTION_START && payload.remaining() >= ACTIVE_ACTION_RECORD_SIZE) {
            handleActiveActionStart(payload);
        } else if (type == TRANSFORM_CORRECTION && payload.remaining() >= 32) {
            handleTransformCorrection(payload);
        } else if (type == NETWORK_EVENT && payload.remaining() > 0) {
            byte[] bytes = new byte[payload.remaining()];
            payload.get(bytes);
            String eventName = new String(bytes, StandardCharsets.UTF_8).trim();
            if (!eventName.isEmpty() && app != null) {
                logNet("receive network event sender=" + senderClientId + " event=\"" + eventName + "\"");
                app.receiveNetworkEvent(eventName);
            }
        } else if (type == NETWORK_VARIABLE_UPDATE && payload.remaining() >= NETWORK_VARIABLE_UPDATE_SIZE) {
            handleNetworkVariableUpdate(payload);
        } else if (type == SNAPSHOT) {
            handleSnapshot(payload);
        } else if (type == SERVER_STATE) {
            handleServerState(payload);
        }
    }

    private void handleServerState(ByteBuffer payload) {
        if (payload.remaining() < 6) {
            return;
        }
        int total = Short.toUnsignedInt(payload.getShort());
        int offset = Short.toUnsignedInt(payload.getShort());
        int count = Short.toUnsignedInt(payload.getShort());
        if (offset == 0) {
            stateSessionsById.clear();
        }
        for (int i = 0; i < count && payload.remaining() >= 70; i++) {
            long id = Integer.toUnsignedLong(payload.getInt());
            int clientCount = Short.toUnsignedInt(payload.getShort());
            String name = readFixedString(payload, 64);
            Map<String, Object> session = new LinkedHashMap<>();
            session.put("id", id);
            session.put("name", name);
            session.put("clients", clientCount);
            session.put("connectedClients", clientCount);
            stateSessionsById.put(id, session);
        }
        if (!sessionSwitchPending && stateSessionsById.size() >= total) {
            networkReady = true;
        }
        rebuildNetworkState();
    }

    private void rebuildNetworkState() {
        stateSessions.clear();
        JSONArray sessionsJson = new JSONArray();
        for (Map<String, Object> session : stateSessionsById.values()) {
            Map<String, Object> copy = new LinkedHashMap<>(session);
            stateSessions.add(copy);
            sessionsJson.put(new JSONObject(copy));
        }
        JSONObject state = new JSONObject();
        state.put("ready", isReady());
        state.put("sessionId", sessionId);
        state.put("sessionName", sessionName);
        state.put("sessions", sessionsJson);
        networkState = state;
    }

    private void upsertCurrentSessionState() {
        if (sessionId == 0) {
            return;
        }
        Map<String, Object> session = stateSessionsById.get(sessionId);
        if (session == null) {
            session = new LinkedHashMap<>();
            stateSessionsById.put(sessionId, session);
        }
        session.put("id", sessionId);
        session.put("name", sessionName == null ? "" : sessionName);
        if (!session.containsKey("clients")) {
            session.put("clients", 1);
        }
        if (!session.containsKey("connectedClients")) {
            session.put("connectedClients", 1);
        }
    }

    private boolean matchesPendingJoinSession() {
        if (pendingJoinSessionId > 0) {
            return sessionId == pendingJoinSessionId;
        }
        return pendingJoinSessionName == null
                || pendingJoinSessionName.isEmpty()
                || pendingJoinSessionName.equals(sessionName);
    }

    private String pollFirstPendingCreateAck() {
        if (pendingCreateAcks.isEmpty()) {
            return null;
        }
        Integer firstKey = pendingCreateAcks.keySet().iterator().next();
        return pendingCreateAcks.remove(firstKey);
    }

    private void flushPendingCommands(RegisteredEntity entity) {
        if (entity == null || entity.networkEntityId == 0 || entity.pendingCommands.isEmpty()) {
            return;
        }
        Iterator<String> iterator = entity.pendingCommands.iterator();
        while (iterator.hasNext()) {
            String command = iterator.next();
            if (sendCommandIfResolved(entity, command)) {
                iterator.remove();
            }
        }
    }

    private void flushAllPendingLocalCommands() {
        for (RegisteredEntity entity : localEntities.values()) {
            flushPendingCommands(entity);
        }
    }

    private void flushPendingActiveActions(RegisteredEntity entity) {
        if (entity == null || entity.networkEntityId == 0 || entity.activeActions.isEmpty()) {
            return;
        }
        for (ActiveAction action : new ArrayList<>(entity.activeActions.values())) {
            if (!action.startSent && sendActiveActionStartIfResolved(entity, action)) {
                action.startSent = true;
            }
        }
    }

    private void flushAllPendingLocalActiveActions() {
        for (RegisteredEntity entity : localEntities.values()) {
            flushPendingActiveActions(entity);
        }
    }

    private void flushPendingNetworkVariables() {
        if (pendingNetworkVariables.isEmpty()) {
            return;
        }
        for (PendingNetworkVariable pending : new ArrayList<>(pendingNetworkVariables.values())) {
            sendNetworkVariableUpdate(pending.name, pending.encodedValue, pending.declarationInit);
            pendingNetworkVariables.remove(pending.name);
        }
    }

    private void sendNetworkVariableUpdate(String varName, String encodedValue, boolean declarationInit) {
        if (!isActive() || clientId == 0 || varName == null || varName.trim().isEmpty() || encodedValue == null) {
            return;
        }
        ByteBuffer packet = packet(NETWORK_VARIABLE_UPDATE, NETWORK_VARIABLE_UPDATE_SIZE);
        putFixedString(packet, varName.trim(), NETWORK_VARIABLE_NAME_SIZE);
        packet.put((byte) (declarationInit ? 1 : 0));
        putFixedString(packet, encodedValue, NETWORK_VARIABLE_VALUE_SIZE);
        logNet("send network variable name=" + varName.trim() + " init=" + declarationInit);
        send(packet);
    }

    private void handleNetworkVariableUpdate(ByteBuffer payload) {
        String varName = readFixedString(payload, NETWORK_VARIABLE_NAME_SIZE);
        payload.get();
        String encodedValue = readFixedString(payload, NETWORK_VARIABLE_VALUE_SIZE);
        Object value = decodeNetworkVariableValue(encodedValue);
        if (varName == null || varName.trim().isEmpty() || app == null) {
            return;
        }
        logNet("receive network variable name=" + varName.trim());
        app.receiveNetworkVariableUpdate(varName.trim(), value);
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

    private void sendActiveActionStart(int networkEntityId, ActiveAction action) {
        if (networkEntityId == 0 || action == null || !isActive() || clientId == 0) {
            return;
        }
        ByteBuffer packet = packet(ACTIVE_ACTION_START, ACTIVE_ACTION_RECORD_SIZE);
        packet.putInt(networkEntityId);
        packet.put((byte) action.slot);
        packet.put((byte) 0);
        packet.putShort((short) action.sequence);
        packet.putInt(action.durationMs);
        putFixedString(packet, action.command, ACTIVE_ACTION_COMMAND_SIZE);
        logNet("send active action start entity=" + networkEntityId
                + " slot=" + action.slot
                + " seq=" + action.sequence
                + " durationMs=" + action.durationMs
                + " command=\"" + action.command + "\"");
        send(packet);
    }

    private boolean sendCommandIfResolved(RegisteredEntity entity, String commandText) {
        if (entity == null || entity.networkEntityId == 0) {
            return false;
        }
        String resolved = resolveOutgoingCommand(commandText);
        if (resolved == null) {
            return false;
        }
        sendCommand(entity.networkEntityId, resolved);
        return true;
    }

    private boolean sendActiveActionStartIfResolved(RegisteredEntity entity, ActiveAction action) {
        if (entity == null || entity.networkEntityId == 0 || action == null) {
            return false;
        }
        String resolved = resolveOutgoingCommand(action.command);
        if (resolved == null) {
            return false;
        }
        ActiveAction resolvedAction = action;
        if (!resolved.equals(action.command)) {
            resolvedAction = new ActiveAction();
            resolvedAction.slot = action.slot;
            resolvedAction.sequence = action.sequence;
            resolvedAction.durationMs = action.durationMs;
            resolvedAction.command = resolved;
            resolvedAction.startSent = action.startSent;
        }
        sendActiveActionStart(entity.networkEntityId, resolvedAction);
        return true;
    }

    private void sendActiveActionEnd(int networkEntityId, int slot, int sequence) {
        if (networkEntityId == 0 || !isActive() || clientId == 0) {
            return;
        }
        ByteBuffer packet = packet(ACTIVE_ACTION_END, 8);
        packet.putInt(networkEntityId);
        packet.put((byte) slot);
        packet.put((byte) 0);
        packet.putShort((short) sequence);
        logNet("send active action end entity=" + networkEntityId + " slot=" + slot + " seq=" + sequence);
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
            String sourceObjectName = readFixedString(payload, SOURCE_OBJECT_NAME_SIZE);
            TransformState transform = readTransformState(payload);
            int animationIndex = Short.toUnsignedInt(payload.getShort());
            String animation = readFixedString(payload, 64);
            String spawnCommand = payload.remaining() >= SPAWN_COMMAND_SIZE ? readFixedString(payload, SPAWN_COMMAND_SIZE) : "";
            List<SnapshotAction> actions = readSnapshotActions(payload);
            if (ownerClientId == clientId || isLocalNetworkEntity(networkId)) {
                continue;
            }
            createOrUpdateRemoteEntity(networkId, ownerClientId, archetype, remotePlayerName,
                    sourceObjectName, transform, animationIndex, animation, spawnCommand);
            applySnapshotActions(networkId, actions);
        }
    }

    private List<SnapshotAction> readSnapshotActions(ByteBuffer payload) {
        List<SnapshotAction> actions = new ArrayList<>();
        if (payload.remaining() < 1) {
            return actions;
        }
        int count = Byte.toUnsignedInt(payload.get());
        for (int i = 0; i < count && payload.remaining() >= ACTIVE_ACTION_RECORD_SIZE; i++) {
            SnapshotAction action = new SnapshotAction();
            action.slot = Byte.toUnsignedInt(payload.get());
            payload.get();
            action.sequence = Short.toUnsignedInt(payload.getShort());
            action.remainingMs = payload.getInt();
            action.durationMs = payload.getInt();
            action.command = readFixedString(payload, ACTIVE_ACTION_COMMAND_SIZE);
            actions.add(action);
        }
        return actions;
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
        handleRemoteCommand(networkId, command, null);
    }

    private void handleRemoteCommand(int networkId, String command, MultiplayerControllerResumeState resumeState) {
        if (networkId == 0 || command == null || command.trim().isEmpty()) {
            return;
        }
        RemoteEntity entity = remoteEntities.get(networkId);
        if (entity == null || !isRemoteEntityReady(entity) || !canResolveRemoteCommand(command)) {
            pendingRemoteCommands.computeIfAbsent(networkId, id -> new ArrayList<>())
                    .add(new PendingRemoteCommand(command, resumeState));
            logNet("queue remote command networkId=" + networkId + " command=\"" + command + "\"");
            return;
        }
        runRemoteCommand(networkId, command, resumeState);
    }

    private void handleActiveActionStart(ByteBuffer payload) {
        int networkId = payload.getInt();
        int slot = Byte.toUnsignedInt(payload.get());
        payload.get();
        payload.getShort();
        payload.getInt();
        String command = readFixedString(payload, ACTIVE_ACTION_COMMAND_SIZE);
        if (slot < SceneMaxBaseController.MULTIPLAYER_ACTION_SLOT_STRUCTURAL_BASE
                || command == null || command.trim().isEmpty()) {
            return;
        }
        handleRemoteCommand(networkId, command.trim());
    }

    private void runRemoteCommand(int networkId, String command) {
        runRemoteCommand(networkId, command, null);
    }

    private void runRemoteCommand(int networkId, String command, MultiplayerControllerResumeState resumeState) {
        if (isStructuralCommand(command) && !markRemoteStructuralCommandApplied(networkId, command)) {
            logNet("skip duplicate structural command networkId=" + networkId + " command=\"" + command + "\"");
            return;
        }
        String resolved = resolveRemoteCommandTarget(networkId, command);
        logNet("receive command networkId=" + networkId + " command=\"" + resolved + "\"");
        RemoteEntity entity = remoteEntities.get(networkId);
        if (resumeState != null && entity != null) {
            app.runNetworkMultiplayerCommand(resolved, entity.sourceName, resumeState);
        } else {
            app.runNetworkMultiplayerCommand(resolved);
        }
    }

    private boolean isStructuralCommand(String command) {
        if (command == null) {
            return false;
        }
        String normalized = command.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains(".attach to ")
                || normalized.contains(".ik")
                || normalized.contains(".switch to character mode")
                || normalized.contains(".clear character mode");
    }

    private boolean markRemoteStructuralCommandApplied(int networkId, String command) {
        return appliedRemoteStructuralCommands.add(networkId + "\n" + command);
    }

    private TransformState readTransformState(ByteBuffer payload) {
        TransformState transform = new TransformState();
        transform.position = new Vector3f(payload.getFloat(), payload.getFloat(), payload.getFloat());
        transform.rotation = new Quaternion(payload.getFloat(), payload.getFloat(), payload.getFloat(), payload.getFloat());
        return transform;
    }

    private void createOrUpdateRemoteEntity(int networkId, String archetype, String remotePlayerName,
                                            TransformState transform, int animationIndex, String animation) {
        createOrUpdateRemoteEntity(networkId, 0, archetype, remotePlayerName, "", transform, animationIndex, animation, "");
    }

    private void createOrUpdateRemoteEntity(int networkId, int ownerClientId, String archetype, String remotePlayerName,
                                            String sourceObjectName,
                                            TransformState transform, int animationIndex, String animation,
                                            String spawnCommand) {
        if (networkId == 0 || archetype == null || archetype.trim().isEmpty()) {
            return;
        }
        RemoteEntity entity = remoteEntities.get(networkId);
        if (entity == null) {
            entity = new RemoteEntity();
            entity.networkEntityId = networkId;
            entity.ownerClientId = ownerClientId;
            entity.sourceName = "mp_remote_" + networkId;
            entity.sourceObjectName = sourceObjectName == null || sourceObjectName.trim().isEmpty()
                    ? entity.sourceName
                    : sourceObjectName.trim();
            entity.archetypeName = sanitizeIdentifier(archetype);
            entity.varType = archetypeVarType(entity.archetypeName);
            entity.playerName = remotePlayerName == null ? "" : remotePlayerName;
            entity.spawnCommand = spawnCommand == null ? "" : spawnCommand.trim();
            remoteEntities.put(networkId, entity);
            createRemoteModel(entity, transform);
        } else {
            if (ownerClientId != 0) {
                entity.ownerClientId = ownerClientId;
            }
            if (sourceObjectName != null && !sourceObjectName.trim().isEmpty()) {
                entity.sourceObjectName = sourceObjectName.trim();
            }
        }
        entity.animationIndex = animationIndex;
        entity.animation = animation == null ? "" : animation;
        if (transform != null && !applyRemoteTransform(networkId, transform)) {
            pendingTransforms.put(networkId, transform);
        }
    }

    private void applySnapshotActions(int networkId, List<SnapshotAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return;
        }
        for (SnapshotAction action : actions) {
            if (action == null || action.remainingMs <= 0 || action.command == null || action.command.trim().isEmpty()) {
                continue;
            }
            String resumed = commandForSnapshotAction(action.command, action.remainingMs);
            MultiplayerControllerResumeState resumeState = resumeStateForSnapshotAction(action);
            handleRemoteCommand(networkId, resumed, resumeState);
        }
    }

    private String commandForSnapshotAction(String command, int remainingMs) {
        float remainingSeconds = Math.max(0.001f, remainingMs / 1000.0f);
        String trimmed = command == null ? "" : command.trim();
        Matcher matcher = TIMED_COMMAND_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            return trimmed;
        }
        String action = matcher.group(1);
        if (!TARGET_ACTION_PATTERN.matcher(action).find()) {
            return trimmed;
        }
        return action + " " + matcher.group(2).toLowerCase(java.util.Locale.ROOT)
                + " " + networkNumber(remainingSeconds) + " seconds";
    }

    private MultiplayerControllerResumeState resumeStateForSnapshotAction(SnapshotAction action) {
        if (action.slot == SceneMaxBaseController.MULTIPLAYER_ACTION_SLOT_ANIMATE) {
            if (action.durationMs <= 0 || action.remainingMs <= 0) {
                return null;
            }
            float remainingSeconds = Math.max(0.001f, action.remainingMs / 1000.0f);
            float originalSeconds = Math.max(remainingSeconds, action.durationMs / 1000.0f);
            float elapsedSeconds = Math.max(0f, originalSeconds - remainingSeconds);
            return new MultiplayerControllerResumeState(action.slot, elapsedSeconds, remainingSeconds, originalSeconds);
        }

        Matcher matcher = TIMED_COMMAND_PATTERN.matcher(action.command == null ? "" : action.command.trim());
        if (!matcher.matches()) {
            return null;
        }
        String commandAction = matcher.group(1);
        if (TARGET_ACTION_PATTERN.matcher(commandAction).find()) {
            return null;
        }
        if (!AXIS_DELTA_ACTION_PATTERN.matcher(commandAction).find()
                && !VERBAL_MOVE_ACTION_PATTERN.matcher(commandAction).find()) {
            return null;
        }
        float remainingSeconds = Math.max(0.001f, action.remainingMs / 1000.0f);
        float originalSeconds = action.durationMs > 0
                ? action.durationMs / 1000.0f
                : parsePositiveFloat(matcher.group(3), remainingSeconds);
        float elapsedSeconds = Math.max(0f, originalSeconds - remainingSeconds);
        return new MultiplayerControllerResumeState(action.slot, elapsedSeconds, remainingSeconds, originalSeconds);
    }

    private float parsePositiveFloat(String raw, float fallback) {
        try {
            float value = Float.parseFloat(raw);
            return value < 0f ? fallback : value;
        } catch (NumberFormatException ex) {
            return fallback;
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
        for (Map.Entry<Integer, List<PendingRemoteCommand>> entry : new HashMap<>(pendingRemoteCommands).entrySet()) {
            RemoteEntity entity = remoteEntities.get(entry.getKey());
            if (entity == null) {
                continue;
            }
            if (entity.destroyPending || !isRemoteEntityReady(entity)) {
                continue;
            }
            Iterator<PendingRemoteCommand> iterator = entry.getValue().iterator();
            while (iterator.hasNext()) {
                PendingRemoteCommand command = iterator.next();
                if (!canResolveRemoteCommand(command.command)) {
                    continue;
                }
                runRemoteCommand(entry.getKey(), command.command, command.resumeState);
                iterator.remove();
            }
            if (entry.getValue().isEmpty()) {
                pendingRemoteCommands.remove(entry.getKey());
            }
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
        removeAppliedRemoteStructuralCommands(networkId);
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
        } else if (entity.varType == VariableDef.VAR_TYPE_CYLINDER) {
            app.killCylinder(entity.runtimeName);
        } else if (entity.varType == VariableDef.VAR_TYPE_HOLLOW_CYLINDER) {
            app.killHollowCylinder(entity.runtimeName);
        } else if (entity.varType == VariableDef.VAR_TYPE_QUAD) {
            app.killQuad(entity.runtimeName);
        } else if (entity.varType == VariableDef.VAR_TYPE_WEDGE) {
            app.killWedge(entity.runtimeName);
        } else if (entity.varType == VariableDef.VAR_TYPE_CONE) {
            app.killCone(entity.runtimeName);
        } else if (entity.varType == VariableDef.VAR_TYPE_STAIRS) {
            app.killStairs(entity.runtimeName);
        } else if (entity.varType == VariableDef.VAR_TYPE_ARCH) {
            app.killArch(entity.runtimeName);
        } else if (entity.varType == VariableDef.VAR_TYPE_LABEL) {
            app.killLabel(entity.runtimeName);
        } else {
            app.killModel(entity.runtimeName);
        }
    }

    private void removeAppliedRemoteStructuralCommands(int networkId) {
        String prefix = networkId + "\n";
        appliedRemoteStructuralCommands.removeIf(key -> key.startsWith(prefix));
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

    private RemoteEntity remoteEntityForRuntime(String runtimeName) {
        if (runtimeName == null || runtimeName.trim().isEmpty()) {
            return null;
        }
        String normalized = runtimeName.trim();
        for (RemoteEntity entity : remoteEntities.values()) {
            if (entity == null) {
                continue;
            }
            if (normalized.equals(entity.runtimeName) || normalized.equals(entity.sourceName)) {
                return entity;
            }
        }
        return null;
    }

    private String resolveRemoteCommandTarget(int networkId, String command) {
        RemoteEntity entity = remoteEntities.get(networkId);
        if (entity == null || entity.sourceName == null) {
            return command;
        }
        String resolved = command
                .replace("{network_entity}", entity.sourceName)
                .replace("{networkEntity}", entity.sourceName)
                .replace("$network_entity", entity.sourceName)
                .replace("$networkEntity", entity.sourceName);
        Matcher matcher = REMOTE_ENTITY_ID_PLACEHOLDER_PATTERN.matcher(resolved);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            int referencedNetworkId = parseInt(matcher.group(1), 0);
            RemoteEntity referenced = remoteEntities.get(referencedNetworkId);
            String replacement = referenced == null || referenced.sourceName == null
                    ? matcher.group(0)
                    : referenced.sourceName;
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private boolean canResolveRemoteCommand(String command) {
        if (command == null) {
            return false;
        }
        Matcher matcher = REMOTE_ENTITY_ID_PLACEHOLDER_PATTERN.matcher(command);
        while (matcher.find()) {
            int referencedNetworkId = parseInt(matcher.group(1), 0);
            RemoteEntity referenced = remoteEntities.get(referencedNetworkId);
            if (!isRemoteEntityReady(referenced)) {
                return false;
            }
        }
        return true;
    }

    private String resolveOutgoingCommand(String command) {
        if (command == null) {
            return null;
        }
        Matcher matcher = LOCAL_ENTITY_PLACEHOLDER_PATTERN.matcher(command);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            RegisteredEntity referenced = localEntities.get(matcher.group(1));
            if (referenced == null || referenced.networkEntityId == 0) {
                return null;
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    "{network_entity_id:" + referenced.networkEntityId + "}"));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
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
        DatagramChannel activeChannel = channel;
        if (activeChannel == null || !activeChannel.isOpen()) {
            return;
        }
        try {
            packet.flip();
            activeChannel.write(packet);
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
        String normalized = archetype.trim().toLowerCase().replace(" ", "").replace("_", "");
        switch (normalized) {
            case "sphere":
                return VariableDef.VAR_TYPE_SPHERE;
            case "box":
                return VariableDef.VAR_TYPE_BOX;
            case "cylinder":
                return VariableDef.VAR_TYPE_CYLINDER;
            case "hollowcylinder":
                return VariableDef.VAR_TYPE_HOLLOW_CYLINDER;
            case "quad":
                return VariableDef.VAR_TYPE_QUAD;
            case "wedge":
                return VariableDef.VAR_TYPE_WEDGE;
            case "cone":
                return VariableDef.VAR_TYPE_CONE;
            case "stairs":
                return VariableDef.VAR_TYPE_STAIRS;
            case "arch":
                return VariableDef.VAR_TYPE_ARCH;
            case "label":
                return VariableDef.VAR_TYPE_LABEL;
            default:
                return VariableDef.VAR_TYPE_3D;
        }
    }

    private int nextCreateRequestId() {
        int id = nextCreateRequestId++;
        if (nextCreateRequestId == 0) {
            nextCreateRequestId = 1;
        }
        return id;
    }

    private String networkNumber(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "0";
        }
        if (Math.abs(value - Math.rint(value)) < 0.000001d) {
            return Long.toString(Math.round(value));
        }
        return String.format(java.util.Locale.ROOT, "%.6f", value)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    private String encodeNetworkVariableValue(Object value) {
        JSONObject json = new JSONObject();
        if (value == null) {
            json.put("type", "null");
            json.put("value", JSONObject.NULL);
        } else if (value instanceof Number) {
            json.put("type", "number");
            json.put("value", ((Number) value).doubleValue());
        } else if (value instanceof Boolean) {
            json.put("type", "boolean");
            json.put("value", value);
        } else if (value instanceof String) {
            json.put("type", "string");
            json.put("value", value);
        } else {
            return null;
        }
        String encoded = json.toString();
        return encoded.getBytes(StandardCharsets.UTF_8).length < NETWORK_VARIABLE_VALUE_SIZE ? encoded : null;
    }

    private Object decodeNetworkVariableValue(String encodedValue) {
        if (encodedValue == null || encodedValue.trim().isEmpty()) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(encodedValue);
            String type = json.optString("type", "");
            if ("number".equals(type)) {
                return json.optDouble("value", 0d);
            }
            if ("boolean".equals(type)) {
                return json.optBoolean("value", false);
            }
            if ("string".equals(type)) {
                return json.optString("value", "");
            }
            return null;
        } catch (Exception ignored) {
            return encodedValue;
        }
    }

    private static class RegisteredEntity {
        String runtimeName;
        VariableDef varDef;
        String archetypeName;
        String sourceObjectName;
        String spawnCommand;
        int networkEntityId;
        int createRequestId;
        int createSendCount;
        float createRetryTimer;
        boolean destroyAfterCreateAck;
        int nextActionSequence = 1;
        List<String> pendingCommands = new ArrayList<>();
        Map<Integer, ActiveAction> activeActions = new LinkedHashMap<>();
    }

    private static class PendingNetworkVariable {
        String name;
        String encodedValue;
        boolean declarationInit;
    }

    private static class RemoteEntity {
        int networkEntityId;
        int ownerClientId;
        String sourceName;
        String runtimeName;
        String sourceObjectName;
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

    private static class ActiveAction {
        int slot;
        int sequence;
        int durationMs;
        String command;
        boolean startSent;
    }

    private static class SnapshotAction {
        int slot;
        int sequence;
        int remainingMs;
        int durationMs;
        String command;
    }

    private static class PendingRemoteCommand {
        final String command;
        final MultiplayerControllerResumeState resumeState;

        PendingRemoteCommand(String command, MultiplayerControllerResumeState resumeState) {
            this.command = command;
            this.resumeState = resumeState;
        }
    }
}
