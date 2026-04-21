# Built-in MCP Server and AI Assistants

SceneMax3D includes a built-in local MCP server so AI assistants can work directly with your open project, active editor tab, and scene designer. Instead of copying code into a chat window, you can let Claude or Codex inspect the current SceneMax workspace and take action inside the IDE.

The same SceneMax tool registry is also available to the built-in Local Gemma workflow, so you can use a local model for many of the same IDE tasks without sending project data to a cloud provider.

## Why This Matters

Without SceneMax MCP, an AI assistant only sees what you manually paste into chat. With SceneMax MCP, the assistant can:

- inspect the active project and editor context
- search files and text across your SceneMax workspace
- open, read, create, and modify project files
- save the current document
- create new SceneMax scene, UI, shader, environment-shader, and material files
- inspect the active scene designer and list entities
- add primitives and create cinematic rigs in the designer
- author `.smui` UI documents end-to-end: discover the widget schema, enumerate available sprites and fonts, add layers and widgets, and validate the result before saving
- search Sketchfab assets using SceneMax-compatible filters
- import Sketchfab models into the active project's `resources/Models` folder
- launch a SceneMax preview run

In practice, this means less copy-paste, fewer context switches, faster iteration, and much more reliable AI help on real project tasks.

## How It Works

SceneMax starts a local HTTP MCP endpoint inside the IDE, typically at:

```text
http://127.0.0.1:8765/mcp
```

If that port is busy, SceneMax can automatically choose another localhost port unless you set a fixed one in `Settings > MCP`.

For quick health checks, SceneMax also exposes a local status endpoint at:

```text
http://127.0.0.1:8765/mcp/status
```

The built-in MCP server is local-only:

- it binds to `127.0.0.1`
- it is available only while SceneMax is running
- it exposes SceneMax-specific tools instead of generic shell access

SceneMax supports AI integration in four related ways:

| Client | How it connects | Best use |
| --- | --- | --- |
| Claude Code | Directly to the SceneMax MCP HTTP endpoint | Strong cloud coding assistant with direct SceneMax IDE actions |
| Codex | Directly to the SceneMax MCP HTTP endpoint | Strong cloud coding assistant with direct SceneMax IDE actions |
| Claude Desktop | Through the bundled `scenemax_mcp_proxy.jar` stdio proxy | Desktop Claude users who want SceneMax tools as an MCP connector |
| Local Gemma | Uses the same SceneMax tool registry inside the AI Console | Local/offline-friendly workflow inside SceneMax |

## Tool Schemas And Reference

The built-in MCP server exposes SceneMax tools through standard MCP `tools/list`.

Each tool entry includes:

- `name`
- `description`
- `inputSchema`
- `outputSchema`

That means an MCP client or agent can discover the live tool contract directly from the running IDE instead of relying on hard-coded assumptions.

For a human-readable reference covering the current tool set, their purpose, common inputs, outputs, and recommended usage patterns, see [SceneMax MCP Tool Reference](mcp-tools-reference.md).

## What You Can Actually Do With It

Here are the main task categories the built-in MCP tools unlock today.

### Project and File Operations

- Get the active project, workspace, script root, and active editor snapshot
- List the project tree under the workspace, project, scripts, or resources roots
- Search for files by name
- Search for text across the project
- Read a file
- Create a file
- Modify a file
- Open a file in the editor
- Save the active editor tab

### Scene Designer Operations

- Create new SceneMax documents:
  - scene (`.smdesign`)
  - UI (`.smui`)
  - shader (`.smshader`)
  - environment shader (`.smenvshader`)
  - material (`.mat`)
- List entities in the active scene designer tab
- Add primitives to the active scene
  - `designer.add_primitive` accepts: `sphere`, `box`, `wedge`, `cylinder`, `cone`, `hollow_cylinder`, `quad`, `stairs`, and `arch`
- Create a cinematic rig in the active scene

### UI Designer Authoring

The `ui.*` tool family lets an AI assistant precisely author `.smui` UI documents (layers, widgets, constraints) without guessing property names or resource identifiers:

- Discover the schema with `ui.get_schema` (widget types, enums, common and type-specific properties)
- Enumerate available resources with `ui.list_sprites` and `ui.list_fonts` (merges global and project-scoped catalogs)
- Inspect a document with `ui.list_layers`, `ui.list_widgets`, and `ui.get_widget`
- Mutate layers with `ui.add_layer`, `ui.update_layer`, `ui.delete_layer`
- Mutate widgets with `ui.add_widget`, `ui.update_widget`, `ui.delete_widget` (renames propagate to constraint targets)
- Gate changes with `ui.validate_document` before handing a UI over for review (unique names, resolved constraint targets, `MATCH_CONSTRAINT` and `aspectRatio` rules, sprite/font references)

### Asset and Preview Operations

- Search Sketchfab for downloadable models using SceneMax-oriented filters
- Import a Sketchfab model into project resources with `asset.import_sketchfab`
  - requires a Sketchfab API token
  - registers the imported model in `models-ext.json`
- Start a preview run from the current SceneMax project

## Productivity Boosts

This feature is most useful when you treat the assistant like an IDE teammate instead of a detached chatbot.

### Faster debugging

You can ask the assistant to:

- find where a trigger, health system, or camera behavior is implemented
- search for a command across scripts
- inspect the active file and propose a fix
- patch a script, save it, and tell you what changed

### Faster content creation

You can ask the assistant to:

- create a new scene or UI document
- scaffold a shader or material file
- add primitives to a designer scene
- create a cinematic rig preset for a cutscene or flyover

### Faster exploration

You can ask the assistant to:

- list all scene or script files related to a mechanic
- search for references to a specific object, variable, or command
- summarize the active project structure
- find a low-poly Sketchfab asset for a prototype
- import a chosen Sketchfab model directly into the project resources

### Less friction inside the IDE

Instead of doing all of this manually:

1. find the right folder
2. search files
3. open the right script
4. copy text into an AI chat
5. paste the answer back
6. create missing files yourself

you can often ask the assistant to do the whole flow inside SceneMax.

## Installation Overview

The important idea is simple:

- You do **not** install a separate SceneMax MCP server.
- The server is built into SceneMax and runs when the IDE is open.
- What you install is the AI client you want to connect to it: Claude Code, Codex, Claude Desktop, or Local Gemma.

Before setting up any client:

1. Start SceneMax3D.
2. Open `Settings > MCP`.
3. Check the `Current Endpoint` shown by SceneMax.
4. Optionally set a `Fixed MCP Port` if you want Claude/Codex to always use the same localhost port.

If you leave the port blank or set it to `DEFAULT`, SceneMax will fall back to automatic local port selection.

## Claude Code Setup

Claude Code is one of the best ways to use SceneMax MCP because SceneMax can prepare the CLI and point it at the local endpoint for you.

### Recommended setup inside SceneMax

1. Open `Settings > MCP`.
2. In the Claude section, click `Install`.
3. If prompted, make sure Node.js and `npm` are installed on Windows first.
4. After install completes, click `Login`.
5. Finish Claude authentication in the terminal window SceneMax opens.
6. Return to SceneMax and use the AI Console or Claude Code normally.

Inside the SceneMax AI Console, Claude Code receives a temporary MCP config automatically for each request. That means you can use Claude Code with SceneMax tools inside the IDE without permanently registering the `scenemax` server in your global Claude config.

### Manual install

If you prefer to install Claude Code yourself:

```bash
npm install -g @anthropic-ai/claude-code
claude auth login
```

If SceneMax does not detect the executable automatically, set `Claude CLI Path Override` in `Settings > MCP`.

### Optional: register SceneMax in Claude Code globally

If you want standalone Claude Code sessions outside the SceneMax AI Console to see the SceneMax server automatically, register it once with:

```bash
claude mcp add --transport http --scope user scenemax http://127.0.0.1:8765/mcp
```

If you configured a different fixed port in SceneMax, use that port instead.

### Testing the connection

After setup, you can verify it with a prompt such as:

- `Use the SceneMax MCP tools to list available tools.`
- `Use SceneMax to search for "camera" in the project.`

## Codex Setup

Codex also connects directly to the built-in SceneMax MCP HTTP endpoint, but unlike Claude Code, Codex needs the `scenemax` server to be registered in Codex first if you want Codex to use the live SceneMax tools.

### Recommended setup inside SceneMax

1. Open `Settings > MCP`.
2. In the Codex section, click `Install`.
3. If prompted, install Node.js and `npm` first.
4. Let SceneMax locate the installed `codex` executable.
5. If auto-detection fails, set `Codex CLI Path Override`.

### Manual install

```bash
npm install -g @openai/codex
```

If your Codex installation requires authentication, complete that in a terminal before using it with SceneMax.

### Register SceneMax in Codex

After Codex is installed, add the SceneMax endpoint to Codex:

```bash
codex mcp add scenemax --url http://127.0.0.1:8765/mcp
```

If you configured a different fixed port in SceneMax, use that port instead.

### Testing the connection

Good first checks are:

```bash
codex mcp list
```

Then ask Codex something like:

- `Use the SceneMax MCP server to list tools and search for "camera".`
- `Inspect the active SceneMax project context and summarize the main scripts.`

## Claude Desktop Setup

Claude Desktop does not talk directly to the SceneMax HTTP endpoint. Instead, SceneMax includes a tiny stdio proxy jar named `scenemax_mcp_proxy.jar` that forwards Claude Desktop MCP requests to the SceneMax IDE endpoint.

### Easiest path

1. Open SceneMax.
2. Go to `Settings > MCP`.
3. Click `See config` in the Claude section.
4. Copy the generated `scenemax` MCP server block.
5. In Claude Desktop, open `Settings > Developer > Local MCP servers`.
6. Click `Edit config`.
7. Paste the SceneMax block inside the existing `mcpServers` object.
8. Save the config.
9. Keep SceneMax open.
10. Restart Claude Desktop or refresh its connectors list.

When configured correctly, `scenemax` should appear in Claude Desktop as an MCP server.

### Important note

Claude Desktop launches the proxy jar, and the proxy jar forwards to the live SceneMax endpoint. If SceneMax is not running, the connector cannot control the IDE.

## Local Gemma Setup

Local Gemma is the best option if you want a local model running on your own machine. In SceneMax, Local Gemma uses the same SceneMax tool registry as MCP, but it runs through the built-in AI Console rather than through an external MCP client.

### Recommended setup

1. Open `Settings > Local Gemma`.
2. Enable Local Gemma.
3. Choose a download variant.
4. Click `Download Gemma`.
5. After download finishes, click `Start Local Gemma`.
6. Click `Test Bridge`.
7. Open the AI Console and choose `Local Gemma` as the provider.

### Which model should you choose?

- `Gemma 4 E2B (Recommended)`:
  Smaller and faster. Best default for most Windows laptops.
- `Gemma 4 E4B`:
  Higher quality, but heavier on memory and generally better on stronger PCs.

### VC++ runtime requirement

If Local Gemma fails with missing DLL errors on Windows, use:

- `Settings > Local Gemma > Install VC++ Runtime`

SceneMax can download and launch the Microsoft Visual C++ Redistributable helper for you. After that:

1. finish the Microsoft installer
2. return to SceneMax
3. click `Start Local Gemma`
4. click `Test Bridge`

### Local Gemma fields

The Local Gemma panel also exposes:

- `Endpoint URL`
- `Model Name`
- `API Key (optional)`
- `Timeout Seconds`

These are useful if you want SceneMax to point at a compatible Gemma-style local bridge instead of the default bundled local setup.

## Typical User Workflows

### Example: fix a gameplay script

Ask:

`Find where the camera is configured, inspect the active script, and update it so the player starts with a wider chase view.`

The assistant can:

- inspect the active project context
- search for camera-related files or text
- open and read the relevant script
- modify the file
- save it

### Example: create a new scene asset

Ask:

`Create a new desert arena scene in the scripts folder, add a few primitive placeholders, and prepare a cinematic rig for an intro flyover.`

The assistant can:

- create the new SceneMax document
- add scene primitives such as `wedge`, `cone`, `stairs`, and `arch`
- create a cinematic rig

### Example: asset discovery

Ask:

`Find a downloadable low-poly animated dragon on Sketchfab that would work for a prototype enemy.`

The assistant can:

- call the Sketchfab search tool
- filter results in a SceneMax-friendly way
- return likely candidates much faster than a manual browser search

## AI Console vs External Clients

SceneMax now gives you two complementary workflows.

### Use the AI Console when you want

- a built-in chat experience inside SceneMax
- quick switching between Local Gemma, Claude Code, and Codex
- less setup friction once everything is installed

Note:
Claude Code in the AI Console gets a temporary MCP config automatically, while Codex benefits from a one-time `codex mcp add scenemax --url ...` setup first.

### Use external clients when you want

- your preferred Claude or Codex environment
- longer-form coding/refactoring workflows outside the IDE
- the same SceneMax project tools available through MCP

## Security and Privacy Notes

- The MCP endpoint is local-only and binds to `127.0.0.1`.
- Local Gemma can keep more of your workflow on your own machine.
- Claude Code and Codex are external AI clients, so their privacy model depends on the provider and your account settings.
- The assistant gets access only to the tools SceneMax exposes, not arbitrary unrestricted desktop control.

## Troubleshooting

### The MCP endpoint says unavailable

- Make sure SceneMax is fully running.
- Open `Settings > MCP` and verify `Current Endpoint`.
- Try setting a `Fixed MCP Port` and reopening the client.

### Claude Code or Codex was not found automatically

- Install the CLI manually.
- Reopen SceneMax.
- If needed, set the CLI path override in `Settings > MCP`.

### Claude asks for SceneMax tools but cannot find them

- Verify that the `scenemax` MCP server is configured.
- Make sure SceneMax is still open.
- Re-check the endpoint or Claude Desktop config snippet.

### Local Gemma fails to start

- Download the model again if the install is incomplete.
- Use `Install VC++ Runtime` if Windows reports missing DLL dependencies.
- Click `Test Bridge` after starting the local service.

## Building From Source

If you run SceneMax from source and need the standalone proxy jar for Claude Desktop, build:

```bash
./gradlew mcpProxyJar
```

This produces:

```text
build/libs/scenemax_mcp_proxy.jar
```

That jar is the small bridge Claude Desktop can launch as a stdio MCP server while SceneMax itself continues serving the real IDE tools over local HTTP.

## Summary

The built-in SceneMax MCP server turns Claude and Codex into real IDE-aware assistants, while Local Gemma brings many of the same tool-driven workflows to a local model. The result is a much tighter loop for searching, editing, creating, previewing, and understanding SceneMax projects with far less manual effort.
