// Adapter for the checksum-pinned 0.1.0 runtime. Its public setBackgroundImage
// owns the native background and binding. Capture only that synchronous texture
// allocation to populate it on the GPU, without frame readback or Wasm uploads.
export class SceneBackground {
  constructor(device) {
    this.device = device;
    this.texture = null;
    this.allocating = false;
    this.retired = [];
  }

  createTexture(descriptor) {
    if (!this.allocating) return this.device.createTexture(descriptor);
    if (this.candidate || descriptor.format !== 'rgba8unorm'
        || (descriptor.sampleCount ?? 1) !== 1) {
      throw Error('Incompatible Effekseer background allocation; update the runtime adapter.');
    }
    const texture = this.device.createTexture({...descriptor,
      usage: descriptor.usage | GPUTextureUsage.RENDER_ATTACHMENT});
    this.candidate = texture;
    // Preserve the actual GPUTexture identity for WebIDL argument validation.
    // Override only this owned object's destroy, never a browser prototype.
    const destroy = texture.destroy.bind(texture);
    texture.destroy = () => this.retired.push(destroy);
    return texture;
  }

  retireSubmitted() {
    const retired = this.retired.splice(0);
    if (retired.length) this.device.queue.onSubmittedWorkDone().catch(() => {}).then(() => {
      for (const destroy of retired) destroy();
    });
  }

  ensure(context, width, height) {
    if (this.texture?.width === width && this.texture?.height === height) return;
    this.candidate = null;
    this.allocating = true;
    try { context.setBackgroundImage(new Uint8Array(width * height * 4), width, height); }
    finally { this.allocating = false; }
    if (!this.candidate || this.candidate.width !== width || this.candidate.height !== height) {
      throw Error('Effekseer did not allocate the expected distortion background.');
    }
    // Lifetime belongs to the native context; do not destroy it independently.
    this.texture = this.candidate;
    this.view = this.texture.createView();
    if (!this.pipeline) {
      const module = this.device.createShaderModule({label:'SceneMax scene-color copy', code:`
        @group(0) @binding(0) var scene: texture_2d<f32>;
        struct Region { origin: vec2<u32> };
        @group(0) @binding(1) var<uniform> region: Region;
        @vertex fn vs(@builtin(vertex_index) i: u32) -> @builtin(position) vec4<f32> {
          let positions = array(vec2(-1.0, -1.0), vec2(3.0, -1.0), vec2(-1.0, 3.0));
          return vec4(positions[i], 0.0, 1.0);
        }
        @fragment fn fs(@builtin(position) p: vec4<f32>) -> @location(0) vec4<f32> {
          return textureLoad(scene, vec2<i32>(p.xy) + vec2<i32>(region.origin), 0);
        }`});
      this.pipeline = this.device.createRenderPipeline({label:'SceneMax scene-color copy',
        layout:'auto', vertex:{module,entryPoint:'vs'},
        fragment:{module,entryPoint:'fs',targets:[{format:'rgba8unorm'}]},
        primitive:{topology:'triangle-list'}});
    }
  }

  capture(context, encoder, source, viewport, format) {
    if (!['rgba8unorm','rgba8unorm-srgb','bgra8unorm','bgra8unorm-srgb'].includes(format)) {
      throw Error('Effekseer distortion requires an SDR camera target. HDR scene-color capture is not supported.');
    }
    const [x,y,width,height] = viewport;
    if (!width || !height) return;
    this.ensure(context, width, height);
    // One immutable uniform per encoded view: queue.writeBuffer on a shared
    // uniform would make multiple cameras sample the final camera's rectangle.
    const region = this.device.createBuffer({size:8, usage:GPUBufferUsage.UNIFORM, mappedAtCreation:true});
    new Uint32Array(region.getMappedRange()).set([x,y]);
    region.unmap();
    const group = this.device.createBindGroup({layout:this.pipeline.getBindGroupLayout(0),
      entries:[{binding:0,resource:source},{binding:1,resource:{buffer:region}}]});
    const pass = encoder.beginRenderPass({label:'SceneMax distortion background',
      colorAttachments:[{view:this.view,loadOp:'clear',storeOp:'store',clearValue:{r:0,g:0,b:0,a:0}}]});
    pass.setPipeline(this.pipeline);
    pass.setBindGroup(0,group);
    pass.draw(3);
    pass.end();
    // WebGPU retains referenced resources until the command buffer completes.
    // Drop JS references; destroy() here would invalidate the pending submission.
  }
}
