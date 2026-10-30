# SceneMax Multiplayer Server

SceneMax expects one prebuilt multiplayer server executable per game platform in:

- `tools/multiplayer-server/bin/windows-x64/scenemax-mp-server.exe`
- `tools/multiplayer-server/bin/linux-x64/scenemax-mp-server`
- `tools/multiplayer-server/bin/macos-x64/scenemax-mp-server`

The Project Settings `Multiplayer` tab copies the selected executable and patches the embedded
`SCENEMAX_MP_CONFIG` block with the game name, project path, port, and password hash.

The server hosts multiple game sessions. Clients create or join a session, then join a scene
within that session. The scene id is the scene folder path (`main` for the root scene,
`game_level1` for a switched scene folder, and so on).

Installed Studio uses bundled platform templates. In a developer checkout with
source and Zig available, SceneMax builds `zig/scenemax_multiplayer_server.zig`
with `zig build-exe -O ReleaseFast`; a missing compiler/source uses the template.
Compiler errors are reported without silently selecting an older binary.

## Action ordering

Shader entity-data is sent before entity/action snapshots during login and joins.
An accepted `shader` update retires the older persistent material override in
slot 251. A later material assignment remains in that slot, applied after the
shader base on late join; an empty material name can therefore restore that base.
Bevy publishes shader and material assignments through one execution-order queue,
retaining their existing entity-data and SceneMax action-command packet formats.
Rebuild project servers to use this ordering correction. This does not introduce
reliable ordering for arbitrary UDP packet loss or reordering.

Each entity also retains its first nonempty shader value and the material command
active before that shader (bounded by the existing 256/192-byte fields). When
needed, a late join receives a SceneMax command preamble recreating that base
before the latest shader data. This preserves shader resets across later material
changes. A reset before any shader leaves the material override intact. Entity
replacement initializes fresh history; inactive entities never replay it. Shared
shader values are decoded from the existing typed JSON envelope before becoming
script literals. No new packet type or JVM dependency is introduced.

For local-only hosting, set `SCENEMAX_MP_BIND_ADDRESS=127.0.0.1` in the server
process environment. This restricts the UDP listener to this computer. Loopback
alone did not eliminate Windows network notifications for fixed-port test servers.
Native acceptance and Rust real-server fixtures additionally set
`SCENEMAX_MP_PORT=0`, letting the OS allocate the port, and discover the actual
port from the server startup log before connecting. This also avoids a port
reservation race. These local runs do not modify firewall rules or require admin.
Without the variable, the server continues to bind `0.0.0.0` for normal network
hosting. Invalid IPv4 overrides abort startup instead of falling back to all
interfaces. `SCENEMAX_MP_PORT` overrides the embedded port with a decimal u16;
invalid values abort startup. Unset preserves the embedded hosting port. The
startup log reports the actual bound port, including when zero was requested.
Rebuild the templates before running the updated fixtures.

Numbered actions use per-entity, per-slot 16-bit serial ordering (a forward
distance of less than 32768). Duplicate/stale starts neither reset the duration
clock nor get relayed. Ended/expired slots retain their watermark until entity
replacement; an end received before its start prevents that start from being
revived by delayed packets. Accepted end notifications are relayed to other clients
in the same session/scene, including ends received before a start or after server
expiry. Duplicate/stale owner ends do not enqueue new relay work; pending recipient deliveries may be retried. Ownership checks reject another
client attempting to end an entity's action. Stale ends leave newer actions intact. The v1 packet
layout is unchanged; ordinary commands and transforms remain unnumbered.

Accepted starts are relayed for every slot, including movement, turn and looping
animation. The Bevy runtime sends one numbered start for these actions, with no
duplicate unnumbered execution command. Use the updated projector and server
together: old servers store motion starts for snapshots but do not relay them.
Rebuild existing project servers through Project Settings to refresh them.

Run `zig test zig/scenemax_multiplayer_server.zig -O ReleaseSafe` for ordering
regressions, then `zig build servers --summary all` with Zig 0.16.x to refresh all
three platform templates. The Rust opt-in compatibility test under
`scenemax3d_nextgen/Engine/multiplayer/tests/zig_server.rs` verifies the same
behavior over UDP, including the resulting late-join snapshots.


## Bounded action-end recovery

Updated Bevy clients and these server templates support additive v1 packet type
28, `ACTION_END_RECEIPT`. Existing starts (22) and ends (23) retain their layouts.
The receipt body is exactly eight bytes: little-endian entity u32, slot u8,
reserved u8, and sequence u16. Server receipts have sender ID zero; recipient
receipts use that recipient's client ID. Receipts are transport-internal.

The sender retries an unconfirmed end at a minimum 250 ms interval for eight
seconds. The relay validates ownership and source address, then atomically admits
all current same-room/scene recipients before changing the action watermark and
acknowledging the owner. Each recipient has separate retry state and receipts;
owner confirmation does not mean every recipient has received the end. A duplicate
owner end only repeats confirmation, without resetting any deadline or rebuilding
recipient work. Stale ends cannot replace newer playback.

Each side bounds pending ends to 4,096 entries; relay entries are per recipient.
Pressure delays admission instead of evicting pending work. Retry service is
bounded to 128 sends per pass and ordered fairly. Recipient delivery retries run
on the existing server traffic loop (heartbeats/probes provide idle traffic), so
250 ms is a minimum interval, not a guaranteed scheduling deadline. Entries expire
after eight seconds and are invalidated by replacement, destruction, recipient
room/scene generation change, or disconnection. This is bounded recovery, not an
unlimited reliable transport. Arbitrarily delayed in-flight v1 packets still lack
transition epochs.

Rebuild existing project servers through Project Settings to use the updated
relay. Older servers can ignore type 28; the new client still sends the original
end packet and bounds its unacknowledged retries. Older clients may ignore
receipts and receive repeated end packets during the relay retry window. Full
loss recovery requires the updated Bevy client and server together; Java code is
not needed. `network.state.unconfirmedActionEnds` reports sender retry windows
that expired in the current connection/room (also expected with an old relay).

The real-server fault gate now drops the end on both hops and both receipts,
including an expired queued animation converted to an end and sequence wrap.
Native acceptance: `python Tests/widget_multiplayer.py --profile release
--automated --end-loss --hold-seconds 100` from `scenemax3d_nextgen`.
