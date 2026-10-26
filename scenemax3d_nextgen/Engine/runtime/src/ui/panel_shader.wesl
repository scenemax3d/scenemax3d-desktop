import bevy_ui_render::ui_vertex_output::UiVertexOutput;
@group(1) @binding(0) var<uniform> color: vec4<f32>;
@group(1) @binding(1) var source_texture: texture_2d<f32>;
@group(1) @binding(2) var source_sampler: sampler;
@group(1) @binding(3) var<uniform> parameters: vec4<f32>;
@group(1) @binding(4) var<uniform> motion: vec4<f32>;
@group(1) @binding(5) var<uniform> uv_rect: vec4<f32>;
@group(1) @binding(6) var<uniform> main_color: vec4<f32>;
fn enabled(bit:u32)->bool {return (u32(motion.z)&bit)!=0u;}
fn noise(p:vec2<f32>)->f32 {return fract(sin(dot(p,vec2<f32>(127.1,311.7)))*43758.5453123);}
@fragment
fn fragment(in:UiVertexOutput)->@location(0) vec4<f32> {
    var uv=in.uv;
    let time=motion.y;
    if enabled(32u) {uv+=time*motion.x*vec2<f32>(0.08,0.03);}
    if enabled(128u) {
        uv.y+=sin(uv.x*12.0+time*1.8*max(motion.x,0.15))*0.03;
        uv.x+=cos(uv.y*10.0+time*1.4*max(motion.x,0.15))*0.02;
    }
    // Repeat inside this source region, never bleed into adjacent atlas glyphs.
    var local_uv=uv;
    if enabled(32u)||enabled(128u) {local_uv=fract(uv);}
    let source_uv=mix(uv_rect.xy,uv_rect.zw,local_uv);
    let sample_color=textureSample(source_texture,source_sampler,source_uv);
    var base=color*sample_color;
    if motion.w>0.5 {base=vec4<f32>(color.rgb,color.a*sample_color.a);}
    var pulse=1.0;
    if enabled(4u) {pulse=0.72+0.28*(0.5+0.5*sin(time*parameters.y*6.28318));}
    var flicker=1.0;
    if enabled(64u) {flicker=0.82+0.18*noise(vec2<f32>(floor(time*12.0),floor(uv.y*32.0)));}
    if enabled(256u) {base=vec4<f32>(base.rgb+main_color.rgb*(0.55+0.45*sin(uv.y*90.0-time*7.0))*0.18,base.a);}
    // UI quads face the camera with a +Z normal, as in the reference geometry.
    if enabled(512u) {let lit=floor((normalize(vec3<f32>(0.25,0.75,0.6)).z*0.5+0.5)*4.0)/4.0;base=vec4<f32>(base.rgb*mix(0.55,1.0,lit),base.a);}
    var rgb=base.rgb*pulse*flicker;
    if enabled(2u) {rgb+=main_color.rgb*parameters.x*(0.35+pulse*0.65);}
    if enabled(8u) {
        let n=noise(floor((uv+in.uv*0.3)*24.0)+floor(time*1.5));
        let cut=1.0-parameters.z;
        let edge=smoothstep(cut-parameters.w,cut,n)-smoothstep(cut,cut+parameters.w,n);
        if n>cut {discard;}
        rgb+=vec3<f32>(1.0,0.85,0.45)*edge*(0.35+parameters.x*0.5);
    }
    return vec4<f32>(rgb,base.a);
}
