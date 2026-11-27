import * as runtime from "./runtime.js";

const button = document.getElementById("pair-game");
const status = document.getElementById("pair-status");
const showButton = new URLSearchParams(location.search).has("game-mcp");
let socket;
let pairingCode;
let paired = false;
let timer;

function notice(text) {
  clearTimeout(timer);
  status.textContent = text;
  timer = setTimeout(() => { status.textContent = ""; }, 1000);
}

async function copyCode(code, manualFallback = false) {
  notice("Game MCP pairing code: " + code);
  try {
    await navigator.clipboard.writeText(code);
    if (pairingCode === code) button.hidden = !showButton;
  } catch {
    if (pairingCode !== code) return;
    if (manualFallback) window.prompt("Copy this Game MCP pairing code", code);
    // Some browsers require a fresh click after the WebSocket reply arrives.
    button.textContent = "Copy pairing code";
    button.hidden = false;
  }
}

function requestPairing() {
  if (paired) return notice("Game MCP already paired");
  if (pairingCode) return copyCode(pairingCode, true);
  if (socket) return;
  if (!runtime.run_partial_code) return notice("Rebuild this game with Game MCP support.");
  try {
    socket = new WebSocket("ws://127.0.0.1:8766/game");
  } catch {
    return notice("Cannot connect to local SceneMax Game MCP.");
  }
  socket.binaryType = "arraybuffer";
  socket.onmessage = async ({data}) => {
    if (!pairingCode && !paired) {
      if (typeof data !== "string" || !/^[0-9a-f]{24}$/.test(data)) return socket.close();
      pairingCode = data;
      await copyCode(data);
    } else if (data instanceof ArrayBuffer) {
      paired = true;
      pairingCode = undefined;
      button.hidden = true;
      socket.send("ready");
    } else {
      const connection = socket;
      let reply = "202";
      try { await runtime.run_partial_code(data); }
      catch { reply = "400"; }
      if (connection.readyState === WebSocket.OPEN) connection.send(reply);
    }
  };
  socket.onclose = () => {
    if (!paired && !pairingCode) notice("Cannot connect. Start SceneMax Game MCP locally.");
    socket = undefined;
    paired = false;
    pairingCode = undefined;
    button.textContent = "Pair AI";
    button.hidden = !showButton;
  };
}

button.hidden = !showButton;
button.onclick = requestPairing;
window.addEventListener("keydown", event => {
  if (event.ctrlKey && event.code === "F12") {
    event.preventDefault();
    if (!event.repeat) requestPairing();
  }
}, true);
