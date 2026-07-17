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
const max_sessions = 256;
const snapshot_entity_size = 228;

const PacketType = enum(u8) {
    login_request = 1,
    login_accepted = 2,
    login_rejected = 3,
    heartbeat = 4,
    join_scene = 5,
    create_entity_request = 10,
    create_entity_accepted = 11,
    destroy_entity = 12,
    command_dispatch = 20,
    transform_correction = 21,
    snapshot = 30,
    disconnect = 40,
};

const ServerConfig = struct {
    port: u16 = 9001,
    game_name: [128]u8 = [_]u8{0} ** 128,
    project_path: [256]u8 = [_]u8{0} ** 256,
    password_hash: [32]u8 = [_]u8{0} ** 32,
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
    owner_client: u16 = 0,
    session_id: u32 = 0,
    scene_id: [128]u8 = [_]u8{0} ** 128,
    archetype: [64]u8 = [_]u8{0} ** 64,
    player_name: [64]u8 = [_]u8{0} ** 64,
    position: [3]f32 = .{ 0, 0, 0 },
    rotation: [4]f32 = .{ 0, 0, 0, 1 },
    animation_index: u16 = 0,
    animation: [64]u8 = [_]u8{0} ** 64,
};

export var scenemax_mp_config_block: [config_begin.len + config_payload_size + config_end.len]u8 =
    initConfigBlock();

pub fn main(init: std.process.Init) !void {
    const io = init.io;
    const config = readConfig();
    var bind_addr = try net.IpAddress.parseIp4("0.0.0.0", config.port);
    const sock = try bind_addr.bind(io, .{ .mode = .dgram, .protocol = .udp });
    defer sock.close(io);

    var clients = [_]Client{.{}} ** max_clients;
    var sessions = [_]Session{.{}} ** max_sessions;
    var entities = [_]Entity{.{}} ** max_entities;
    var next_client_id: u16 = 1;
    var next_session_id: u32 = 1000;
    var next_entity_id: u32 = 1;
    var tick: i64 = 0;
    var buffer: [max_packet_size]u8 = undefined;

    while (true) {
        tick += 1;
        const message = try sock.receive(io, &buffer);
        const received = message.data.len;
        if (received < 8) continue;

        const packet_magic = std.mem.readInt(u32, buffer[0..][0..4], .little);
        if (packet_magic != protocol_magic or buffer[4] != protocol_version) continue;
        const packet_type = decodePacketType(buffer[5]) orelse continue;
        const client_id = std.mem.readInt(u16, buffer[6..][0..2], .little);
        const payload = buffer[8..received];
        const now = tick;

        switch (packet_type) {
            .login_request => {
                const password_ok = verifyPassword(payload, config.password_hash);
                if (!password_ok) {
                    try sendReject(sock, io, message.from);
                    continue;
                }
                const login = decodeLoginRequest(payload) orelse continue;
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
                try sendSnapshot(sock, io, message.from, &entities, session.id, login.scene_id);
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
                    zero(&client.scene_id);
                    copyFixed(&client.scene_id, payload, 0);
                    clearClientEntitiesInOtherScenes(&entities, client.id, client.session_id, client.scene_id);
                    try sendSnapshot(sock, io, message.from, &entities, client.session_id, client.scene_id);
                }
            },
            .create_entity_request => {
                const client = findClient(&clients, client_id) orelse continue;
                const entity_id = next_entity_id;
                next_entity_id +%= 1;
                if (next_entity_id == 0) next_entity_id = 1;
                const slot = entity_id % max_entities;
                entities[slot] = decodeEntityCreate(payload, entity_id, client.*);
                try sendEntityAccepted(sock, io, message.from, client_id, entity_id);
                try dispatchEntityCreated(sock, io, &clients, client_id, entity_id, payload);
            },
            .command_dispatch, .transform_correction => {
                if (findClient(&clients, client_id) == null) continue;
                if (packet_type == .transform_correction) {
                    updateEntityTransform(&entities, payload);
                }
                try dispatch(sock, io, &clients, client_id, packet_type, client_id, payload);
            },
            .disconnect => {
                if (findClient(&clients, client_id)) |client| {
                    try destroyClientEntities(sock, io, &clients, &entities, client.id, client.session_id, client.scene_id);
                    client.active = false;
                }
            },
            else => {},
        }

        expireClients(&clients, now);
    }
}

fn initConfigBlock() [config_begin.len + config_payload_size + config_end.len]u8 {
    var block = [_]u8{0} ** (config_begin.len + config_payload_size + config_end.len);
    @memcpy(block[0..config_begin.len], config_begin);
    @memcpy(block[config_begin.len + config_payload_size ..], config_end);
    return block;
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

const LoginRequest = struct {
    create_session: bool = false,
    requested_session_id: u32 = 0,
    session_name: [64]u8 = [_]u8{0} ** 64,
    scene_id: [128]u8 = [_]u8{0} ** 128,
    player_name: [64]u8 = [_]u8{0} ** 64,
};

fn decodeLoginRequest(payload: []const u8) ?LoginRequest {
    if (payload.len < 293) return null;
    var login = LoginRequest{};
    login.create_session = payload[32] != 0;
    login.requested_session_id = std.mem.readInt(u32, payload[33..][0..4], .little);
    copyFixed(&login.session_name, payload, 37);
    copyFixed(&login.scene_id, payload, 101);
    copyFixed(&login.player_name, payload, 229);
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

fn findSession(sessions: *[max_sessions]Session, session_id: u32) ?*Session {
    for (sessions) |*session| {
        if (session.active and session.id == session_id) return session;
    }
    return null;
}

fn findClient(clients: *[max_clients]Client, client_id: u16) ?*Client {
    for (clients) |*client| {
        if (client.active and client.id == client_id) return client;
    }
    return null;
}

fn expireClients(clients: *[max_clients]Client, now: i64) void {
    for (clients) |*client| {
        if (client.active and now - client.last_seen_ms > 15000) {
            client.active = false;
        }
    }
}

fn decodeEntityCreate(payload: []const u8, entity_id: u32, client: Client) Entity {
    var entity = Entity{
        .active = true,
        .network_id = entity_id,
        .owner_client = client.id,
        .session_id = client.session_id,
        .scene_id = client.scene_id,
    };
    copyFixed(&entity.archetype, payload, 0);
    copyFixed(&entity.player_name, payload, 64);
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
        10 => .create_entity_request,
        11 => .create_entity_accepted,
        12 => .destroy_entity,
        20 => .command_dispatch,
        21 => .transform_correction,
        30 => .snapshot,
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

fn clearClientEntitiesInOtherScenes(entities: *[max_entities]Entity, client_id: u16, session_id: u32, scene_id: [128]u8) void {
    for (entities) |*entity| {
        if (entity.active and entity.owner_client == client_id and entity.session_id == session_id and !sameScene(entity.scene_id, scene_id)) {
            entity.active = false;
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

fn readF32(bytes: []const u8) f32 {
    return @bitCast(std.mem.readInt(u32, bytes[0..][0..4], .little));
}

fn sendReject(sock: net.Socket, io: std.Io, address: net.IpAddress) !void {
    var packet: [8]u8 = undefined;
    writeHeader(packet[0..], .login_rejected, 0);
    try sock.send(io, &address, &packet);
}

fn sendLoginAccepted(sock: net.Socket, io: std.Io, address: net.IpAddress, client_id: u16, session_id: u32, session_name: [64]u8) !void {
    var packet: [78]u8 = undefined;
    writeHeader(packet[0..], .login_accepted, client_id);
    std.mem.writeInt(u16, packet[8..][0..2], client_id, .little);
    std.mem.writeInt(u32, packet[10..][0..4], session_id, .little);
    @memcpy(packet[14..78], &session_name);
    try sock.send(io, &address, &packet);
}

fn sendEntityAccepted(sock: net.Socket, io: std.Io, address: net.IpAddress, client_id: u16, entity_id: u32) !void {
    var packet: [12]u8 = undefined;
    writeHeader(packet[0..], .create_entity_accepted, client_id);
    std.mem.writeInt(u32, packet[8..][0..4], entity_id, .little);
    try sock.send(io, &address, &packet);
}

fn sendSnapshot(sock: net.Socket, io: std.Io, address: net.IpAddress, entities: *[max_entities]Entity, session_id: u32, scene_id: [128]u8) !void {
    var packet: [max_packet_size]u8 = undefined;
    writeHeader(packet[0..], .snapshot, 0);
    var cursor: usize = 10;
    var count: u16 = 0;
    for (entities) |entity| {
        if (!entity.active) continue;
        if (entity.session_id != session_id or !sameScene(entity.scene_id, scene_id)) continue;
        if (cursor + snapshot_entity_size > packet.len) continue;
        std.mem.writeInt(u32, packet[cursor..][0..4], entity.network_id, .little);
        cursor += 4;
        std.mem.writeInt(u16, packet[cursor..][0..2], entity.owner_client, .little);
        cursor += 2;
        @memcpy(packet[cursor .. cursor + 64], &entity.archetype);
        cursor += 64;
        @memcpy(packet[cursor .. cursor + 64], &entity.player_name);
        cursor += 64;
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
        count += 1;
    }
    std.mem.writeInt(u16, packet[8..][0..2], count, .little);
    try sock.send(io, &address, packet[0..cursor]);
}

fn dispatchEntityCreated(sock: net.Socket, io: std.Io, clients: *[max_clients]Client, sender_client_id: u16, entity_id: u32, payload: []const u8) !void {
    var packet: [max_packet_size]u8 = undefined;
    writeHeader(packet[0..], .create_entity_accepted, sender_client_id);
    std.mem.writeInt(u32, packet[8..][0..4], entity_id, .little);
    const payload_len = @min(payload.len, packet.len - 12);
    @memcpy(packet[12 .. 12 + payload_len], payload[0..payload_len]);
    const sender = findClient(clients, sender_client_id) orelse return;
    for (clients) |client| {
        if (!client.active or client.id == sender_client_id) continue;
        if (client.session_id != sender.session_id or !sameScene(client.scene_id, sender.scene_id)) continue;
        try sock.send(io, &client.address, packet[0 .. 12 + payload_len]);
    }
}

fn dispatchEntityDestroyed(sock: net.Socket, io: std.Io, clients: *[max_clients]Client, sender_client_id: u16, session_id: u32, scene_id: [128]u8, entity_id: u32) !void {
    var packet: [12]u8 = undefined;
    writeHeader(packet[0..], .destroy_entity, sender_client_id);
    std.mem.writeInt(u32, packet[8..][0..4], entity_id, .little);
    for (clients) |client| {
        if (!client.active or client.id == sender_client_id) continue;
        if (client.session_id != session_id or !sameScene(client.scene_id, scene_id)) continue;
        try sock.send(io, &client.address, packet[0..]);
    }
}

fn dispatch(sock: net.Socket, io: std.Io, clients: *[max_clients]Client, sender_client_id: u16, packet_type: PacketType, client_id: u16, payload: []const u8) !void {
    var packet: [max_packet_size]u8 = undefined;
    writeHeader(packet[0..], packet_type, client_id);
    const payload_len = @min(payload.len, packet.len - 8);
    @memcpy(packet[8 .. 8 + payload_len], payload[0..payload_len]);
    const sender = findClient(clients, sender_client_id) orelse return;
    for (clients) |client| {
        if (!client.active or client.id == sender_client_id) continue;
        if (client.session_id != sender.session_id or !sameScene(client.scene_id, sender.scene_id)) continue;
        try sock.send(io, &client.address, packet[0 .. 8 + payload_len]);
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
