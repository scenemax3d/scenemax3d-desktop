> **Historical development record.** This document records implementation stages and test results, including superseded gaps and former Java/Swing comparisons. It is not current setup guidance or a release feature checklist. Use the [current IDE guide](README.md) and [multiplayer guide](../../docs/multiplayer.md) for supported workflows. Earlier source references may exist only in release history.

# Native multiplayer migration status

See the [feature parity audit](../docs/MULTIPLAYER_FEATURE_PARITY.md) for the full
source-based inventory, verification limits and recommended implementation order.

This is an independent Bevy implementation, **not yet complete multiplayer support**.
The prior implementation is a behavioral reference only; it is not a runtime or
build dependency. The dedicated server lives in
`tools/multiplayer-server/zig/scenemax_multiplayer_server.zig`.

## Configuration

File → Projects → Project Settings opens a retained multiplayer form. It reads
Java catalog defaults for the selected project, then saves native settings in
`.scenemax-studio/multiplayer.json`. Unknown JSON fields survive. Saves compare
the loaded bytes and reject external changes. The Java catalog is not rewritten.

Settings contain server hostname/IP, UDP port, project GUID, session ID/name and
Windows/Linux/macOS x64 server platform. All clients and the server must use the
same project GUID and room. The default port is 9001 and room is 1000.

Build Server compiles the current Zig source for the selected platform into a
private temporary directory, then patches a uniquely named executable under
`multiplayer_server`. It uses the platform template only when Zig or source is
unavailable; compilation errors are reported without silently using old code.
Install Zig 0.16.x on PATH or set `SCENEMAX_ZIG` to its executable. Builds run in
the settings worker, with a five-minute timeout, 4 MiB compiler-output limit and
bounded failure diagnostics. Cancel, closing the form, changing projects or
exiting the IDE cancels owned compiler work. Existing server files/processes
are preserved. Open Folder reveals the most recently built server's directory
through the existing filesystem worker.

The masked Password field holds a credential only for the current IDE session.
Save applies it to future runs after settings save succeeds; Build Server installs
its SHA-256 hash and applies the same client credential after a successful build.
No password explicitly selects an empty credential. Use environment password
restores `SCENEMAX_MULTIPLAYER_PASSWORD` fallback after Save/Build. Plaintext never
enters the settings JSON, packaged script, process arguments or renderable UI text.
Changing projects clears the session credential; restarting the IDE requires it
again. The native field supports Unicode keyboard text/paste, keyboard selection,
Home/End, arrows and deletion (1024 UTF-8 bytes maximum); clipboard copy/cut, undo
and IME preedit are intentionally not provided. Existing standalone environment
password support remains available. An explicit empty environment password now
also overrides older password metadata in a script.


IDE Run also imports connection defaults directly from the matching Java catalog
entry when native settings do not exist. It uses the IDE's explicit catalog or
discovers an ancestor catalog, then the installation catalog. Preparation runs
off the UI thread; Stop cancels a pending launch. It never rewrites the catalog,
source script or native settings, and never imports a catalog password. Empty
server entries stay offline. Imported values travel only in the child process's
`SCENEMAX_MULTIPLAYER_DEFAULTS`; inherited defaults are cleared for every Run.
Native settings, script metadata and explicit environment overrides take precedence
in that order. Standalone projector launches do not discover Java catalogs.

The projector reads native settings at startup. Explicit
`SCENEMAX_MULTIPLAYER_SERVER`, `_PORT`, `_PASSWORD`, `_PROJECT_GUID`, `_SESSION_ID`,
`_SESSION_NAME`, `_CREATE_SESSION`, `_PLAYER` and `_SCENE` environment values
override settings and packaged metadata. With no server configuration it opens
no socket. Package & Deploy embeds non-secret settings in the staged entry script
using Java-compatible `//$[...]` metadata, without modifying source scripts.

Session-join continuations now wait for `network.joinPending` to become zero,
independently of `network.ready`. Offline, invalid, unresolved, closed/rejected and
failed requests release the continuation without erasing remote state for a join
that never started. `network.joinError` contains the failure reason (empty after
successful joining). Valid requests wait for readiness in the requested ID/name;
a numeric zero waits for a new session ID. Names are trimmed. A sent request that
does not complete within ten seconds releases with `session join timed out`.
Initial connection has its separate transport login timeout. A timeout does not
cancel an already sent UDP request; late responses and concurrent scripted joins
still need wider acceptance. These fields represent one current join attempt per
client, not independent promises for concurrent script scopes.

## Implemented boundaries

- `Engine/multiplayer`: independent typed protocol and bounded nonblocking UDP
  client; no Bevy, project files, game rules or IDE dependency.
- `Language/parser/network.rs`: declarations, handlers, broadcasts, sends,
  session joins and `entity.data.field# = value` syntax. Entity options retain
  the multiplayer flag and original spawn declaration.
- `Engine/runtime/multiplayer/`: registration, ownership, existing runtime
  action-queue replay, transforms, scene/session cleanup, event/variable binding
  and evaluated command emission.
- IDE core owns settings/validation; services owns background storage/server
  installation; application owns captured project identity and orchestration;
  presentation owns retained native controls.

The protocol implements all message types emitted by the current Zig server:
login/rejection, ready, create acknowledgements, remote creation/destruction,
commands, transforms, action start/end, snapshots, session pages, events,
variables and entity data. Outbound operations include scene/session join and
server timer registration. Fixed strings, packet sizes, UTF-8, counts, transform
numbers and shared-value JSON are validated. Overlong source/values fail instead
of being silently truncated. Create retries retain their request ID; other
traffic retains the existing protocol's UDP delivery limitations.

Current runtime bindings cover models/primitives/colliders/effects represented
by existing model declarations; directional movement/turns; explicit position
and move-to; model animation requests; character-mode commands; attach; IK;
effect play; shader data and native material commands. Referenced entities wait
for network IDs/readiness. Finite movement/turn snapshots use the remaining
distance/time; persistent structural actions replay through existing systems.
Remote declarations are stripped of their multiplayer flag to prevent echo.

Network handlers and server intervals use the normal action queue. Shared values
retain the Java JSON wire representation (null, boolean, number, string, nested
array). Incoming values now enter the shared script VM. It supports string/array
assignment, concatenation, equality, nested array reads and indexed updates,
length, local scope shadowing, typed function arguments, logging and UI text.
Indexed network-array changes send the updated whole array through the existing
protocol. Timer intervals can resolve numeric script expressions.

`network.state` is a script object containing `ready`, `sessionId`, `sessionName`
and `sessions`; each session has `id`, `name`, `clients` and `connectedClients`.
Both `network.state.sessions[0].name` and Java's
`json(network.state).sessions[0].name` expression forms are supported. The state
is refreshed from received server pages and the current session. An offline
runtime publishes an empty session list with readiness false.

Java collision selectors such as `when local collides with #authored do` bind
to remote entities whose original script name matches `authored` (case-insensitive).
Each remote entity has independent collision-entry/controller state. Inside the
handler, `network.send "contact"` sends to that entity's owner; the target follows
nested function and delayed-action scopes. Outside such a scope, implicit sends
do nothing, as in Java. Explicit `entity.network.send(...)` remains available.
Quoted joint names are preserved and resolved through the existing bone alias
system; missing joints never fall back to an entity origin. Joint/weapon physics
behavior still needs Bevy multiplayer acceptance with real animated assets.

`entity.network.id` exposes the current acknowledged ID (zero before local
acknowledgement). Destroyed IDs are removed. Typed values retain full u32 IDs;
the engine's movement/physics APIs still convert numeric arguments to f32.

Java array commands are supported: `items.push(value)`, `items.pop`,
`items.clear` and `items.reset(value)`. Reset fills existing slots without
resizing; pop on an empty array is harmless. Existing scalar variables become
arrays, while undeclared targets remain unchanged. Operands retain typed/nested
values and local scope shadowing. Declared network arrays synchronize their
whole executed value; several edits within one command flush may publish the
same final array. Incoming updates do not replay the mutation. Arrays still
copy on assignment rather than sharing Java references.

Snapshot replay now tracks each entity/action slot and its 16-bit sequence.
Split records merge distinct slots; duplicate/older numbered actions are ignored,
including wraparound within the usual half-range ordering window. Superseded or
ended actions still waiting for entity/reference resolution are discarded before
execution. Only the first snapshot pose is applied, and a live pose received
first prevents a delayed snapshot from overwriting it. Recently destroyed IDs
cannot be recreated by delayed snapshot/create packets. Tracking is bounded to
2,048 entity histories and 4,096 destruction tombstones, and resets on scene or
session changes.

Protocol v1 has no packet generation for unnumbered commands/transforms or scene
transitions. This does not establish general UDP ordering or reliability.
Tombstones older than the bounded destruction history are evicted.

The dedicated Zig server now retains each entity's per-slot sequence watermark
after end/expiry, rejects duplicate or older starts without restarting their
duration or relaying them, and accepts 16-bit wraparound. End-before-start is
remembered; stale ends cannot cancel newer state. Action-slot capacity failures
do not consume sequence numbers. Bevy now allocates action sequences per entity,
as Java does, and unnumbered commands do not advance them. Existing packet
layouts are unchanged. Updated Windows/Linux/macOS server templates are included;
Linux/macOS builds still require native execution acceptance.

The server now forwards newly accepted action ends to peers in the same
session/scene. A bounded per-slot ended flag suppresses duplicate ends without
losing the first end received after expiry; sequence ordering rejects stale ends
and entity ownership is checked before relay. Bevy's existing receiver uses these
notifications to reject pending/snapshot replay. Rebuild existing project servers
to get the updated relay behavior.

Timed local move, move-to and turn controllers now retain a shared lifetime token,
independent of script continuation ownership. Character timed intent shares it.
Once every controller part finishes or is removed, Bevy enqueues a matching end
for the current entity/slot/sequence. Superseded lifetimes cannot end newer work;
destruction and scene/session changes clear tracking. Failed end enqueues remain
pending for retry; successful enqueues are not repeated (UDP delivery is still
unacknowledged). Controllers already finished while waiting in the runtime's
outgoing dependency queue are discarded instead of starting late. Continuous
per-frame input has no retained timed controller and remains outside this end
mechanism.

Model animations now share this lifetime tracking. A queued request
retains the token while assets load, then transfers it into current playback.
Stopping/removing the playback or replacing it with an untracked clip releases
the old lifetime and emits its end. Repeated requests for the same local loop
remain suppressed rather than creating new numbered starts. Numbered remote
requests retain ownership through ordinary and baked/retargeted playback.
Matching ends remove pending requests and stop only the recorded playback graphs;
newer graphs and unrelated local playback survive. Transition bookkeeping is
cleared for the stopped graph, and the existing visual-offset restoration path
handles the removed current animation.

Non-loop clips now also use numbered starts and matching completion/cancellation
ends. Animation starts wait for the matching loaded playback and an acknowledged
entity ID. Finite duration reflects the clip's remaining time at its playback
speed; looping clips retain the indefinite sentinel. Late-join non-loop playback
seeks to the position implied by remaining server time and the local loaded clip.
Receiver dependency, dispatch and asset-loading time is deducted; expiry while
loading discards the request without playing it. Ordinary, sliced and baked
playback set both Bevy's clip seek time and script animation progress consistently.
This assumes matching project assets and constant playback speed. Unknown network
transit time, subsequent speed changes and looping-animation phase are not synchronized.

Finite snapshot actions now count time spent waiting in the receiver's pending
queue for their entity or referenced entities. Retries preserve the original
receive timestamp; expired actions are discarded and retain an ended sequence
watermark so delayed duplicates cannot restart them. Remaining move/turn/move-to
duration uses the reduced time. Indefinite state and unnumbered commands keep
their existing behavior. This accounts for local queue time, not unknown network
transit time; animation seeking additionally accounts for asset-loading time.
Pose correction still uses the existing authoritative transform stream.

Numbered replay commands now carry entity/slot/sequence ownership through the
normal delayed-action queue into timed move, move-to and turn controllers.
An end, superseding sequence or invalidated replay history cancels only matching
remote-owned controllers/continuations. Timed character motion carries the same
ownership; cancellation preserves separate local input and jump state. Moves are
removed without snapping to their old final destination. Stale ends cannot stop a
newer sequence, and unrelated local movement survives. Cancellation takes effect
when the network update processes the end; it does not undo prior frame movement.
Live move, move-to, turn and looping-animation starts now use one numbered
ActionStart message instead of a separate unnumbered execution copy. The Zig
server relays accepted starts for all slots. Live starts and snapshots share
the same replay identity, so duplicate starts and late snapshots cannot execute
an action twice. Finite live actions count dependency-wait time and expire while
waiting, just like snapshots. Live movement/turn controllers can therefore be
cancelled by matching ends, including ends emitted by local timed controllers.
Numbered model animations also support cancellation.

Effect play commands now use numbered slot 4 starts. Runtime-owned metadata binds
the action identity and shared lifetime to the exact Effekseer playback generation,
without adding multiplayer dependencies to the shared renderer. Starts wait for
the matching playback and entity acknowledgement. Loops are indefinite; finite
starts use the existing effect duration metadata and playback speed. Completion
or removal releases the lifetime and sends an end. Supersession and matching ends
remove only owned playback; a stale generation cannot stop a newer effect. Playback
removal feeds the renderer's existing native stop/handle cleanup path.

Finite effect duration uses the greater of project frame-range metadata (or
its two-second fallback) and the compiled effect's finite native lifetime bound,
including one simulation frame of margin. The native build inspects compiled bytes
without a graphics device at declaration time, before sending any action starts.
Local expiry, transmitted duration and late-join seeking therefore share the same
extended bound. Unavailable native support, invalid files and unbounded native
terms retain the existing metadata fallback. Native renderer completion can now
finish playback sooner and release its numbered action lifetime. Feedback is keyed
by instance and playback generation; loops, previews and stale generations cannot
end another playback. Missing/failed native playback produces no completion
signal, so duration-based cleanup remains available. Finite late-join
playback now reconstructs elapsed time from the received remaining duration,
constant playback speed and local queue wait. Only matching action ownership and
playback generation consume that resume position; expired playback is removed
before render extraction. Native finite playback seeks once when its handle is
created, without changing the shared renderer clock or restarting existing handles.
Native seeking is capped at 36,000 simulation frames (ten minutes) to bound work.
Loop phase, random particle seed synchronization, unbounded native terms and
duration changes driven by dynamic inputs remain unfinished; matching effect assets/duration metadata are assumed.

This requires the updated Bevy projector and Zig server together. Rebuild existing
project servers through Project Settings; older servers store motion starts but
do not relay them. No packet layout changed. Untracked operations still use the
ordinary command message. Numbered motion has one current action per entity/slot;
overlapping local movement fidelity still needs review. Network transit time is
not subtracted from live action durations; movement still lacks sender-side
registration-delay compensation.

## Outstanding before claiming full parity

The 2026-10-05 follow-up adds a native bridge for multiplayer-marked widget text,
show/hide, visible/frame properties and easing. Saved flags reach the runtime;
unmarked widgets stay local. Separate typed state keys support late join and
delayed UI loading without replay echoes or network script execution. Other UI
properties still require local implementation. Easing replay now counts time
spent waiting on receiver UI loading and the action queue, preserving the original
curve across widget reloads. Already-expired replay applies the final pose directly.
The wire value has no server age: time before receipt, including late-join delay,
is still not recovered. Message effects stay local while final text is shared. Oversized
updates remain local with a warning under the existing 255-byte encoded-value
limit. Real Zig transport and headless Bevy UI tests pass; two-window graphical
acceptance, general packet ordering and reliable delivery remain open.

The audit's session-join continuation gap has also been fixed and tested; see
the parity report's follow-ups for evidence and remaining acceptance cases.

The 2026-10-06 continuation adds text/background colors and font size, including
widget-specific color aliases, to both local UI application and independent
multiplayer snapshot slots. Ordinary and bitmap text retain updated styles across
later text/animation changes. Invalid colors and non-finite/out-of-range font sizes
are rejected. Font asset changes, image/sprite replacement, shaders and richer
list/edit control properties still need native implementation. Use the maintained
`Tests/widget_multiplayer.py` fixture for two-window delayed-load/late-join/live
acceptance; it keeps project data isolated and owns its child-process cleanup.

1. Finish Java reference/alias semantics and complete dynamic
   coercion/comparison/function-return coverage.
   The VM currently stores typed values alongside numeric compatibility values;
   some legacy expression paths and decimal literal parsing still use f32.
   Arrays/objects currently copy on assignment; do not claim full Java VM parity.
2. Complete Bevy session discovery/switching and joint/weapon collision
   acceptance. Java's public entity network field is `id`; owner/source metadata
   drives collision routing internally.
3. Complete native label creation/text replication and missing base-runtime
   rotation/frame-range features. Verify all aliases, pooled/dynamic declarations,
   locally evaluated spawn attributes and lifetime/reactivation behavior.
4. Complete unbounded/dynamic native effect lifetime handling, animation
   controller command replication, looping phase and mid-play speed changes,
   reconciliation under packet loss/reordering beyond numbered snapshot actions,
   and comprehensive Bevy multiplayer acceptance.
5. Validate packaged password distribution and all desktop targets. Session-only
   password entry and native client/server credential sharing are implemented.

These are functional gaps, not merely untested UI options. Do not advertise this
slice as complete multiplayer support or mark the migration request finished.

## Verification

Protocol tests assert Java/Zig byte offsets, every truncation of a snapshot,
bounded decoding, Unicode events, typed values and a real UDP acknowledgement/
retry lifecycle. The opt-in `Engine/multiplayer/tests/zig_server.rs` test patches
an isolated executable and exercises two native clients against the actual Zig
server: authentication rejection, entity creation, late-join transforms/actions,
shared arrays, user data, targeted messages, timers, scene isolation, destruction
and session switching. It kills/reaps only its owned server.

```powershell
$env:SCENEMAX_TEST_MP_SERVER = 'C:\dev\scenemax_desktop\tools\multiplayer-server\bin\windows-x64\scenemax-mp-server.exe'
cargo test -p scenemax_multiplayer --test zig_server -- --ignored
cargo test -p scenemax_runtime multiplayer --lib
cargo test -p scenemax_ide_services multiplayer --lib
cargo test -p scenemax_ide multiplayer --lib
```

`SCENEMAX_SMOKE_MULTIPLAYER=1` with the normal IDE `--smoke-frames` /
`--smoke-screenshot` options opens the settings form in an isolated project.
Temporary acceptance artifacts belong under `target/multiplayer-audit`.


### Relative axes and rendered widget acceptance (2026-10-06)

Relative X/Y/Z rotations now preserve local-axis semantics in native execution,
evaluated multiplayer commands and remaining-time late-join replay. Ordinary
left/right turns keep their world-Y basis. Rotate-to/reset, general multi-axis
rotation and overlapping-controller behavior remain incomplete; native 3D labels
and their text replication are still missing.

`Tests/widget_multiplayer.py --profile release --automated` captures both Bevy
clients after timed live updates using the existing renderer audit. The rendered
live-update check passed for text, colors, font size, hidden state and isolation
of unmarked labels. Add `--rotations` to assert local and late-join remote X/Z
orientation against an independently calculated quaternion. This is representative
acceptance, not complete widget, motion or multiplayer parity.


### Native world labels (2026-10-06)

Default-font 3D labels now render as world-space billboard meshes and replicate
initial/evaluated text plus later `.text = expression` changes. Text uses persistent
numbered snapshots for late joins. Unmarked labels stay local. Run the maintained
fixture with `--automated --labels` for two-client layout checks and PNG captures.
Custom label fonts, style presets, transparency, wider declaration attributes and
full pooled/dynamic lifecycle acceptance remain unfinished. This supersedes the
previous missing-label status for the implemented default-font slice only.


### Label appearance (2026-10-06)

Label creation now supports the five original style presets/aliases, their panel,
text and accent strip, and evaluated `transparency` percentages. Multiplayer spawn
snapshots carry canonical styles and evaluated transparency; subsequent text edits
retain them. Custom fonts, runtime appearance assignments and replication of
appearance changes on existing/reused entities remain unfinished.


### Project bitmap fonts (2026-10-06)

Named single-page BMFonts now render on 3D labels and retain their selection in
multiplayer creation snapshots and later text updates. Catalog lookup shares the
native UI path, including legacy unquoted catalog keys, project overrides and
built-in-only resources. Missing/unloadable fonts use the default renderer.
Other font formats and changes to an existing registered entity's appearance
remain incomplete; matching font assets are required on both clients.


### Rotation reset (2026-10-06)

Entity `.rotate (x,y,z)` now evaluates expressions and synchronizes its three-angle
orientation assignment. Position, scale and active turn controllers are preserved.
Late joiners receive the current transform; reset commands are not persistent
snapshot actions. Use `--automated --rotation-resets` in the two-client fixture to
check a live reset followed by X/Z turns alongside marked-widget updates.
Target-axis rotate-to and broader rotation parity remain unfinished.


### Target-axis rotate-to (2026-10-06)

Entity `.rotate to (y angle + 10) in duration seconds` now takes the shortest
signed local-axis turn from the current orientation. X/Y/Z, function arguments,
async execution and evaluated duration are supported; omitted duration is one
second. Multiplayer sends the evaluated target and late joiners retain that target
while resuming for the remaining time. Continuous input sends a bounded relative
step. Test with `--automated --rotate-to` in the two-client widget fixture.
Easing, camera-specific timed rotation and broader motion parity remain unfinished.


### Translation-axis movement (2026-10-06)

Entity `.move (x + distance) in duration seconds` now supports X/Y/Z translation
axes, evaluated expressions, function arguments and multiplayer replay. These axes
ignore entity facing and scale; forward/left commands retain their existing basis.
Omitted duration is one second. Late joins resume the remaining axis delta/time.
Use `--automated --axis-moves` in the two-client widget fixture. Motion easing and
broader physical/overlapping-motion fidelity remain unfinished.


### Connection loss and room rejoin (2026-10-07)

The client checks both network directions using an internal self-directed relay
event. After ten seconds without a matching reply, it closes the connection,
publishes `network.ready = 0`, and removes remote entities while preserving local
entities. The reserved `$scenemax:transport:probe` event is consumed by transport
and does not invoke script handlers. Automatic reconnect is not implemented.

Explicit session joins require server confirmation. Script departure/rejoin and
sustained network partition cleanup have real two-projector/Zig acceptance via
`Tests/widget_multiplayer.py --automated --session-rejoin` and `--partition`.
See the multiplayer parity report for evidence and remaining delivery limits.


### Action-end recovery (2026-10-07)

Updated clients retry unconfirmed numbered ends for up to eight seconds. Updated
server templates track each recipient independently, so a lost relay packet or
receipt can be recovered without repeating the action in the runtime. Newer
playback, entity deletion and room changes invalidate obsolete retry state.
`network.state.unconfirmedActionEnds` counts sender confirmation windows that
expired in the current connection/room; it is not a count of all lost packets.

Rebuild existing project servers in Project Settings to enable recipient recovery.
Old relays still receive the original end packet but do not provide confirmations.
Retry queues are bounded; prolonged loss/pressure can exhaust the recovery window.
Automatic reconnect and general snapshot/transform reliability remain unfinished.

Controller stop and asset-start timeout cleanup (2026-10-09) now bind to the
resolved Bevy entity and exact playback generation. Changing a script alias or
reusing a name cannot redirect a pending stop; replacement playback is preserved.
Cancellation releases the original action lease for the existing replicated end
path. General controller aliases and overlap semantics remain partial.

Controller runs (2026-10-09) now resolve startup pool aliases and capture outgoing
animation commands against the actual local playback target. Runtime scoped
aliases retain precedence. Missing entity/GLTF mappings do not publish unmatched
starts. This preserves evaluated commands, numbered lifetimes and existing packets.

Controller restart ownership (2026-10-09): running a controller again retires the
previous playback before resolving its new target. Retargeted aliases and failed
restarts release the old action lease; generation checks preserve new playback.

Controller parity follow-up (2026-10-09): registered events fire once per run
across repeated clips and re-arm on restart; events added during playback can
recognize thresholds already reached. Local controller cancellation now stops
only recorded player/graph pairs that still match, preserving independently
animated children and replacement graphs. Existing SceneMax command packets and
network action lifetimes remain unchanged.

Playback ownership follow-up (2026-10-09): rewind and animation-speed updates now
use recorded player/graph ownership, preserving independently animated attachments
and replacement graphs. Permanent speed commands also update pending animation
requests so asynchronous loading cannot discard the authored speed. Existing
SceneMax command/action-lifetime synchronization and packet formats are unchanged.
