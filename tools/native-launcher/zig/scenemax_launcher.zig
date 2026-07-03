const std = @import("std");
const builtin = @import("builtin");

const footer_magic = "SCENEMAX_PAYLOAD";
const footer_size: usize = 8 + 32 + footer_magic.len;
const payload_magic = "SMXPKG1";

const EntryType = enum(u8) {
    file = 0,
    directory = 1,
};

pub fn main(init: std.process.Init) !void {
    const allocator = init.gpa;
    const io = init.io;

    const self_path = try std.process.executablePathAlloc(io, allocator);
    defer allocator.free(self_path);

    var exe = try std.Io.Dir.openFileAbsolute(io, self_path, .{});
    defer exe.close(io);

    const exe_size = try exe.length(io);
    if (exe_size < footer_size) {
        return error.MissingSceneMaxPayload;
    }

    var footer: [footer_size]u8 = undefined;
    try readExactAt(exe, io, exe_size - @as(u64, footer_size), &footer);

    if (!std.mem.eql(u8, footer[40..], footer_magic)) {
        return error.InvalidSceneMaxPayloadFooter;
    }

    const payload_len = std.mem.readInt(u64, footer[0..8], .little);
    if (payload_len == 0 or payload_len > exe_size - footer_size) {
        return error.InvalidSceneMaxPayloadLength;
    }
    const payload_offset = exe_size - @as(u64, footer_size) - payload_len;
    const payload_hash = footer[8..40];

    const install_dir = try resolveInstallDir(allocator, self_path, payload_hash);
    defer allocator.free(install_dir);

    const marker_path = try std.fs.path.join(allocator, &.{ install_dir, ".scenemax-payload.sha256" });
    defer allocator.free(marker_path);

    var needs_extract = true;
    if (std.Io.Dir.openFileAbsolute(io, marker_path, .{})) |marker_file| {
        var marker = marker_file;
        defer marker.close(io);
        var marker_buf: [64]u8 = undefined;
        const read = try marker.readPositionalAll(io, &marker_buf, 0);
        var expected: [64]u8 = undefined;
        writeHex(payload_hash, &expected);
        needs_extract = read != expected.len or !std.mem.eql(u8, marker_buf[0..read], &expected);
    } else |_| {}

    if (needs_extract) {
        std.Io.Dir.cwd().deleteTree(io, install_dir) catch {};
        try std.Io.Dir.cwd().createDirPath(io, install_dir);
        try extractPayload(allocator, io, &exe, payload_offset, payload_len, install_dir);

        var marker_file = try std.Io.Dir.createFileAbsolute(io, marker_path, .{ .truncate = true });
        defer marker_file.close(io);
        var write_buffer: [1024]u8 = undefined;
        var marker_writer = marker_file.writer(io, &write_buffer);
        var hash_hex: [64]u8 = undefined;
        writeHex(payload_hash, &hash_hex);
        try marker_writer.interface.writeAll(&hash_hex);
        try marker_writer.end();
    }

    const bundled_java_rel = if (builtin.os.tag == .windows)
        &.{ "runtime", "bin", "javaw.exe" }
    else
        &.{ "runtime", "bin", "java" };
    const bundled_java_path = try std.fs.path.join(allocator, &.{ install_dir, bundled_java_rel[0], bundled_java_rel[1], bundled_java_rel[2] });
    defer allocator.free(bundled_java_path);

    const jar_path = try std.fs.path.join(allocator, &.{ install_dir, "scenemax3d_scene.jar" });
    defer allocator.free(jar_path);

    const java_command = if (builtin.os.tag == .windows) "javaw.exe" else "java";
    const has_bundled_runtime = fileExists(io, bundled_java_path);
    var child = if (has_bundled_runtime)
        try std.process.spawn(io, .{
            .argv = &.{ bundled_java_path, "-XX:MaxDirectMemorySize=1024m", "-jar", jar_path },
            .cwd = .{ .path = install_dir },
            .stdin = .inherit,
            .stdout = .inherit,
            .stderr = .inherit,
        })
    else
        try std.process.spawn(io, .{
            .argv = &.{ java_command, "-XX:MaxDirectMemorySize=1024m", "-jar", jar_path },
            .cwd = .{ .path = install_dir },
            .stdin = .inherit,
            .stdout = .inherit,
            .stderr = .inherit,
        });
    const term = try child.wait(io);
    switch (term) {
        .exited => |code| std.process.exit(code),
        else => std.process.exit(1),
    }
}

fn fileExists(io: std.Io, path: []const u8) bool {
    var file = std.Io.Dir.openFileAbsolute(io, path, .{}) catch return false;
    file.close(io);
    return true;
}

fn extractPayload(
    allocator: std.mem.Allocator,
    io: std.Io,
    exe: *std.Io.File,
    payload_offset: u64,
    payload_len: u64,
    install_dir: []const u8,
) !void {
    _ = payload_len;
    var cursor = payload_offset;

    var magic: [payload_magic.len]u8 = undefined;
    try readExactAt(exe.*, io, cursor, &magic);
    cursor += magic.len;
    if (!std.mem.eql(u8, &magic, payload_magic)) {
        return error.InvalidSceneMaxPayloadHeader;
    }

    const entry_count = try readU32At(exe.*, io, &cursor);
    var i: u32 = 0;
    while (i < entry_count) : (i += 1) {
        var small: [2]u8 = undefined;
        try readExactAt(exe.*, io, cursor, &small);
        cursor += small.len;
        const entry_type_raw = small[0];
        const executable = small[1] != 0;
        const path_len = try readU32At(exe.*, io, &cursor);
        const data_len = try readU64At(exe.*, io, &cursor);

        if (path_len == 0 or path_len > 32768) {
            return error.InvalidSceneMaxPayloadPath;
        }

        const rel_path = try allocator.alloc(u8, path_len);
        defer allocator.free(rel_path);
        try readExactAt(exe.*, io, cursor, rel_path);
        cursor += path_len;
        validateRelativePath(rel_path) catch return error.InvalidSceneMaxPayloadPath;

        const target_path = try std.fs.path.join(allocator, &.{ install_dir, rel_path });
        defer allocator.free(target_path);

        const parent_path = std.fs.path.dirname(target_path);
        if (parent_path) |parent| {
            try std.Io.Dir.cwd().createDirPath(io, parent);
        }

        const entry_type: EntryType = switch (entry_type_raw) {
            0 => .file,
            1 => .directory,
            else => return error.InvalidSceneMaxPayloadEntry,
        };
        switch (entry_type) {
            .directory => {
                if (data_len != 0) {
                    return error.InvalidSceneMaxPayloadEntry;
                }
                try std.Io.Dir.cwd().createDirPath(io, target_path);
            },
            .file => {
                var out_file = try std.Io.Dir.createFileAbsolute(io, target_path, .{ .truncate = true });
                defer out_file.close(io);
                try copyExactFrom(exe.*, io, &cursor, out_file, data_len);
                if (executable and builtin.os.tag != .windows) {
                    try out_file.setPermissions(io, .executable_file);
                }
            },
        }
    }
}

fn readExactAt(file: std.Io.File, io: std.Io, offset: u64, buffer: []u8) !void {
    const read = try file.readPositionalAll(io, buffer, offset);
    if (read != buffer.len) {
        return error.UnexpectedEndOfSceneMaxPayload;
    }
}

fn readU32At(file: std.Io.File, io: std.Io, cursor: *u64) !u32 {
    var buffer: [4]u8 = undefined;
    try readExactAt(file, io, cursor.*, &buffer);
    cursor.* += buffer.len;
    return std.mem.readInt(u32, &buffer, .little);
}

fn readU64At(file: std.Io.File, io: std.Io, cursor: *u64) !u64 {
    var buffer: [8]u8 = undefined;
    try readExactAt(file, io, cursor.*, &buffer);
    cursor.* += buffer.len;
    return std.mem.readInt(u64, &buffer, .little);
}

fn copyExactFrom(source: std.Io.File, io: std.Io, cursor: *u64, dest: std.Io.File, len: u64) !void {
    var remaining = len;
    var read_buffer: [64 * 1024]u8 = undefined;
    var write_buffer: [64 * 1024]u8 = undefined;
    var writer = dest.writer(io, &write_buffer);
    while (remaining > 0) {
        const chunk_len: usize = @intCast(@min(remaining, read_buffer.len));
        try readExactAt(source, io, cursor.*, read_buffer[0..chunk_len]);
        try writer.interface.writeAll(read_buffer[0..chunk_len]);
        cursor.* += chunk_len;
        remaining -= chunk_len;
    }
    try writer.end();
}

fn validateRelativePath(path: []const u8) !void {
    if (std.fs.path.isAbsolute(path)) {
        return error.AbsolutePayloadPath;
    }
    var parts = std.mem.tokenizeAny(u8, path, "/\\");
    while (parts.next()) |part| {
        if (part.len == 0 or std.mem.eql(u8, part, ".") or std.mem.eql(u8, part, "..")) {
            return error.UnsafePayloadPath;
        }
    }
}

fn resolveInstallDir(allocator: std.mem.Allocator, self_path: []const u8, hash: []const u8) ![]u8 {
    var hash_hex: [64]u8 = undefined;
    writeHex(hash, &hash_hex);
    const hash_folder = hash_hex[0..16];

    if (std.fs.path.dirname(self_path)) |exe_dir| {
        return try std.fs.path.join(allocator, &.{ exe_dir, ".scenemax-runtime-cache", hash_folder });
    }
    return try std.fs.path.join(allocator, &.{ ".scenemax-runtime-cache", hash_folder });
}

fn writeHex(bytes: []const u8, out: []u8) void {
    const alphabet = "0123456789abcdef";
    for (bytes, 0..) |byte, i| {
        out[i * 2] = alphabet[byte >> 4];
        out[i * 2 + 1] = alphabet[byte & 0x0f];
    }
}
