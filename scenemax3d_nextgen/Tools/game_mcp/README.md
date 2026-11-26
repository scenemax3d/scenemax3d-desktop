# SceneMax Game MCP

SceneMax Game MCP lets a local AI application send complete SceneMax snippets to one running browser or desktop game. The player can continue playing. Single-player and multiplayer games use the same connection. Use a game exported with Game MCP support.

## Start the service

Extract the package for your operating system. On Windows run `scenemax_game_mcp.exe`. On Linux or macOS run `./scenemax_game_mcp` in a terminal. Keep it running while playing. Closing the service disconnects its agents and games.

In SceneMax Studio, click **Game MCP** in the status bar to open the monitor and start the service, then run the game. The monitor shows connection instructions and recent game and agent activity. Studio hosts the service until Studio exits, or uses the standalone service if it is already running. **IDE MCP** opens the separate editor automation monitor.

## Connect a local AI application

Configure a stdio MCP server in your AI application. Use the absolute path to the executable you extracted. Studio distributions also include the executable alongside Studio.

```json
{
  "mcpServers": {
    "scenemax-game": {
      "command": "/absolute/path/to/scenemax_game_mcp",
      "args": ["stdio"]
    }
  }
}
```

On Windows, for example, use `"command": "C:\\SceneMax\\scenemax_game_mcp.exe"`. Start a fresh MCP connection after changing the configuration. The executable's default mode runs the local service. The AI application starts its own copy with `stdio` for each MCP connection.

## Pair a running game

- Focus the desktop game or browser tab and press **Ctrl+F12**. The game connects to the local service, shows the code for one second and copies it to the clipboard. Press the shortcut again to copy the same pending code. Native command-line launches can explicitly request pairing on startup with `--game-mcp`.
- **Web:** Allow local-network and clipboard access if prompted. If automatic copying is blocked, click **Copy pairing code**. A secure browser context and clipboard permission are required for clipboard writes. The page must be allowed to connect to `ws://127.0.0.1:8766`. The optional `?game-mcp` URL parameter also exposes a **Pair AI** button.
- Paste the copied code to the agent you want to pair. For example: “Use SceneMax Game MCP to pair with this code, create a box, then move it after one second.” Codes are single-use and expire after five minutes. A code grants permission to execute any SceneMax source that the projector supports.
- Say “Unpair from my game” to release the connection. A later pairing requires a fresh code. Press **Ctrl+F12** again to obtain a new code.

| MCP tool | Arguments | Result |
| --- | --- | --- |
| `pair` | `code`, the displayed pairing code | Pairs this MCP connection with the specific game instance. |
| `run_partial_code` | `code`, one string containing the SceneMax source | Returns `202` after the running projector accepts the snippet, or a tool error containing `400` for rejected source. |
| `unpair` | None | Releases the connection. The game continues and previously scheduled actions retain their normal semantics. |

A snippet can contain literal newlines. The game connection carries raw UTF-8 source in one WebSocket text frame/message, up to 256 KiB. The local agent's stdio interface uses MCP JSON-RPC. The service binds only to IPv4 loopback on port 8766. Browser origins can register only their own game connections. Only the native agent adapter can claim a pairing code. Requests on a paired connection are serialized. If a game fails to acknowledge within 30 seconds the connection is dropped and the action outcome may be unknown.

## Build and distribute

From the `scenemax3d_nextgen` workspace run `cargo build --locked --release -p scenemax_game_mcp`. The helper is a standalone executable and does not require Studio, Bevy, a Zig server, or a JVM on the player's machine.

The **SceneMax Game MCP** GitHub Actions workflow packages builds for Windows x64, Linux x64, macOS Apple Silicon, and macOS Intel. Run the workflow manually for downloadable build artifacts. Pushing a `game-mcp-v*` release tag publishes the archives as GitHub Release assets.
