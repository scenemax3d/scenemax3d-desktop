const std = @import("std");
const net = std.Io.net;

const config_begin = "SCENEMAX_MP_CONFIG_BEGIN";
const config_end = "SCENEMAX_MP_CONFIG_END";
const config_payload_size = 4096;
const protocol_magic = 0x504d5853;
const protocol_version = 1;
const max_packet_size = 1200;
const max_clients = 256;
const max_entities = 2048;
const max_network_variables = 2048;
const max_server_events = 256;
const max_sessions = 256;
const source_object_name_size = 64;
const server_event_name_size = 64;
const server_event_registration_size = server_event_name_size + 4;
const minimum_server_event_interval_ms = 100;
const network_variable_name_size = 64;
const network_variable_value_size = 256;
const network_variable_update_size = network_variable_name_size + 1 + network_variable_value_size;
const spawn_command_size = 256;
const active_action_command_size = 192;
const snapshot_action_record_size = 12 + active_action_command_size;
const max_active_actions = 16;
const snapshot_entity_size = 228 + source_object_name_size + spawn_command_size;
const snapshot_entity_base_size = snapshot_entity_size + 1;
const active_action_grace_ms = 1000;
const verbose_packet_logs = false;

const PacketType = enum(u8) {
    login_request = 1,
    login_accepted = 2,
    login_rejected = 3,
    heartbeat = 4,
    join_scene = 5,
    join_session = 6,
    create_entity_request = 10,
    create_entity_accepted = 11,
    destroy_entity = 12,
    command_dispatch = 20,
    transform_correction = 21,
    active_action_start = 22,
    active_action_end = 23,
    network_event = 24,
    network_variable_update = 25,
    server_event_register = 26,
    snapshot = 30,
    server_state = 31,
    initial_sync_complete = 32,
    disconnect = 40,
};

const ServerConfig = struct {
    port: u16 = 9001,
    game_name: [128]u8 = [_]u8{0} ** 128,
    project_path: [256]u8 = [_]u8{0} ** 256,
    password_hash: [32]u8 = [_]u8{0} ** 32,
    project_guid: [64]u8 = [_]u8{0} ** 64,
};

const Session = struct {
    active: bool = false,
    id: u32 = 0,
    name: [64]u8 = [_]u8{0} ** 64,
};

const Client = struct {
    active: bool = false,
    id: u16 = 0,
    session_id: u32 = 0,
    scene_id: [128]u8 = [_]u8{0} ** 128,
    player_name: [64]u8 = [_]u8{0} ** 64,
    address: net.IpAddress = undefined,
    last_seen_ms: i64 = 0,
};

const Entity = struct {
    active: bool = false,
    network_id: u32 = 0,
    client_create_id: u32 = 0,
    owner_client: u16 = 0,
    session_id: u32 = 0,
    scene_id: [128]u8 = [_]u8{0} ** 128,
    archetype: [64]u8 = [_]u8{0} ** 64,
    player_name: [64]u8 = [_]u8{0} ** 64,
    source_object_name: [source_object_name_size]u8 = [_]u8{0} ** source_object_name_size,
    position: [3]f32 = .{ 0, 0, 0 },
    rotation: [4]f32 = .{ 0, 0, 0, 0 },
    animation_index: u16 = 0,
    animation: [64]u8 = [_]u8{0} ** 64,
    spawn_command: [spawn_command_size]u8 = [_]u8{0} ** spawn_command_size,
    active_actions: [max_active_actions]ActiveAction = [_]ActiveAction{.{}} ** max_active_actions,
};

const ActiveAction = struct {
    active: bool = false,
    slot: u8 = 0,
    sequence: u16 = 0,
    started_at_ms: i64 = 0,
    duration_ms: u32 = 0,
    command: [active_action_command_size]u8 = [_]u8{0} ** active_action_command_size,
};

const NetworkVariable = struct {
    active: bool = false,
    session_id: u32 = 0,
    scene_id: [128]u8 = [_]u8{0} ** 128,
    name: [network_variable_name_size]u8 = [_]u8{0} ** network_variable_name_size,
    value: [network_variable_value_size]u8 = [_]u8{0} ** network_variable_value_size,
};

const ServerEvent = struct {
    active: bool = false,
    session_id: u32 = 0,
    scene_id: [128]u8 = [_]u8{0} ** 128,
    name: [server_event_name_size]u8 = [_]u8{0} ** server_event_name_size,
    interval_ms: u32 = 0,
    next_fire_ms: i64 = 0,
};

export var scenemax_mp_config_block: [config_begin.len + config_payload_size + config_end.len]u8 =
    initConfigBlock();

pub fn main(init: std.process.Init) !void {
    const io = init.io;
    const config = readConfig();
    var bind_addr = try net.IpAddress.parseIp4("0.0.0.0", config.port);
    const sock = try bind_addr.bind(io, .{ .mode = .dgram, .protocol = .udp });
    defer sock.close(io);
    std.debug.print("[SceneMax MP] server listening port={d} game=\"{s}\" projectGuid=\"{s}\"\n", .{
        config.port,
        fixedText(&config.game_name),
        fixedText(&config.project_guid),
    });

    var clients = [_]Client{.{}} ** max_clients;
    var sessions = [_]Session{.{}} ** max_sessions;
    var entities = [_]Entity{.{}} ** max_entities;
    var network_variables = [_]NetworkVariable{.{}} ** max_network_variables;
    var server_events = [_]ServerEvent{.{}} ** max_server_events;
    var next_client_id: u16 = 1;
    var next_session_id: u32 = 1000;
    var next_entity_id: u32 = 1;
    var buffer: [max_packet_size]u8 = undefined;

    while (true) {
        const message = sock.receive(io, &buffer) catch |err| {
            logUdpError("receive", err, null);
            continue;
        };
        const received = message.data.len;
        if (received < 8) continue;

        const packet_magic = std.mem.readInt(u32, buffer[0..][0..4], .little);
        if (packet_magic != protocol_magic or buffer[4] != protocol_version) continue;
        const packet_type = decodePacketType(buffer[5]) orelse continue;
        const client_id = std.mem.readInt(u16, buffer[6..][0..2], .little);
        const payload = buffer[8..received];
        const now = nowMs(io);
        expireActiveActions(&entities, now);

        switch (packet_type) {
            .login_request => {
                const password_ok = verifyPassword(payload, config.password_hash);
                if (!password_ok) {
                    std.debug.print("[SceneMax MP] login rejected: password mismatch from={any}\n", .{message.from});
                    try sendReject(sock, io, message.from);
                    continue;
                }
                const login = decodeLoginRequest(payload) orelse continue;
                if (!verifyProjectGuid(login.project_guid, config.project_guid)) {
                    std.debug.print("[SceneMax MP] login rejected: project GUID mismatch actual=\"{s}\" expected=\"{s}\"\n", .{
                        fixedText(&login.project_guid),
                        fixedText(&config.project_guid),
                    });
                    try sendReject(sock, io, message.from);
                    continue;
                }
                const session = resolveSession(&sessions, &next_session_id, login.requested_session_id, login.create_session, login.session_name);
                const assigned = next_client_id;
                next_client_id +%= 1;
                if (next_client_id == 0) next_client_id = 1;
                const slot = assigned % max_clients;
                clients[slot] = .{
                    .active = true,
                    .id = assigned,
                    .session_id = session.id,
                    .scene_id = login.scene_id,
                    .player_name = login.player_name,
                    .address = message.from,
                    .last_seen_ms = now,
                };
                try sendLoginAccepted(sock, io, message.from, assigned, session.id, session.name);
                try broadcastServerState(sock, io, &clients, &sessions);
                try sendSnapshot(sock, io, message.from, &entities, session.id, login.scene_id, now);
                try sendNetworkVariableSnapshot(sock, io, message.from, &network_variables, session.id, login.scene_id);
                try sendInitialSyncComplete(sock, io, message.from, assigned);
            },
            .heartbeat => {
                if (findClient(&clients, client_id)) |client| {
                    client.last_seen_ms = now;
                    client.address = message.from;
                }
            },
            .join_scene => {
                if (findClient(&clients, client_id)) |client| {
                    client.last_seen_ms = now;
                    client.address = message.from;
                    const old_scene_id = client.scene_id;
                    zero(&client.scene_id);
                    copyFixed(&client.scene_id, payload, 0);
                    if (verbose_packet_logs) {
                        std.debug.print("[SceneMax MP] join scene client={d} session={d} scene=\"{s}\"\n", .{
                            client.id,
                            client.session_id,
                            fixedText(&client.scene_id),
                        });
                    }
                    try destroyClientEntities(sock, io, &clients, &entities, client.id, client.session_id, old_scene_id);
                    clearSceneNetworkStateIfEmpty(&clients, &network_variables, &server_events, client.session_id, old_scene_id);
                    try sendSnapshot(sock, io, message.from, &entities, client.session_id, client.scene_id, now);
                    try sendNetworkVariableSnapshot(sock, io, message.from, &network_variables, client.session_id, client.scene_id);
                    try sendInitialSyncComplete(sock, io, message.from, client.id);
                }
            },
            .join_session => {
                if (findClient(&clients, client_id)) |client| {
                    if (payload.len < 68) continue;
                    client.last_seen_ms = now;
                    client.address = message.from;
                    const old_session_id = client.session_id;
                    const old_scene_id = client.scene_id;
                    const requested_session_id = std.mem.readInt(u32, payload[0..][0..4], .little);
                    var requested_session_name = [_]u8{0} ** 64;
                    copyFixed(&requested_session_name, payload, 4);
                    const session = resolveJoinSession(&sessions, &next_session_id, requested_session_id, requested_session_name);
                    if (verbose_packet_logs) {
                        std.debug.print("[SceneMax MP] join session client={d} from={d} to={d} name=\"{s}\" scene=\"{s}\"\n", .{
                            client.id,
                            old_session_id,
                            session.id,
                            fixedText(&session.name),
                            fixedText(&client.scene_id),
                        });
                    }
                    try destroyClientEntities(sock, io, &clients, &entities, client.id, old_session_id, old_scene_id);
                    client.session_id = session.id;
                    clearSceneNetworkStateIfEmpty(&clients, &network_variables, &server_events, old_session_id, old_scene_id);
                    try sendLoginAccepted(sock, io, message.from, client.id, session.id, session.name);
                    try broadcastServerState(sock, io, &clients, &sessions);
                    try sendSnapshot(sock, io, message.from, &entities, client.session_id, client.scene_id, now);
                    try sendNetworkVariableSnapshot(sock, io, message.from, &network_variables, client.session_id, client.scene_id);
                    try sendInitialSyncComplete(sock, io, message.from, client.id);
                }
            },
            .create_entity_request => {
                const client = findClient(&clients, client_id) orelse continue;
                const client_create_id = readClientCreateId(payload);
                if (client_create_id != 0) {
                    if (findOwnedEntityByCreateId(&entities, client.id, client.session_id, client.scene_id, client_create_id)) |existing| {
                        try sendEntityAccepted(sock, io, message.from, client_id, existing.network_id, client_create_id);
                        continue;
                    }
                }
                const entity_id = next_entity_id;
                next_entity_id +%= 1;
                if (next_entity_id == 0) next_entity_id = 1;
                const slot = entity_id % max_entities;
                entities[slot] = decodeEntityCreate(payload, entity_id, client.*);
                if (verbose_packet_logs) {
                    std.debug.print("[SceneMax MP] create entity client={d} entity={d} session={d} scene=\"{s}\" archetype=\"{s}\" spawn=\"{s}\"\n", .{
                        client.id,
                        entity_id,
                        client.session_id,
                        fixedText(&client.scene_id),
                        fixedText(&entities[slot].archetype),
                        fixedText(&entities[slot].spawn_command),
                    });
                }
                try sendEntityAccepted(sock, io, message.from, client_id, entity_id, entities[slot].client_create_id);
                try dispatchEntityCreated(sock, io, &clients, client_id, entity_id, payload);
            },
            .command_dispatch, .transform_correction => {
                const client = findClient(&clients, client_id) orelse continue;
                if (!isOwnedActiveEntity(&entities, client.id, client.session_id, client.scene_id, payload)) {
                    continue;
                }
                if (verbose_packet_logs) {
                    logRelayPacket(packet_type, client_id, payload);
                }
                if (packet_type == .transform_correction) {
                    updateEntityTransform(&entities, payload);
                }
                try dispatch(sock, io, &clients, client_id, packet_type, client_id, payload);
            },
            .active_action_start => {
                const client = findClient(&clients, client_id) orelse continue;
                const entity = findOwnedActiveEntity(&entities, client.id, client.session_id, client.scene_id, payload) orelse continue;
                storeActiveAction(entity, payload, now);
                if (isStructuralActionStart(payload)) {
                    try dispatch(sock, io, &clients, client_id, packet_type, client_id, payload);
                }
            },
            .active_action_end => {
                const client = findClient(&clients, client_id) orelse continue;
                const entity = findOwnedActiveEntity(&entities, client.id, client.session_id, client.scene_id, payload) orelse continue;
                clearActiveAction(entity, payload);
            },
            .network_event => {
                const client = findClient(&clients, client_id) orelse continue;
                if (payload.len <= 2) continue;
                const target_client_id = std.mem.readInt(u16, payload[0..][0..2], .little);
                try dispatchNetworkEvent(sock, io, &clients, client.*, target_client_id, payload[2..]);
            },
            .network_variable_update => {
                const client = findClient(&clients, client_id) orelse continue;
                if (payload.len < network_variable_update_size) continue;
                client.last_seen_ms = now;
                client.address = message.from;
                const stored = storeNetworkVariable(&network_variables, client.session_id, client.scene_id, payload);
                if (stored) {
                    try dispatch(sock, io, &clients, client_id, packet_type, client_id, payload);
                }
            },
            .server_event_register => {
                const client = findClient(&clients, client_id) orelse continue;
                if (payload.len < server_event_registration_size) continue;
                client.last_seen_ms = now;
                client.address = message.from;
                storeServerEvent(&server_events, client.session_id, client.scene_id, payload, now);
            },
            .destroy_entity => {
                const client = findClient(&clients, client_id) orelse continue;
                if (payload.len < 4) continue;
                const entity_id = std.mem.readInt(u32, payload[0..][0..4], .little);
                if (destroyOwnedEntity(&entities, client.id, client.session_id, client.scene_id, entity_id)) {
                    if (verbose_packet_logs) {
                        std.debug.print("[SceneMax MP] destroy entity client={d} entity={d} session={d} scene=\"{s}\"\n", .{
                            client.id,
                            entity_id,
                            client.session_id,
                            fixedText(&client.scene_id),
                        });
                    }
                    try dispatchEntityDestroyed(sock, io, &clients, client.id, client.session_id, client.scene_id, entity_id);
                } else {
                    if (verbose_packet_logs) {
                        std.debug.print("[SceneMax MP] destroy ignored client={d} entity={d}\n", .{ client.id, entity_id });
                    }
                }
            },
            .disconnect => {
                if (findClient(&clients, client_id)) |client| {
                    if (verbose_packet_logs) {
                        std.debug.print("[SceneMax MP] disconnect client={d} session={d} scene=\"{s}\"\n", .{
                            client.id,
                            client.session_id,
                            fixedText(&client.scene_id),
                        });
                    }
                    const old_session_id = client.session_id;
                    const old_scene_id = client.scene_id;
                    try destroyClientEntities(sock, io, &clients, &entities, client.id, old_session_id, old_scene_id);
                    client.active = false;
                    clearSceneNetworkStateIfEmpty(&clients, &network_variables, &server_events, old_session_id, old_scene_id);
                    try broadcastServerState(sock, io, &clients, &sessions);
                }
            },
            else => {},
        }

        try expireClients(sock, io, &clients, &entities, &network_variables, &server_events, now);
        try dispatchDueServerEvents(sock, io, &clients, &server_events, now);
    }
}

fn initConfigBlock() [config_begin.len + config_payload_size + config_end.len]u8 {
    var block = [_]u8{0} ** (config_begin.len + config_payload_size + config_end.len);
    @memcpy(block[0..config_begin.len], config_begin);
    @memcpy(block[config_begin.len + config_payload_size ..], config_end);
    return block;
}

fn nowMs(io: std.Io) i64 {
    const ns = std.Io.Clock.awake.now(io).nanoseconds;
    return @intCast(@divTrunc(ns, std.time.ns_per_ms));
}

fn readConfig() ServerConfig {
    const payload = scenemax_mp_config_block[config_begin.len .. config_begin.len + config_payload_size];
    var config = ServerConfig{};
    if (!std.mem.eql(u8, payload[0..8], "SMXMPCFG")) {
        return config;
    }
    const port = std.mem.readInt(u32, payload[12..][0..4], .little);
    config.port = if (port == 0 or port > 65535) 9001 else @intCast(port);
    @memcpy(&config.game_name, payload[16..144]);
    @memcpy(&config.project_path, payload[144..400]);
    @memcpy(&config.password_hash, payload[400..432]);
    @memcpy(&config.project_guid, payload[432..496]);
    return config;
}

fn verifyPassword(payload: []const u8, expected_hash: [32]u8) bool {
    if (isZeroHash(expected_hash)) return true;
    if (payload.len < 32) return false;
    return std.mem.eql(u8, payload[0..32], &expected_hash);
}

fn isZeroHash(hash: [32]u8) bool {
    for (hash) |value| {
        if (value != 0) return false;
    }
    return true;
}

fn verifyProjectGuid(actual_guid: [64]u8, expected_guid: [64]u8) bool {
    if (isEmpty(&expected_guid)) return true;
    return std.mem.eql(u8, &actual_guid, &expected_guid);
}

const LoginRequest = struct {
    project_guid: [64]u8 = [_]u8{0} ** 64,
    create_session: bool = false,
    requested_session_id: u32 = 0,
    session_name: [64]u8 = [_]u8{0} ** 64,
    scene_id: [128]u8 = [_]u8{0} ** 128,
    player_name: [64]u8 = [_]u8{0} ** 64,
};

fn decodeLoginRequest(payload: []const u8) ?LoginRequest {
    if (payload.len < 357) return null;
    var login = LoginRequest{};
    copyFixed(&login.project_guid, payload, 32);
    login.create_session = payload[96] != 0;
    login.requested_session_id = std.mem.readInt(u32, payload[97..][0..4], .little);
    copyFixed(&login.session_name, payload, 101);
    copyFixed(&login.scene_id, payload, 165);
    copyFixed(&login.player_name, payload, 293);
    if (isEmpty(&login.scene_id)) {
        writeAscii(&login.scene_id, "main");
    }
    return login;
}

fn resolveSession(sessions: *[max_sessions]Session, next_session_id: *u32, requested_id: u32, create: bool, requested_name: [64]u8) *Session {
    if (!create and requested_id != 0) {
        if (findSession(sessions, requested_id)) |session| return session;
    }

    const id = if (create or requested_id == 0) blk: {
        const allocated = next_session_id.*;
        next_session_id.* += 1;
        break :blk allocated;
    } else requested_id;

    const slot = id % max_sessions;
    sessions[slot] = .{ .active = true, .id = id };
    if (isEmpty(&requested_name)) {
        writeDefaultSessionName(&sessions[slot].name, id);
    } else {
        sessions[slot].name = requested_name;
    }
    return &sessions[slot];
}

fn resolveJoinSession(sessions: *[max_sessions]Session, next_session_id: *u32, requested_id: u32, requested_name: [64]u8) *Session {
    if (requested_id != 0) {
        if (findSession(sessions, requested_id)) |session| return session;
        const slot = requested_id % max_sessions;
        sessions[slot] = .{ .active = true, .id = requested_id };
        if (isEmpty(&requested_name)) {
            writeDefaultSessionName(&sessions[slot].name, requested_id);
        } else {
            sessions[slot].name = requested_name;
        }
        return &sessions[slot];
    }

    if (!isEmpty(&requested_name)) {
        if (findSessionByName(sessions, requested_name)) |session| return session;
    }

    const allocated = next_session_id.*;
    next_session_id.* += 1;
    const slot = allocated % max_sessions;
    sessions[slot] = .{ .active = true, .id = allocated };
    if (isEmpty(&requested_name)) {
        writeDefaultSessionName(&sessions[slot].name, allocated);
    } else {
        sessions[slot].name = requested_name;
    }
    return &sessions[slot];
}

fn findSession(sessions: *[max_sessions]Session, session_id: u32) ?*Session {
    for (sessions) |*session| {
        if (session.active and session.id == session_id) return session;
    }
    return null;
}

fn findSessionByName(sessions: *[max_sessions]Session, session_name: [64]u8) ?*Session {
    if (isEmpty(&session_name)) return null;
    const wanted = fixedText(&session_name);
    for (sessions) |*session| {
        if (!session.active) continue;
        if (std.mem.eql(u8, fixedText(&session.name), wanted)) return session;
    }
    return null;
}

fn findClient(clients: *[max_clients]Client, client_id: u16) ?*Client {
    for (clients) |*client| {
        if (client.active and client.id == client_id) return client;
    }
    return null;
}

fn readClientCreateId(payload: []const u8) u32 {
    if (payload.len < 4) return 0;
    return std.mem.readInt(u32, payload[0..][0..4], .little);
}

fn findOwnedEntityByCreateId(entities: *[max_entities]Entity, client_id: u16, session_id: u32, scene_id: [128]u8, client_create_id: u32) ?*Entity {
    for (entities) |*entity| {
        if (!entity.active or entity.client_create_id != client_create_id) continue;
        if (entity.owner_client == client_id and entity.session_id == session_id and sameScene(entity.scene_id, scene_id)) {
            return entity;
        }
    }
    return null;
}

fn isOwnedActiveEntity(entities: *[max_entities]Entity, client_id: u16, session_id: u32, scene_id: [128]u8, payload: []const u8) bool {
    return findOwnedActiveEntity(entities, client_id, session_id, scene_id, payload) != null;
}

fn findOwnedActiveEntity(entities: *[max_entities]Entity, client_id: u16, session_id: u32, scene_id: [128]u8, payload: []const u8) ?*Entity {
    if (payload.len < 4) return null;
    const entity_id = std.mem.readInt(u32, payload[0..][0..4], .little);
    for (entities) |*entity| {
        if (!entity.active or entity.network_id != entity_id) continue;
        if (entity.owner_client == client_id and entity.session_id == session_id and sameScene(entity.scene_id, scene_id)) {
            return entity;
        }
        return null;
    }
    return null;
}

fn expireClients(sock: net.Socket, io: std.Io, clients: *[max_clients]Client, entities: *[max_entities]Entity, network_variables: *[max_network_variables]NetworkVariable, server_events: *[max_server_events]ServerEvent, now: i64) !void {
    for (clients) |*client| {
        if (client.active and now - client.last_seen_ms > 15000) {
            std.debug.print("[SceneMax MP] client timeout client={d} session={d} scene=\"{s}\"\n", .{
                client.id,
                client.session_id,
                fixedText(&client.scene_id),
            });
            const old_session_id = client.session_id;
            const old_scene_id = client.scene_id;
            try destroyClientEntities(sock, io, clients, entities, client.id, old_session_id, old_scene_id);
            client.active = false;
            clearSceneNetworkStateIfEmpty(clients, network_variables, server_events, old_session_id, old_scene_id);
        }
    }
}

fn storeActiveAction(entity: *Entity, payload: []const u8, now: i64) void {
    if (payload.len < 12 + active_action_command_size) return;
    const slot = payload[4];
    if (slot == 0) return;
    const sequence = std.mem.readInt(u16, payload[6..][0..2], .little);
    const duration_ms = std.mem.readInt(u32, payload[8..][0..4], .little);
    var target: ?*ActiveAction = null;
    for (&entity.active_actions) |*action| {
        if (action.active and action.slot == slot) {
            if (sequence < action.sequence) {
                return;
            }
            target = action;
            break;
        }
    }
    if (target == null) {
        for (&entity.active_actions) |*action| {
            if (!action.active) {
                target = action;
                break;
            }
        }
    }
    const action = target orelse return;
    action.* = .{
        .active = true,
        .slot = slot,
        .sequence = sequence,
        .started_at_ms = now,
        .duration_ms = duration_ms,
    };
    copyFixed(&action.command, payload, 12);
    if (verbose_packet_logs) {
        std.debug.print("[SceneMax MP] active action start entity={d} slot={d} seq={d} durationMs={d} command=\"{s}\"\n", .{
            entity.network_id,
            slot,
            sequence,
            duration_ms,
            fixedText(&action.command),
        });
    }
}

fn isStructuralActionStart(payload: []const u8) bool {
    if (payload.len < 5) return false;
    return payload[4] >= 64;
}

fn clearActiveAction(entity: *Entity, payload: []const u8) void {
    if (payload.len < 8) return;
    const slot = payload[4];
    const sequence = std.mem.readInt(u16, payload[6..][0..2], .little);
    for (&entity.active_actions) |*action| {
        if (action.active and action.slot == slot and action.sequence == sequence) {
            action.active = false;
            if (verbose_packet_logs) {
                std.debug.print("[SceneMax MP] active action end entity={d} slot={d} seq={d}\n", .{
                    entity.network_id,
                    slot,
                    sequence,
                });
            }
            return;
        }
    }
}

fn expireActiveActions(entities: *[max_entities]Entity, now: i64) void {
    for (entities) |*entity| {
        if (!entity.active) continue;
        for (&entity.active_actions) |*action| {
            if (!action.active or action.duration_ms == 0) continue;
            if (now - action.started_at_ms > @as(i64, @intCast(action.duration_ms)) + active_action_grace_ms) {
                action.active = false;
            }
        }
    }
}

fn decodeEntityCreate(payload: []const u8, entity_id: u32, client: Client) Entity {
    const create_offset: usize = if (payload.len >= 4) 4 else 0;
    var entity = Entity{
        .active = true,
        .network_id = entity_id,
        .client_create_id = readClientCreateId(payload),
        .owner_client = client.id,
        .session_id = client.session_id,
        .scene_id = client.scene_id,
        .rotation = .{ 0, 0, 0, 1 },
    };
    copyFixed(&entity.archetype, payload, create_offset);
    copyFixed(&entity.player_name, payload, create_offset + 64);
    const extended_create_size = create_offset + 128 + source_object_name_size + spawn_command_size;
    if (payload.len >= extended_create_size) {
        copyFixed(&entity.source_object_name, payload, create_offset + 128);
        copyFixed(&entity.spawn_command, payload, create_offset + 128 + source_object_name_size);
    } else {
        copyFixed(&entity.spawn_command, payload, create_offset + 128);
    }
    return entity;
}

fn copyFixed(dest: []u8, payload: []const u8, offset: usize) void {
    if (payload.len <= offset) return;
    const count = @min(dest.len, payload.len - offset);
    @memcpy(dest[0..count], payload[offset .. offset + count]);
}

fn decodePacketType(value: u8) ?PacketType {
    return switch (value) {
        1 => .login_request,
        2 => .login_accepted,
        3 => .login_rejected,
        4 => .heartbeat,
        5 => .join_scene,
        6 => .join_session,
        10 => .create_entity_request,
        11 => .create_entity_accepted,
        12 => .destroy_entity,
        20 => .command_dispatch,
        21 => .transform_correction,
        22 => .active_action_start,
        23 => .active_action_end,
        24 => .network_event,
        25 => .network_variable_update,
        26 => .server_event_register,
        30 => .snapshot,
        31 => .server_state,
        32 => .initial_sync_complete,
        40 => .disconnect,
        else => null,
    };
}

fn updateEntityTransform(entities: *[max_entities]Entity, payload: []const u8) void {
    if (payload.len < 32) return;
    const entity_id = std.mem.readInt(u32, payload[0..][0..4], .little);
    for (entities) |*entity| {
        if (entity.active and entity.network_id == entity_id) {
            entity.position[0] = readF32(payload[4..8]);
            entity.position[1] = readF32(payload[8..12]);
            entity.position[2] = readF32(payload[12..16]);
            entity.rotation[0] = readF32(payload[16..20]);
            entity.rotation[1] = readF32(payload[20..24]);
            entity.rotation[2] = readF32(payload[24..28]);
            entity.rotation[3] = readF32(payload[28..32]);
            return;
        }
    }
}

fn destroyClientEntities(sock: net.Socket, io: std.Io, clients: *[max_clients]Client, entities: *[max_entities]Entity, client_id: u16, session_id: u32, scene_id: [128]u8) !void {
    for (entities) |*entity| {
        if (!entity.active or entity.owner_client != client_id or entity.session_id != session_id or !sameScene(entity.scene_id, scene_id)) {
            continue;
        }
        try dispatchEntityDestroyed(sock, io, clients, client_id, session_id, scene_id, entity.network_id);
        entity.active = false;
    }
}

fn destroyOwnedEntity(entities: *[max_entities]Entity, client_id: u16, session_id: u32, scene_id: [128]u8, entity_id: u32) bool {
    for (entities) |*entity| {
        if (!entity.active or entity.network_id != entity_id) continue;
        if (entity.owner_client != client_id or entity.session_id != session_id or !sameScene(entity.scene_id, scene_id)) {
            return false;
        }
        entity.active = false;
        return true;
    }
    return false;
}

fn clearSceneNetworkStateIfEmpty(clients: *[max_clients]Client, network_variables: *[max_network_variables]NetworkVariable, server_events: *[max_server_events]ServerEvent, session_id: u32, scene_id: [128]u8) void {
    if (connectedClientCountInScene(clients, session_id, scene_id) != 0) {
        return;
    }
    clearSceneNetworkVariables(network_variables, session_id, scene_id);
    clearSceneServerEvents(server_events, session_id, scene_id);
}

fn clearSceneNetworkVariables(network_variables: *[max_network_variables]NetworkVariable, session_id: u32, scene_id: [128]u8) void {
    var cleared: usize = 0;
    for (network_variables) |*network_variable| {
        if (!network_variable.active) continue;
        if (network_variable.session_id != session_id or !sameScene(network_variable.scene_id, scene_id)) continue;
        network_variable.active = false;
        cleared += 1;
    }
    if (cleared > 0) {
        std.debug.print("[SceneMax MP] cleared network variables session={d} scene=\"{s}\" count={d}\n", .{
            session_id,
            fixedText(&scene_id),
            cleared,
        });
    }
}

fn clearSceneServerEvents(server_events: *[max_server_events]ServerEvent, session_id: u32, scene_id: [128]u8) void {
    var cleared: usize = 0;
    for (server_events) |*server_event| {
        if (!server_event.active) continue;
        if (server_event.session_id != session_id or !sameScene(server_event.scene_id, scene_id)) continue;
        server_event.active = false;
        cleared += 1;
    }
    if (cleared > 0) {
        std.debug.print("[SceneMax MP] cleared server events session={d} scene=\"{s}\" count={d}\n", .{
            session_id,
            fixedText(&scene_id),
            cleared,
        });
    }
}

fn storeServerEvent(server_events: *[max_server_events]ServerEvent, session_id: u32, scene_id: [128]u8, payload: []const u8, now: i64) void {
    if (payload.len < server_event_registration_size) return;
    var name = [_]u8{0} ** server_event_name_size;
    copyFixed(&name, payload, 0);
    if (isEmpty(&name)) return;
    var interval_ms = std.mem.readInt(u32, payload[server_event_name_size..][0..4], .little);
    if (interval_ms < minimum_server_event_interval_ms) {
        interval_ms = minimum_server_event_interval_ms;
    }

    if (findServerEvent(server_events, session_id, scene_id, name)) |existing| {
        if (existing.interval_ms != interval_ms) {
            existing.interval_ms = interval_ms;
            existing.next_fire_ms = now + @as(i64, @intCast(interval_ms));
        }
        return;
    }

    const slot = serverEventSlot(server_events, session_id, scene_id, name) orelse return;
    server_events[slot] = .{
        .active = true,
        .session_id = session_id,
        .scene_id = scene_id,
        .name = name,
        .interval_ms = interval_ms,
        .next_fire_ms = now + @as(i64, @intCast(interval_ms)),
    };
    if (verbose_packet_logs) {
        std.debug.print("[SceneMax MP] registered server event session={d} scene=\"{s}\" name=\"{s}\" intervalMs={d}\n", .{
            session_id,
            fixedText(&scene_id),
            fixedText(&name),
            interval_ms,
        });
    }
}

fn findServerEvent(server_events: *[max_server_events]ServerEvent, session_id: u32, scene_id: [128]u8, name: [server_event_name_size]u8) ?*ServerEvent {
    const wanted = fixedText(&name);
    for (server_events) |*server_event| {
        if (!server_event.active) continue;
        if (server_event.session_id != session_id or !sameScene(server_event.scene_id, scene_id)) continue;
        if (std.mem.eql(u8, fixedText(&server_event.name), wanted)) return server_event;
    }
    return null;
}

fn serverEventSlot(server_events: *[max_server_events]ServerEvent, session_id: u32, scene_id: [128]u8, name: [server_event_name_size]u8) ?usize {
    var hash = std.hash.Wyhash.init(0);
    hash.update(std.mem.asBytes(&session_id));
    hash.update(&scene_id);
    hash.update(fixedText(&name));
    const start: usize = @intCast(hash.final() % max_server_events);
    var i: usize = 0;
    while (i < max_server_events) : (i += 1) {
        const slot = (start + i) % max_server_events;
        if (!server_events[slot].active) return slot;
    }
    return null;
}

fn storeNetworkVariable(network_variables: *[max_network_variables]NetworkVariable, session_id: u32, scene_id: [128]u8, payload: []const u8) bool {
    if (payload.len < network_variable_update_size) return false;
    const declaration_init = payload[network_variable_name_size] != 0;
    var name = [_]u8{0} ** network_variable_name_size;
    var value = [_]u8{0} ** network_variable_value_size;
    copyFixed(&name, payload, 0);
    copyFixed(&value, payload, network_variable_name_size + 1);
    if (isEmpty(&name)) return false;

    if (findNetworkVariable(network_variables, session_id, scene_id, name)) |existing| {
        if (declaration_init) return false;
        existing.value = value;
        return true;
    }

    const slot = networkVariableSlot(network_variables, session_id, scene_id, name) orelse return false;
    network_variables[slot] = .{
        .active = true,
        .session_id = session_id,
        .scene_id = scene_id,
        .name = name,
        .value = value,
    };
    return true;
}

fn findNetworkVariable(network_variables: *[max_network_variables]NetworkVariable, session_id: u32, scene_id: [128]u8, name: [network_variable_name_size]u8) ?*NetworkVariable {
    const wanted = fixedText(&name);
    for (network_variables) |*network_variable| {
        if (!network_variable.active) continue;
        if (network_variable.session_id != session_id or !sameScene(network_variable.scene_id, scene_id)) continue;
        if (std.mem.eql(u8, fixedText(&network_variable.name), wanted)) return network_variable;
    }
    return null;
}

fn networkVariableSlot(network_variables: *[max_network_variables]NetworkVariable, session_id: u32, scene_id: [128]u8, name: [network_variable_name_size]u8) ?usize {
    var hash = std.hash.Wyhash.init(0);
    hash.update(std.mem.asBytes(&session_id));
    hash.update(&scene_id);
    hash.update(fixedText(&name));
    const start: usize = @intCast(hash.final() % max_network_variables);
    var i: usize = 0;
    while (i < max_network_variables) : (i += 1) {
        const slot = (start + i) % max_network_variables;
        if (!network_variables[slot].active) return slot;
    }
    return null;
}

fn readF32(bytes: []const u8) f32 {
    return @bitCast(std.mem.readInt(u32, bytes[0..][0..4], .little));
}

fn sendUdp(sock: net.Socket, io: std.Io, address: net.IpAddress, bytes: []const u8) void {
    sock.send(io, &address, bytes) catch |err| {
        logUdpError("send", err, address);
    };
}

fn logUdpError(operation: []const u8, err: anyerror, address: ?net.IpAddress) void {
    if (err == error.PortUnreachable) {
        return;
    }
    if (address) |target| {
        std.debug.print("[SceneMax MP] udp {s} ignored error={any} to={any}\n", .{ operation, err, target });
    } else {
        std.debug.print("[SceneMax MP] udp {s} ignored error={any}\n", .{ operation, err });
    }
}

fn sendReject(sock: net.Socket, io: std.Io, address: net.IpAddress) !void {
    var packet: [8]u8 = undefined;
    writeHeader(packet[0..], .login_rejected, 0);
    sendUdp(sock, io, address, &packet);
}

fn sendLoginAccepted(sock: net.Socket, io: std.Io, address: net.IpAddress, client_id: u16, session_id: u32, session_name: [64]u8) !void {
    var packet: [78]u8 = undefined;
    writeHeader(packet[0..], .login_accepted, client_id);
    std.mem.writeInt(u16, packet[8..][0..2], client_id, .little);
    std.mem.writeInt(u32, packet[10..][0..4], session_id, .little);
    @memcpy(packet[14..78], &session_name);
    sendUdp(sock, io, address, &packet);
}

fn sendServerState(sock: net.Socket, io: std.Io, address: net.IpAddress, sessions: *[max_sessions]Session, clients: *[max_clients]Client) !void {
    const record_size: usize = 70;
    const max_records_per_packet: usize = (max_packet_size - 14) / record_size;
    const total = activeSessionCount(sessions);
    var offset: u16 = 0;

    while (offset < total or (total == 0 and offset == 0)) {
        var packet: [max_packet_size]u8 = undefined;
        writeHeader(packet[0..], .server_state, 0);
        std.mem.writeInt(u16, packet[8..][0..2], total, .little);
        std.mem.writeInt(u16, packet[10..][0..2], offset, .little);
        var cursor: usize = 14;
        var count: u16 = 0;
        var seen: u16 = 0;

        for (sessions) |session| {
            if (!session.active) continue;
            if (seen < offset) {
                seen += 1;
                continue;
            }
            if (count >= max_records_per_packet) break;
            std.mem.writeInt(u32, packet[cursor..][0..4], session.id, .little);
            cursor += 4;
            std.mem.writeInt(u16, packet[cursor..][0..2], connectedClientCount(clients, session.id), .little);
            cursor += 2;
            @memcpy(packet[cursor .. cursor + 64], &session.name);
            cursor += 64;
            count += 1;
            seen += 1;
        }

        std.mem.writeInt(u16, packet[12..][0..2], count, .little);
        sendUdp(sock, io, address, packet[0..cursor]);
        if (total == 0) break;
        offset += count;
        if (count == 0) break;
    }
}

fn broadcastServerState(sock: net.Socket, io: std.Io, clients: *[max_clients]Client, sessions: *[max_sessions]Session) !void {
    for (clients) |client| {
        if (!client.active) continue;
        try sendServerState(sock, io, client.address, sessions, clients);
    }
}

fn activeSessionCount(sessions: *[max_sessions]Session) u16 {
    var count: u16 = 0;
    for (sessions) |session| {
        if (session.active) count += 1;
    }
    return count;
}

fn connectedClientCount(clients: *[max_clients]Client, session_id: u32) u16 {
    var count: u16 = 0;
    for (clients) |client| {
        if (client.active and client.session_id == session_id) count += 1;
    }
    return count;
}

fn connectedClientCountInScene(clients: *[max_clients]Client, session_id: u32, scene_id: [128]u8) u16 {
    var count: u16 = 0;
    for (clients) |client| {
        if (client.active and client.session_id == session_id and sameScene(client.scene_id, scene_id)) count += 1;
    }
    return count;
}

fn sendEntityAccepted(sock: net.Socket, io: std.Io, address: net.IpAddress, client_id: u16, entity_id: u32, client_create_id: u32) !void {
    var packet: [16]u8 = undefined;
    writeHeader(packet[0..], .create_entity_accepted, client_id);
    std.mem.writeInt(u32, packet[8..][0..4], entity_id, .little);
    std.mem.writeInt(u32, packet[12..][0..4], client_create_id, .little);
    sendUdp(sock, io, address, &packet);
}

fn sendSnapshot(sock: net.Socket, io: std.Io, address: net.IpAddress, entities: *[max_entities]Entity, session_id: u32, scene_id: [128]u8, now: i64) !void {
    var packet: [max_packet_size]u8 = undefined;
    writeHeader(packet[0..], .snapshot, 0);
    var cursor: usize = 10;
    var count: u16 = 0;
    var total_count: u16 = 0;
    var packet_count: u16 = 0;
    const max_actions_per_record = (packet.len - 10 - snapshot_entity_base_size) / snapshot_action_record_size;
    for (entities) |entity| {
        if (!entity.active) continue;
        if (entity.session_id != session_id or !sameScene(entity.scene_id, scene_id)) continue;

        const action_count = activeActionCount(entity, now);
        var action_offset: usize = 0;
        while (action_offset < action_count or (action_count == 0 and action_offset == 0)) {
            const remaining_actions = action_count - action_offset;
            const chunk_action_count: usize = if (max_actions_per_record == 0)
                0
            else
                @min(remaining_actions, max_actions_per_record);
            const required = snapshot_entity_base_size + chunk_action_count * snapshot_action_record_size;
            if (required > packet.len - 10) break;
            if (cursor + required > packet.len) {
                std.mem.writeInt(u16, packet[8..][0..2], count, .little);
                sendUdp(sock, io, address, packet[0..cursor]);
                packet_count += 1;
                writeHeader(packet[0..], .snapshot, 0);
                cursor = 10;
                count = 0;
            }

            cursor = writeSnapshotEntity(packet[0..], cursor, entity);
            cursor = writeSnapshotActions(packet[0..], cursor, entity, now, action_offset, chunk_action_count);
            count += 1;

            if (action_count == 0 or chunk_action_count == 0) break;
            action_offset += chunk_action_count;
        }
        total_count += 1;
    }
    if (count > 0 or total_count == 0) {
        std.mem.writeInt(u16, packet[8..][0..2], count, .little);
        sendUdp(sock, io, address, packet[0..cursor]);
        packet_count += 1;
    }
    if (verbose_packet_logs) {
        std.debug.print("[SceneMax MP] snapshot session={d} scene=\"{s}\" entities={d} packets={d}\n", .{
            session_id,
            fixedText(&scene_id),
            total_count,
            packet_count,
        });
    }
}

fn sendNetworkVariableSnapshot(sock: net.Socket, io: std.Io, address: net.IpAddress, network_variables: *[max_network_variables]NetworkVariable, session_id: u32, scene_id: [128]u8) !void {
    var sent: usize = 0;
    for (network_variables) |network_variable| {
        if (!network_variable.active) continue;
        if (network_variable.session_id != session_id or !sameScene(network_variable.scene_id, scene_id)) continue;

        var packet: [8 + network_variable_update_size]u8 = undefined;
        writeHeader(packet[0..], .network_variable_update, 0);
        @memcpy(packet[8 .. 8 + network_variable_name_size], &network_variable.name);
        packet[8 + network_variable_name_size] = 0;
        @memcpy(packet[8 + network_variable_name_size + 1 .. 8 + network_variable_update_size], &network_variable.value);
        sendUdp(sock, io, address, packet[0..]);
        sent += 1;
    }
    if (verbose_packet_logs) {
        std.debug.print("[SceneMax MP] variable snapshot session={d} scene=\"{s}\" variables={d}\n", .{
            session_id,
            fixedText(&scene_id),
            sent,
        });
    }
}

fn sendInitialSyncComplete(sock: net.Socket, io: std.Io, address: net.IpAddress, client_id: u16) !void {
    var packet: [8]u8 = undefined;
    writeHeader(packet[0..], .initial_sync_complete, client_id);
    sendUdp(sock, io, address, &packet);
}

fn writeSnapshotEntity(packet: []u8, start_cursor: usize, entity: Entity) usize {
    var cursor = start_cursor;
    std.mem.writeInt(u32, packet[cursor..][0..4], entity.network_id, .little);
    cursor += 4;
    std.mem.writeInt(u16, packet[cursor..][0..2], entity.owner_client, .little);
    cursor += 2;
    @memcpy(packet[cursor .. cursor + 64], &entity.archetype);
    cursor += 64;
    @memcpy(packet[cursor .. cursor + 64], &entity.player_name);
    cursor += 64;
    @memcpy(packet[cursor .. cursor + source_object_name_size], &entity.source_object_name);
    cursor += source_object_name_size;
    for (entity.position) |value| {
        std.mem.writeInt(u32, packet[cursor..][0..4], @bitCast(value), .little);
        cursor += 4;
    }
    for (entity.rotation) |value| {
        std.mem.writeInt(u32, packet[cursor..][0..4], @bitCast(value), .little);
        cursor += 4;
    }
    std.mem.writeInt(u16, packet[cursor..][0..2], entity.animation_index, .little);
    cursor += 2;
    @memcpy(packet[cursor .. cursor + 64], &entity.animation);
    cursor += 64;
    @memcpy(packet[cursor .. cursor + spawn_command_size], &entity.spawn_command);
    cursor += spawn_command_size;
    return cursor;
}

fn activeActionCount(entity: Entity, now: i64) usize {
    var count: usize = 0;
    for (entity.active_actions) |action| {
        if (!action.active) continue;
        if (remainingActionMs(action, now) == 0) continue;
        count += 1;
    }
    return count;
}

fn writeSnapshotActions(packet: []u8, start_cursor: usize, entity: Entity, now: i64, action_offset: usize, max_actions: usize) usize {
    var cursor = start_cursor;
    const count_cursor = cursor;
    packet[cursor] = 0;
    cursor += 1;
    var skipped: usize = 0;
    var written: usize = 0;
    for (entity.active_actions) |action| {
        if (!action.active) continue;
        const remaining_ms = remainingActionMs(action, now);
        if (remaining_ms == 0) continue;
        if (skipped < action_offset) {
            skipped += 1;
            continue;
        }
        if (written >= max_actions) break;
        packet[cursor] = action.slot;
        packet[cursor + 1] = 0;
        std.mem.writeInt(u16, packet[cursor + 2 ..][0..2], action.sequence, .little);
        std.mem.writeInt(u32, packet[cursor + 4 ..][0..4], remaining_ms, .little);
        std.mem.writeInt(u32, packet[cursor + 8 ..][0..4], action.duration_ms, .little);
        @memcpy(packet[cursor + 12 .. cursor + 12 + active_action_command_size], &action.command);
        cursor += snapshot_action_record_size;
        written += 1;
    }
    packet[count_cursor] = @intCast(written);
    return cursor;
}

fn remainingActionMs(action: ActiveAction, now: i64) u32 {
    if (!action.active) return 0;
    if (action.duration_ms == 0) return 0;
    const elapsed = now - action.started_at_ms;
    if (elapsed <= 0) return action.duration_ms;
    const capped_elapsed: i64 = @min(elapsed, @as(i64, std.math.maxInt(u32)));
    const elapsed_ms: u32 = @intCast(capped_elapsed);
    if (elapsed_ms >= action.duration_ms) return 0;
    return action.duration_ms - elapsed_ms;
}

fn dispatchEntityCreated(sock: net.Socket, io: std.Io, clients: *[max_clients]Client, sender_client_id: u16, entity_id: u32, payload: []const u8) !void {
    var packet: [max_packet_size]u8 = undefined;
    writeHeader(packet[0..], .create_entity_accepted, sender_client_id);
    std.mem.writeInt(u32, packet[8..][0..4], entity_id, .little);
    const payload_offset: usize = if (payload.len >= 4) 4 else 0;
    const payload_len = @min(payload.len - payload_offset, packet.len - 12);
    @memcpy(packet[12 .. 12 + payload_len], payload[payload_offset .. payload_offset + payload_len]);
    const sender = findClient(clients, sender_client_id) orelse return;
    var sent: usize = 0;
    for (clients) |client| {
        if (!client.active or client.id == sender_client_id) continue;
        if (client.session_id != sender.session_id or !sameScene(client.scene_id, sender.scene_id)) continue;
        sendUdp(sock, io, client.address, packet[0 .. 12 + payload_len]);
        sent += 1;
    }
    if (verbose_packet_logs) {
        std.debug.print("[SceneMax MP] dispatched create entity={d} from={d} recipients={d}\n", .{ entity_id, sender_client_id, sent });
    }
}

fn dispatchEntityDestroyed(sock: net.Socket, io: std.Io, clients: *[max_clients]Client, sender_client_id: u16, session_id: u32, scene_id: [128]u8, entity_id: u32) !void {
    var packet: [12]u8 = undefined;
    writeHeader(packet[0..], .destroy_entity, sender_client_id);
    std.mem.writeInt(u32, packet[8..][0..4], entity_id, .little);
    var sent: usize = 0;
    for (clients) |client| {
        if (!client.active or client.id == sender_client_id) continue;
        if (client.session_id != session_id or !sameScene(client.scene_id, scene_id)) continue;
        sendUdp(sock, io, client.address, packet[0..]);
        sent += 1;
    }
    if (verbose_packet_logs) {
        std.debug.print("[SceneMax MP] dispatched destroy entity={d} from={d} recipients={d}\n", .{ entity_id, sender_client_id, sent });
    }
}

fn dispatch(sock: net.Socket, io: std.Io, clients: *[max_clients]Client, sender_client_id: u16, packet_type: PacketType, client_id: u16, payload: []const u8) !void {
    var packet: [max_packet_size]u8 = undefined;
    writeHeader(packet[0..], packet_type, client_id);
    const payload_len = @min(payload.len, packet.len - 8);
    @memcpy(packet[8 .. 8 + payload_len], payload[0..payload_len]);
    const sender = findClient(clients, sender_client_id) orelse return;
    var sent: usize = 0;
    for (clients) |client| {
        if (!client.active or client.id == sender_client_id) continue;
        if (client.session_id != sender.session_id or !sameScene(client.scene_id, sender.scene_id)) continue;
        sendUdp(sock, io, client.address, packet[0 .. 8 + payload_len]);
        sent += 1;
    }
    if (verbose_packet_logs and packet_type != .transform_correction) {
        std.debug.print("[SceneMax MP] dispatched {s} from={d} recipients={d} bytes={d}\n", .{
            packetTypeName(packet_type),
            sender_client_id,
            sent,
            payload_len,
        });
    }
}

fn dispatchNetworkEvent(sock: net.Socket, io: std.Io, clients: *[max_clients]Client, sender: Client, target_client_id: u16, event_payload: []const u8) !void {
    if (target_client_id == 0 or event_payload.len == 0) return;
    var packet: [max_packet_size]u8 = undefined;
    writeHeader(packet[0..], .network_event, sender.id);
    const payload_len = @min(event_payload.len, packet.len - 8);
    @memcpy(packet[8 .. 8 + payload_len], event_payload[0..payload_len]);
    for (clients) |client| {
        if (!client.active or client.id != target_client_id) continue;
        if (client.session_id != sender.session_id or !sameScene(client.scene_id, sender.scene_id)) return;
        sendUdp(sock, io, client.address, packet[0 .. 8 + payload_len]);
        if (verbose_packet_logs) {
            std.debug.print("[SceneMax MP] dispatched network event from={d} to={d} bytes={d}\n", .{
                sender.id,
                target_client_id,
                payload_len,
            });
        }
        return;
    }
}

fn dispatchDueServerEvents(sock: net.Socket, io: std.Io, clients: *[max_clients]Client, server_events: *[max_server_events]ServerEvent, now: i64) !void {
    for (server_events) |*server_event| {
        if (!server_event.active or server_event.interval_ms == 0 or now < server_event.next_fire_ms) continue;
        const interval: i64 = @intCast(server_event.interval_ms);
        while (server_event.next_fire_ms <= now) {
            server_event.next_fire_ms += interval;
        }
        try broadcastServerEvent(sock, io, clients, server_event);
    }
}

fn broadcastServerEvent(sock: net.Socket, io: std.Io, clients: *[max_clients]Client, server_event: *const ServerEvent) !void {
    const event_payload = fixedText(&server_event.name);
    if (event_payload.len == 0) return;
    var packet: [max_packet_size]u8 = undefined;
    writeHeader(packet[0..], .network_event, 0);
    const payload_len = @min(event_payload.len, packet.len - 8);
    @memcpy(packet[8 .. 8 + payload_len], event_payload[0..payload_len]);
    var sent: usize = 0;
    for (clients) |client| {
        if (!client.active) continue;
        if (client.session_id != server_event.session_id or !sameScene(client.scene_id, server_event.scene_id)) continue;
        sendUdp(sock, io, client.address, packet[0 .. 8 + payload_len]);
        sent += 1;
    }
    if (verbose_packet_logs) {
        std.debug.print("[SceneMax MP] fired server event session={d} scene=\"{s}\" name=\"{s}\" recipients={d}\n", .{
            server_event.session_id,
            fixedText(&server_event.scene_id),
            event_payload,
            sent,
        });
    }
}

fn sameScene(a: [128]u8, b: [128]u8) bool {
    return std.mem.eql(u8, &a, &b);
}

fn isEmpty(bytes: []const u8) bool {
    return bytes.len == 0 or bytes[0] == 0;
}

fn zero(bytes: []u8) void {
    @memset(bytes, 0);
}

fn writeAscii(dest: []u8, value: []const u8) void {
    zero(dest);
    const count = @min(dest.len - 1, value.len);
    @memcpy(dest[0..count], value[0..count]);
}

fn writeDefaultSessionName(dest: []u8, session_id: u32) void {
    zero(dest);
    const prefix = "session #";
    @memcpy(dest[0..prefix.len], prefix);
    var buf: [16]u8 = undefined;
    const id_text = std.fmt.bufPrint(&buf, "{d}", .{session_id}) catch "0";
    const count = @min(dest.len - prefix.len - 1, id_text.len);
    @memcpy(dest[prefix.len .. prefix.len + count], id_text[0..count]);
}

fn writeHeader(packet: []u8, packet_type: PacketType, client_id: u16) void {
    std.mem.writeInt(u32, packet[0..][0..4], protocol_magic, .little);
    packet[4] = protocol_version;
    packet[5] = @intFromEnum(packet_type);
    std.mem.writeInt(u16, packet[6..][0..2], client_id, .little);
}

fn fixedText(bytes: []const u8) []const u8 {
    var count: usize = 0;
    while (count < bytes.len and bytes[count] != 0) {
        count += 1;
    }
    return bytes[0..count];
}

fn packetTypeName(packet_type: PacketType) []const u8 {
    return switch (packet_type) {
        .login_request => "login",
        .login_accepted => "login_accepted",
        .login_rejected => "login_rejected",
        .heartbeat => "heartbeat",
        .join_scene => "join_scene",
        .join_session => "join_session",
        .create_entity_request => "create",
        .create_entity_accepted => "create_accepted",
        .destroy_entity => "destroy",
        .command_dispatch => "command",
        .transform_correction => "transform",
        .active_action_start => "active_action_start",
        .active_action_end => "active_action_end",
        .network_event => "network_event",
        .network_variable_update => "network_variable",
        .server_event_register => "server_event_register",
        .snapshot => "snapshot",
        .server_state => "server_state",
        .initial_sync_complete => "initial_sync_complete",
        .disconnect => "disconnect",
    };
}

fn logRelayPacket(packet_type: PacketType, client_id: u16, payload: []const u8) void {
    if (payload.len < 4) {
        std.debug.print("[SceneMax MP] receive {s} client={d} bytes={d}\n", .{ packetTypeName(packet_type), client_id, payload.len });
        return;
    }
    const entity_id = std.mem.readInt(u32, payload[0..][0..4], .little);
    if (packet_type == .command_dispatch) {
        std.debug.print("[SceneMax MP] receive command client={d} entity={d} text=\"{s}\"\n", .{
            client_id,
            entity_id,
            payload[4..],
        });
    } else if (packet_type == .transform_correction) {
        return;
    } else {
        std.debug.print("[SceneMax MP] receive {s} client={d} entity={d} bytes={d}\n", .{
            packetTypeName(packet_type),
            client_id,
            entity_id,
            payload.len,
        });
    }
}
