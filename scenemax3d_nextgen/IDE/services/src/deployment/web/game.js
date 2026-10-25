import init, { mount_file, mount_alias, read_file, start_game } from "./runtime.js";
import {mountFiles} from "./files.mjs";
const $ = id => document.getElementById(id);
let failed = false, started = false;
function fail(error) {
  if (failed) return;
  failed = true;
  $("loading").hidden = false;
  $("effect-status").hidden = true;
  $("status").textContent = started ? "The game encountered an error." : "The game could not start.";
  $("error").textContent = error?.message || String(error);
  console.error(error);
}
window.addEventListener("error", event => fail(event.error || event.message));
window.addEventListener("unhandledrejection", event => fail(event.reason));
try {
  if (!navigator.gpu) throw Error("This game requires WebGPU. Use a supported browser over HTTPS or localhost.");
  if (!await navigator.gpu.requestAdapter()) throw Error("No compatible WebGPU graphics adapter is available.");
  await init();
  const response = await fetch("./game-manifest.json");
  if (!response.ok) throw Error(`Game manifest: HTTP ${response.status}`);
  const manifest = await response.json();
  let effects;
  if (manifest.effects) {
    effects = await import('./effects/bridge.js');
    effects.install(new URL('./', import.meta.url), manifest.files, manifest.effect_distortion === true, read_file);
    window.addEventListener('scenemax-effects-status', ({detail}) => {
      if (failed) return;
      if (detail.error) { fail(Error(detail.error)); return; }
      if (detail.loading) {
        $("effect-status").textContent = detail.loading;
        $("effect-status").hidden = false;
      } else if (detail.ready) $("effect-status").hidden = true;
    });
  }
  let gateway = "";
  if (manifest.multiplayer) {
    const network = await fetch("./network.json", {cache: "no-store"});
    if (!network.ok) throw Error("Multiplayer network.json could not be loaded.");
    const config = await network.json();
    gateway = config.websocket_url;
    if (typeof gateway !== "string" || !/^wss?:\/\//.test(gateway))
      throw Error("Set websocket_url in network.json to your SceneMax browser gateway. See README.txt in this export.");
    if (config.allowed_origin && location.origin !== config.allowed_origin)
      throw Error(`Multiplayer gateway allows ${config.allowed_origin}, but this game is running at ${location.origin}. Open the game at the allowed address, or update Allowed game origin in Project Settings > Multiplayer > Browser, redeploy the gateway, and rebuild the game.`);
  }
  await mountFiles(manifest, {
    fetchFile: url => fetch(url), mountFile: mount_file, mountAlias: mount_alias,
    retry: (path, attempt, total) => {
      if (!failed) $("status").textContent = `Retrying ${path} (${attempt}/${total})`;
    },
    progress: (complete, total) => {
      if (failed) return;
      $("progress").max = total;
      $("progress").value = complete;
      $("status").textContent = `${$("status").dataset.label}: ${complete} / ${total}`;
    },
  });
  started = true;
  $("loading").hidden = true;
  // Audio resumes on the first ordinary interaction; it never gates startup.
  effects?.unlockAudio();
  start_game(manifest.entry, gateway);
  $("game").focus();
} catch (error) { fail(error); }
