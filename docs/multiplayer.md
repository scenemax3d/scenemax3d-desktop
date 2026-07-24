# Multiplayer

SceneMax multiplayer lets several game clients share the same session and scene through a
SceneMax UDP server. A developer writes normal SceneMax code, marks the entities that should
exist on all clients as `multiplayer`, and then drives those entities with the movement,
rotation, animation, attach, and IK commands that the runtime knows how to broadcast.

The important rule is simple: multiplayer is entity based. Local input stays local, but
commands that target a registered multiplayer entity are sent to the server and replayed on
the other clients.

## Quick Start

Create a networked actor by adding the `multiplayer` attribute to the entity declaration:

```scenemax
canvas.size 600,800
skybox.show solar system

player => fighter1_native: multiplayer, pos (0,0,0), scale 3, collision shape none async
camera.chase player

when key up is pressed do
  player.move forward 1 in 0.1 seconds
end do

when key left is pressed do
  player.turn left 15 in 0.1 seconds
end do

when key A is pressed once do
  player.Run at speed of 1
end do
```

Run two clients against the same server, session, and scene. Each client owns its own local
`player` entity, while the other clients receive it as a generated remote entity.

## Architecture

SceneMax uses a client/server model:

- Each game process starts a UDP multiplayer client only when multiplayer is configured.
- The server listens on one UDP port, accepts clients, and groups them by project, session,
  and scene.
- A client creates local multiplayer entities and receives a server-assigned network id for
  each one.
- The server relays creates, destroys, commands, active actions, and transform corrections to
  the other clients in the same session and scene.
- New joiners receive a snapshot of the existing scene, including entity spawn commands,
  transforms, and active long-running actions.

The server is intentionally a relay and session host. The game logic still runs in each
SceneMax client.

## Sessions And Scenes

A multiplayer room is identified by:

- Project GUID: separates games built from different SceneMax projects.
- Session id: separates groups of players within the same game.
- Scene id: separates players who are in different scenes or levels.

The default SceneMax desktop flow uses session id `1000` and scene `main`.

When a script switches scenes, the runtime calls `joinScene` with the new level id. Remote
entities from the previous scene are removed, local multiplayer entities are registered again
as the new scene loads, and the server sends a snapshot for the new scene.

Scene ids are folder-style paths. The root scene is `main`; a scene folder such as `scene2`
uses `scene2`.

## Marking Entities As Multiplayer

Add `multiplayer` to the entity attributes:

```scenemax
hero => hero_native: multiplayer, pos (0,0,0), scale 2 async
ball => sphere: multiplayer, pos (1,2,3), radius 0.5
crate => box: multiplayer, size (2,2,2), pos (2,0,0)
stairs_1 => stairs: multiplayer, size (2,0.25,0.4), steps 6, pos (4,0,0)
hand_target => collider sphere: multiplayer, pos (0,1,0), radius 0.2
name_tag => label: multiplayer, text "Hero", style "holo_glass", size (50,10)
```

You can also set the same flag from the designer; the saved design emits entities with
`multiplayer: true` and generated SceneMax code that includes the multiplayer attribute.

Current runtime registration covers:

- 3D model/resource entities.
- All built-in primitive entities: `sphere`, `box`, `cylinder`, `hollow cylinder`, `quad`,
  `wedge`, `cone`, `stairs`, and `arch`.
- Collider primitives that use the same primitive declarations, such as `collider sphere`,
  `collider box`, or `collider hollow cylinder`.
- Runtime labels created with `label`.

The parser accepts `multiplayer` as a general model/entity attribute, but a networked entity
must also be registered by the runtime. If a type is parsed as multiplayer but no registration
path exists for that type, commands against it will be skipped because it never receives a
network id.

Use the same resource names and packaged assets on every client. Remote clients recreate an
entity by running a compact spawn command such as:

```scenemax
{network_entity} => hero_native: pos (0,0,0), scale 2, collision shape none
```

`{network_entity}` is an internal placeholder. Do not write it in normal game code; use your
own variable names. The networking layer replaces it with the generated remote runtime name.

## What Gets Synchronized

SceneMax currently synchronizes the following multiplayer entity behavior:

- Entity creation for registered multiplayer entities.
- Entity destruction when the owning client destroys or disconnects.
- Periodic transform corrections for position and rotation.
- Movement commands:
  `move forward/backward/left/right/up/down`,
  `move (x + n)`, `move (y - n)`, `move (z + n)`,
  and `move to (...)`.
- Rotation commands:
  `rotate (...)`, `rotate to (...)`, `turn left/right/forward/backward`, and rotation reset.
- Model animation commands, including animation name, speed, and frame ranges.
- Attach commands between multiplayer entities.
- IK apply/remove, layer target, weight, blend, play, and stop commands.
- Label text changes with `label_name.text = ...`.
- Persistent structural commands, such as attach and IK, so late joiners receive the current
  structure in the snapshot.
- Persistent label text state, so late joiners receive the latest synchronized label text.
- Scalar variables declared with `network var`, such as `network var fighters_count = 0`.
  Clients evaluate assignments locally, then send the resulting value to the server for relay.

Timed movement, rotation, and animation commands are sent both as normal commands and as
active actions. Active actions let a late-joining client resume a command at the correct
elapsed time instead of replaying it from the beginning.

## What Does Not Get Synchronized

These stay local unless you explicitly turn them into commands on multiplayer entities:

- Keyboard, mouse, and controller input events.
- Camera state, UI, HUD drawing, screen/canvas settings, skybox, lights, audio, and debug
  output.
- Plain variables, arrays, random numbers, timers, and custom game state that are not
  expressed as commands on networked entities or scalar `network var` declarations.
- Commands targeting entities that are not marked and registered as multiplayer.
- Assets that are only available in the IDE project folder and are not packaged with the game.

Design your game so each client handles its own input and sends only the resulting entity
actions through multiplayer.

## Recommended Script Pattern

Keep local player control code separate from shared entity state:

```scenemax
add "hero.code" code

when key W is pressed do
  hero.move forward 1 in 0.1 seconds
end do

when key D is pressed do
  hero.turn right 10 in 0.1 seconds
end do

when key Space is pressed once do
  hero.Jump at speed of 1
end do
```

In `hero.code` or a designer-generated file:

```scenemax
hero => hero_native: multiplayer, pos (0,0,0), scale 2, collision shape none async
```

Good multiplayer scripts follow these habits:

- Mark every actor that other players must see as `multiplayer`.
- Mark attach targets and IK target helpers as `multiplayer` when remote clients need to
  resolve them.
- Use short, repeatable input commands rather than large one-off teleports for continuous
  controls.
- Prefer `async` for heavier 3D models so remote creation does not block rendering.
- Keep shared actor creation in files that are included by every scene/client that needs them.
- Avoid using random local choices for shared gameplay unless the random result is applied
  through a synchronized entity command.

## Designer Workflow

The visual designer can produce multiplayer-ready entities. For each shared entity:

1. Enable the entity's multiplayer flag.
2. Save the design so the generated `.code` contains `multiplayer` attributes.
3. Include the generated code from the scene:

```scenemax
add "horse1.code" code
```

The `multi-player-test` project demonstrates this pattern. Its `main` script includes
`horse1.code` and then drives the `horse` entity from keyboard handlers.

## Local IDE Runs

When the IDE launcher sees the word `multiplayer` in the scanned program source, it adds
multiplayer JVM properties to the game process.

Default IDE values:

| Setting | Default |
| --- | --- |
| Server | Project Settings multiplayer server IP, or `127.0.0.1` |
| Port | Project Settings multiplayer port, or `9001` |
| Password | Project Settings multiplayer password, if present |
| Session id | `1000` |
| Create session | `false` |
| Session name | Active project name, or `local` |
| Scene | `main` |
| Player | Windows user name plus a short timestamp suffix |

The client only starts if `scenemax.multiplayer.server` or `SCENEMAX_MULTIPLAYER_SERVER` is
set. If no server is configured, multiplayer code still runs locally, but no network traffic
is sent.

## Packaged Games

Packaged games cannot rely on IDE-only project paths. The packager embeds multiplayer metadata
at the beginning of the packaged `main` script when it detects multiplayer source code:

```text
//$[multiplayer_server]=127.0.0.1;
//$[multiplayer_port]=9001;
//$[multiplayer_session_id]=1000;
//$[multiplayer_create_session]=false;
//$[multiplayer_session_name]=my-game;
//$[multiplayer_scene]=main;
```

At runtime, `MainWinApp` reads those metadata values and turns them into the same
`scenemax.multiplayer.*` system properties used by the IDE flow.

Before distributing a multiplayer game, verify:

- The packaged `main` contains the multiplayer metadata.
- The packaged game contains every model, material, texture, animation, IK resource, effect,
  and code file needed by multiplayer spawn commands.
- The executable can reach the configured UDP server IP and port.
- The server executable was built for the target platform and patched with the correct
  project GUID, port, game name, project path, and password hash.

## Multiplayer Server

SceneMax expects platform server executables under:

```text
tools/multiplayer-server/bin/windows-x64/scenemax-mp-server.exe
tools/multiplayer-server/bin/linux-x64/scenemax-mp-server
tools/multiplayer-server/bin/macos-x64/scenemax-mp-server
```

The Project Settings multiplayer tab can build/copy the selected server into the project
`multiplayer_server` folder. During that build, SceneMax patches the embedded
`SCENEMAX_MP_CONFIG` block with:

- Game name.
- Project path.
- UDP port.
- Password hash, or disabled password state.
- Project GUID.

If no prebuilt executable exists, SceneMax attempts to build the server from
`tools/multiplayer-server/zig/scenemax_multiplayer_server.zig` using Zig.

Run the server before running clients. The default port is UDP `9001`; make sure local
firewalls and deployment hosts allow UDP traffic on that port.

## Runtime Configuration Reference

Every runtime setting can be supplied as a Java system property or environment variable:

| Purpose | Java property | Environment variable |
| --- | --- | --- |
| Server host/IP | `scenemax.multiplayer.server` | `SCENEMAX_MULTIPLAYER_SERVER` |
| Server UDP port | `scenemax.multiplayer.port` | `SCENEMAX_MULTIPLAYER_PORT` |
| Password | `scenemax.multiplayer.password` | `SCENEMAX_MULTIPLAYER_PASSWORD` |
| Session id | `scenemax.multiplayer.sessionId` | `SCENEMAX_MULTIPLAYER_SESSION_ID` |
| Create session | `scenemax.multiplayer.createSession` | `SCENEMAX_MULTIPLAYER_CREATE_SESSION` |
| Session name | `scenemax.multiplayer.sessionName` | `SCENEMAX_MULTIPLAYER_SESSION_NAME` |
| Player name | `scenemax.multiplayer.player` | `SCENEMAX_MULTIPLAYER_PLAYER` |
| Scene id | `scenemax.multiplayer.scene` | `SCENEMAX_MULTIPLAYER_SCENE` |
| Project GUID | `scenemax.multiplayer.projectGuid` | `SCENEMAX_MULTIPLAYER_PROJECT_GUID` |

If `sessionId` is `0`, or `createSession` is `true`, the server allocates the next session
id. Otherwise the client uses the requested session id; if that session does not exist yet,
the server creates it with that id. The built-in desktop/package flow uses session `1000`.

## Diagnostics

The client writes multiplayer diagnostics to:

```text
scenemax-multiplayer-client.log
```

Useful successful lines look like:

```text
[SceneMax MP] registered local entity runtime=hero@1 type=1 archetype=hero_native
[SceneMax MP] send create runtime=hero@1 createRequestId=1 archetype=hero_native
[SceneMax MP] create accepted runtime=hero@1 createRequestId=1 networkId=101
[SceneMax MP] send command entity=101 command="{network_entity}.move forward 1 in 0.1 seconds"
```

Common troubleshooting checks:

- No log file: the client did not start. Check server settings or packaged metadata.
- Login rejected: password, project GUID, or server configuration does not match.
- Entity never appears remotely: the entity is not marked `multiplayer`, did not register,
  or its resource is missing from the packaged game.
- Commands are skipped for an unregistered runtime: the target entity has no network id.
- Attach or IK does not resolve remotely: the referenced parent/target/helper entity also
  needs to be multiplayer.
- IDE works but packaged exe does not: inspect packaged metadata and asset inclusion first.
- Late joiner sees entities but not current actions: check for commands that are not one of
  the active timed or persistent structural commands listed above.

## Production Checklist

Before shipping a multiplayer SceneMax game:

- Start with a small two-client local test using `127.0.0.1`.
- Confirm every shared actor is marked `multiplayer`.
- Confirm every shared helper target for attach/IK is also marked `multiplayer`.
- Confirm all required assets are packaged, not only available from `projects/<name>/resources`.
- Build the server from Project Settings for the deployment OS.
- Run the packaged executable against the packaged server, not only the IDE launcher.
- Check `scenemax-multiplayer-client.log` for registration, create acknowledgement, and
  command dispatch lines.
- Test scene switching with at least two clients.
- Test a late joiner while another player is moving, rotating, animating, or using attach/IK.
