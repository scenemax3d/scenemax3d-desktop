SceneMax Web game

Serve this directory over HTTP on localhost, or HTTPS on a WebGPU-capable browser.
Opening index.html with file:// is unsupported. Upload the ZIP as an HTML game
on itch.io, with index.html at the ZIP root, and mark the upload playable in
the browser. Allow itch.io processing/extraction to finish before testing.
The game starts automatically; click or press a key inside it to enable audio.
No Rust, SDK or Effekseer installation is required on players' computers.

LOCAL TESTING
From the SceneMax source workspace, serve an extracted export with:
python Tools/serve_web.py PATH_TO_EXTRACTED_EXPORT --port 8080
The server binds to loopback and disables caching for rebuilds. Any other host
must serve .js and .mjs as text/javascript and .wasm as application/wasm.
Some Windows Python installations serve .mjs as text/plain by default, which
prevents the browser from running the game loader. Re-export after runtime
changes; replacing network.json does not update an older game's runtime/assets.

MULTIPLAYER
Browsers connect through the SceneMax WebSocket gateway to the Zig UDP server.
The exported network.json contains the saved Browser WebSocket URL from Project
Settings - Multiplayer. Enable Browser gateway there and configure its listener
and exact allowed website/iframe origin before exporting.
The sidecar also records allowed_origin. SceneMax local previews use this exact
address instead of a random port; multiplayer exports with a public origin must
be tested on that host or rebuilt with a local origin accepted by the gateway.
The browser loader reports an origin mismatch before starting the game. If you
edit network.json after export, keep allowed_origin aligned with gateway.json.

Build Server packages both executables plus gateway.json. Run Server Locally
starts both for local testing; Stop Local Server stops both. Local defaults are
ws://localhost:9002 and allowed origin http://localhost:8080. Serve this game from
that origin. For deployment, run the bundled UDP server and gateway on the same
host. Launching the gateway without arguments reads its adjacent gateway.json.
Public HTTPS games need a wss:// URL and a TLS reverse proxy forwarding WebSocket
upgrades to the gateway. Static hosting alone cannot run these server processes.
Desktop clients continue to connect directly to the Zig UDP server.

Web resource downloads are deduplicated by verified content. game-manifest.json
lists logical file paths and aliases; files.mjs downloads canonical files once
and mounts every original path before starting the game. Do not remove alias
entries or rename files independently of the manifest.
Bevy model, texture and audio loaders also read the mounted files, including
relative glTF dependencies, without fetching those files again over HTTP.

Web games start automatically when their files are mounted. Customize the
loading header, loading text, footer, and PNG/JPEG background in Package & Deploy
under Web. The default footer is "powered by SceneMax3D". Backgrounds are embedded
in index.html so they are available before runtime loading begins.
Browser audio policies may keep sound muted until the first click or key press;
this does not block game startup.

