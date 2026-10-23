import {initRuntime, createContext} from './vendor/effekseer.js';
import {PlaybackRegistry} from './playback.mjs';
import {createEffectLoader} from './resources.mjs';
import {SceneBackground} from './background.mjs';

let context, registry, initializing, audio, failed = false, disposed = false;
let loadEffect, lastPending = -1;
let background, needsBackground = false;
const status = detail => window.dispatchEvent(new CustomEvent('scenemax-effects-status', {detail}));
function fail(error) {
  if (failed || disposed) return;
  failed = true;
  registry?.dispose();
  status({error:`Effekseer: ${error?.message || error}`});
  console.error(error);
}

// Bind only Effekseer's calls to Bevy's device; never replace browser prototypes.
function reversedDepthDevice(device) {
  const reverse = {less:'greater', 'less-equal':'greater-equal', greater:'less', 'greater-equal':'less-equal'};
  return new Proxy(device, {get(target, key) {
    if (key === 'createTexture') return descriptor => background.createTexture(descriptor);
    if (key === 'createRenderPipeline' || key === 'createRenderPipelineAsync') return descriptor => {
      if (descriptor.depthStencil) descriptor = {...descriptor, depthStencil: {...descriptor.depthStencil,
        depthCompare:reverse[descriptor.depthStencil.depthCompare] || descriptor.depthStencil.depthCompare,
        depthBias:-(descriptor.depthStencil.depthBias || 0),
        depthBiasSlopeScale:-(descriptor.depthStencil.depthBiasSlopeScale || 0)}};
      return target[key](descriptor);
    };
    const value = Reflect.get(target, key, target);
    return typeof value === 'function' ? value.bind(target) : value;
  }});
}

async function initialize(device) {
  background = new SceneBackground(device);
  status({loading:'Loading effect renderer…'});
  device.addEventListener('uncapturederror', event => fail(event.error));
  device.lost.then(info => fail(Error(`WebGPU device lost: ${info.message}. Reload the game.`)));
  await initRuntime({backend:'webgpu', device:reversedDepthDevice(device),
    scriptPath:new URL('./vendor/effekseer-webgpu.js', import.meta.url).href,
    wasmPath:new URL('./vendor/effekseer-webgpu.wasm', import.meta.url).href});
  if (disposed) return;
  // Upstream 0.1.0 needs a context to select external-only initialization. It is
  // never drawn or presented; the visible target and depth belong to Bevy.
  const scratch = new OffscreenCanvas(1, 1).getContext('webgpu');
  context = await createContext({backend:'webgpu', device, canvasContext:scratch,
    colorFormat:navigator.gpu.getPreferredCanvasFormat(), depthFormat:'depth32float',
    width:1, height:1, audioContext:audio});
  if (disposed) { context.release(); return; }
  registry = new PlaybackRegistry(context, path => loadEffect(context, path), fail);
}

export function install(siteBase, manifestFiles, distortion = false, readFile) {
  needsBackground = distortion;
  loadEffect = createEffectLoader(siteBase, manifestFiles, readFile);
  globalThis.scenemaxEffects = {
    background(encoder, source, viewport, format) {
      if (!needsBackground || !registry || failed || disposed) return;
      try { background.capture(context, encoder, source, viewport, format); }
      catch (error) { fail(error); }
    },
    sync(device, json, delta) {
      if (failed || disposed) return '[]';
      if (!initializing) initializing = initialize(device).catch(fail);
      if (!registry) return '[]';
      try {
        background.retireSubmitted();
        const completed = registry.sync(JSON.parse(json), delta);
        if (failed) return '[]';
        if (registry.pending !== lastPending) {
          lastPending = registry.pending;
          status(lastPending ? {loading:`Loading effects: ${lastPending} remaining…`} : {ready:true});
        }
        return JSON.stringify(completed);
      } catch (error) { fail(error); return '[]'; }
    },
    draw(pass, projection, camera, ids, format) {
      if (!registry || failed || disposed) return;
      try {
        if (format === 'unsupported') throw Error('This camera color format is not supported by the effect renderer. Use an SDR camera target.');
        registry.show(JSON.parse(ids));
        context.setProjectionMatrix(projection);
        context.setCameraMatrix(camera);
        context.drawToRenderPass(pass, {colorFormat:format, depthFormat:'depth32float'});
      } catch (error) { fail(error); }
    },
  };
}

// Prepare audio without gating automatic startup on a user gesture.
export function unlockAudio() {
  if (audio) return;
  audio = new AudioContext();
  const resume = () => {
    if (audio?.state === 'suspended') audio.resume().catch(console.warn);
  };
  window.addEventListener('pointerdown', resume);
  window.addEventListener('keydown', resume);
  audio.addEventListener('statechange', () => {
    if (audio.state === 'running' || audio.state === 'closed') {
      window.removeEventListener('pointerdown', resume);
      window.removeEventListener('keydown', resume);
    }
  });
  resume();
}

export function dispose() {
  if (disposed) return;
  disposed = true;
  registry?.dispose();
  // Native asynchronous loaders may still be using this context. Release it
  // only after stale loaded effects have been returned to their owning context.
  Promise.resolve(registry?.settled()).then(() => {
    context?.release();
    background?.retireSubmitted();
  }).catch(console.error);
  audio?.close().catch(console.error);
}
window.addEventListener('pagehide', dispose, {once:true});
