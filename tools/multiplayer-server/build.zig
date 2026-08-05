const std = @import("std");

const ServerTarget = struct {
    step_name: []const u8,
    description: []const u8,
    output_path: []const u8,
    query: std.Target.Query,
};

const targets = [_]ServerTarget{
    .{
        .step_name = "windows",
        .description = "Build the Windows x64 multiplayer server",
        .output_path = "bin/windows-x64/scenemax-mp-server.exe",
        .query = .{ .cpu_arch = .x86_64, .os_tag = .windows },
    },
    .{
        .step_name = "linux",
        .description = "Build the Linux x64 multiplayer server",
        .output_path = "bin/linux-x64/scenemax-mp-server",
        .query = .{ .cpu_arch = .x86_64, .os_tag = .linux },
    },
    .{
        .step_name = "macos",
        .description = "Build the macOS x64 multiplayer server",
        .output_path = "bin/macos-x64/scenemax-mp-server",
        .query = .{ .cpu_arch = .x86_64, .os_tag = .macos },
    },
};

pub fn build(b: *std.Build) void {
    const optimize = b.option(
        std.builtin.OptimizeMode,
        "optimize",
        "Prioritize performance, safety, or binary size",
    ) orelse .ReleaseFast;
    const all_step = b.step("servers", "Build all multiplayer server executables");
    var previous_update_step: ?*std.Build.Step = null;

    inline for (targets) |server_target| {
        const exe = b.addExecutable(.{
            .name = "scenemax-mp-server",
            .root_module = b.createModule(.{
                .root_source_file = b.path("zig/scenemax_multiplayer_server.zig"),
                .target = b.resolveTargetQuery(server_target.query),
                .optimize = optimize,
            }),
        });

        const update = b.addUpdateSourceFiles();
        update.addCopyFileToSource(exe.getEmittedBin(), server_target.output_path);
        if (previous_update_step) |step| {
            update.step.dependOn(step);
        }
        previous_update_step = &update.step;

        const platform_step = b.step(server_target.step_name, server_target.description);
        platform_step.dependOn(&update.step);
        all_step.dependOn(&update.step);
    }

    b.getInstallStep().dependOn(all_step);
}
