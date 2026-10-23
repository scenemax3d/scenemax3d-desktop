import {test} from 'node:test';
import assert from 'node:assert/strict';
import {SceneBackground} from './background.mjs';

globalThis.GPUTextureUsage = {RENDER_ATTACHMENT:16};
globalThis.GPUBufferUsage = {UNIFORM:64};
function fixture() {
  const textures=[], buffers=[], groups=[], draws=[];
  let submitted;
  const device={
    queue:{onSubmittedWorkDone:()=>new Promise(resolve=>submitted=resolve)},
    createTexture(d) {
      const t={width:d.size.width,height:d.size.height,descriptor:d,destroyed:false,
        destroy(){this.destroyed=true;},createView(){return {texture:this};}};
      textures.push(t);return t;
    },
    createShaderModule:d=>d,
    createRenderPipeline:()=>({getBindGroupLayout:()=>({})}),
    createBuffer(d){const bytes=new ArrayBuffer(d.size);buffers.push(bytes);return {getMappedRange:()=>bytes,unmap(){}};},
    createBindGroup(d){groups.push(d);return d;},
  };
  const background=new SceneBackground(device);
  let owned;
  const context={setBackgroundImage(data,width,height) {
    owned?.destroy();
    owned=background.createTexture({size:{width,height},format:'rgba8unorm',usage:6});
  }};
  const encoder={beginRenderPass(d){draws.push(d);return {setPipeline(){},setBindGroup(){},draw(){},end(){}};}};
  return {background,context,encoder,textures,buffers,groups,draws,complete:()=>submitted()};
}

test('only the scoped native background allocation gains render attachment usage',()=>{
  const f=fixture();
  f.background.createTexture({size:{width:1,height:1},format:'rgba8unorm',usage:6});
  f.background.capture(f.context,f.encoder,{},[0,0,100,80],'rgba8unorm-srgb');
  assert.equal(f.textures[0].descriptor.usage,6);
  assert.equal(f.textures[1].descriptor.usage,22);
  f.background.capture(f.context,f.encoder,{},[0,0,100,80],'bgra8unorm-srgb');
  assert.equal(f.textures.length,2);
});
test('viewport capture uniforms remain distinct until GPU submission',()=>{
  const f=fixture(), source={};
  f.background.capture(f.context,f.encoder,source,[10,20,100,80],'rgba8unorm');
  f.background.capture(f.context,f.encoder,source,[110,40,100,80],'rgba8unorm');
  assert.deepEqual([...new Uint32Array(f.buffers[0])],[10,20]);
  assert.deepEqual([...new Uint32Array(f.buffers[1])],[110,40]);
  assert.equal(f.groups[0].entries[0].resource,source);
  assert.equal(f.draws.length,2);
});
test('resizing retires native-owned targets only after submitted work completes',async()=>{
  const f=fixture();
  f.background.capture(f.context,f.encoder,{},[0,0,100,80],'rgba8unorm');
  f.background.capture(f.context,f.encoder,{},[0,0,200,160],'rgba8unorm');
  assert.equal(f.textures[0].destroyed,false);
  f.background.retireSubmitted();
  assert.equal(f.textures[0].destroyed,false);
  f.complete();await Promise.resolve();await Promise.resolve();
  assert.equal(f.textures[0].destroyed,true);
  assert.equal(f.textures[1].destroyed,false);
});
test('unsupported HDR does not silently clamp scene color',()=>{
  const f=fixture();
  assert.throws(()=>f.background.capture(f.context,f.encoder,{},[0,0,100,80],'rg11b10ufloat'),/SDR/);
  assert.equal(f.textures.length,0);
});
