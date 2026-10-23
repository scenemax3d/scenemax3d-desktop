import {initRuntime, createContext} from './effekseer.js';
import init from './proof_runtime.js';
const status = document.querySelector('#status');
let context, starting, handle, failed = false, frames = 0;
let geometryVisible = true;
document.querySelector('#geometry').onclick = event => {
  geometryVisible = !geometryVisible;
  event.target.textContent = geometryVisible ? 'Hide occluder' : 'Show occluder';
};
const report = error => { failed = true; status.textContent = String(error?.stack || error); console.error(error); };
window.addEventListener('error', e => report(e.error || e.message));
window.addEventListener('unhandledrejection', e => report(e.reason));

// Adapt only Effekseer's pipeline descriptors, without changing global WebGPU
// prototypes or Bevy's device/pipelines. The proxy delegates to the SAME device.
function reversedDepthDevice(device) {
  const reverse = {less:'greater', 'less-equal':'greater-equal', greater:'less', 'greater-equal':'less-equal'};
  return new Proxy(device, {get(target, key) {
    if (key === 'createRenderPipeline' || key === 'createRenderPipelineAsync') return descriptor => {
      if (descriptor.depthStencil) descriptor = {...descriptor, depthStencil: {...descriptor.depthStencil,
        depthCompare: reverse[descriptor.depthStencil.depthCompare] || descriptor.depthStencil.depthCompare,
        depthBias: -(descriptor.depthStencil.depthBias || 0),
        depthBiasSlopeScale: -(descriptor.depthStencil.depthBiasSlopeScale || 0)}};
      return target[key](descriptor);
    };
    const value = Reflect.get(target, key, target);
    return typeof value === 'function' ? value.bind(target) : value;
  }});
}

async function start(device, colorFormat) {
  device.addEventListener('uncapturederror', e => report(e.error));
  device.lost.then(info => report(`WebGPU device lost: ${info.message}`));
  const externalDevice = reversedDepthDevice(device);
  await initRuntime({backend:'webgpu', device:externalDevice, scriptPath:'./effekseer-webgpu.js', wasmPath:'./effekseer-webgpu.wasm'});
  // This release otherwise assumes an HTML #canvas even for external drawing.
  // An unused offscreen surface selects its external-only native initialization;
  // all visible drawing still goes directly into Bevy's borrowed render pass.
  const scratch = new OffscreenCanvas(1, 1).getContext('webgpu');
  const ctx = await createContext({backend:'webgpu', device, canvasContext:scratch, colorFormat:navigator.gpu.getPreferredCanvasFormat(), depthFormat:'depth32float', width:800, height:600});
  const effect = await ctx.loadEffect('./Resources/00_Basic/Simple_Sprite_BillBoard.efkefc');
  context = ctx;
  handle = ctx.play(effect, 0, 0, 0);
  handle.setRandomSeed(1);
  handle.setScale(0.35, 0.35, 0.35);
  for (let i = 0; i < 30; i++) ctx.update(1);
  window.proof = {context:ctx, effect, device, get frames(){ return frames; }};
}
globalThis.scenemaxEffekseerProof = {geometry_visible: () => geometryVisible, draw(device, pass, projection, camera, delta, colorFormat) {
  try {
  if (failed) return;
  if (!starting) starting = start(device, colorFormat).catch(report);
  if (!context) return;
  if (!handle?.exists) handle = context.play(window.proof.effect, 0, 0, 0);
  context.setProjectionMatrix(projection);
  context.setCameraMatrix(camera);
  context.update(0);
  context.drawToRenderPass(pass, {colorFormat, depthFormat:'depth32float'});
  frames++;
  status.textContent = `Shared-device frames: ${frames}. Inspect effect occlusion by the blue geometry. No separate canvas.`;
  } catch (error) { report(error); }
}};
await init();
