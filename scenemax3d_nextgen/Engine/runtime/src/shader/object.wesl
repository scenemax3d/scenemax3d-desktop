import bevy_pbr::render::forward_io::VertexOutput;
@group(constants::MATERIAL_BIND_GROUP) @binding(0) var<uniform> main_color:vec4<f32>;
@group(constants::MATERIAL_BIND_GROUP) @binding(1) var<uniform> parameters:vec4<f32>;
@group(constants::MATERIAL_BIND_GROUP) @binding(2) var<uniform> motion:vec4<f32>;
@group(constants::MATERIAL_BIND_GROUP) @binding(3) var source_texture:texture_2d<f32>;
@group(constants::MATERIAL_BIND_GROUP) @binding(4) var source_sampler:sampler;
@group(constants::MATERIAL_BIND_GROUP) @binding(5) var<uniform> local_from_world:mat4x4<f32>;
fn enabled(bit:u32)->bool {return (u32(motion.z)&bit)!=0u;}
fn noise(p:vec2<f32>)->f32 {return fract(sin(dot(p,vec2<f32>(127.1,311.7)))*43758.5453123);}
@fragment
fn fragment(in:VertexOutput)->@location(0) vec4<f32> {
    var uv=vec2<f32>(0.0);
@if(VERTEX_UVS_A) {
    uv=in.uv;
}
    let time=motion.y;
    if enabled(32u) {uv+=time*motion.x*vec2<f32>(0.08,0.03);}
    if enabled(128u) {
        uv.y+=sin(uv.x*12.0+time*1.8*max(motion.x,0.15))*0.03;
        uv.x+=cos(uv.y*10.0+time*1.4*max(motion.x,0.15))*0.02;
    }
    var base=textureSample(source_texture,source_sampler,fract(uv));
@if(VERTEX_COLORS) {
    base*=in.color;
}
    if enabled(1u) {base=vec4<f32>(base.rgb*main_color.rgb,base.a);}
    base.a*=main_color.a;
    var pulse=1.0;
    if enabled(4u) {pulse=0.72+0.28*(0.5+0.5*sin(time*parameters.y*6.28318));}
    var flicker=1.0;
    if enabled(64u) {flicker=0.82+0.18*noise(vec2<f32>(floor(time*12.0),floor(uv.y*32.0)));}
    if enabled(256u) {base=vec4<f32>(base.rgb+main_color.rgb*(0.55+0.45*sin(uv.y*90.0-time*7.0))*0.18,base.a);}
    // Toon bands and rim intensity follow each mesh world normal.
    if enabled(512u) {let lit=floor((dot(normalize(in.world_normal),normalize(vec3<f32>(0.25,0.75,0.6)))*0.5+0.5)*4.0)/4.0;base=vec4<f32>(base.rgb*mix(0.55,1.0,lit),base.a);}
    var rgb=base.rgb*pulse*flicker;
    if enabled(2u) {rgb+=main_color.rgb*parameters.x*(0.35+pulse*0.65);}
    if enabled(16u) {let rim=pow(1.0-abs(normalize(in.world_normal).z),2.2);rgb+=main_color.rgb*rim*(0.15+parameters.x*0.25);}
    var alpha=clamp(base.a*(1.0-parameters.z),0.0,1.0);
    if enabled(8u) {
        let n=noise(floor((uv+(local_from_world*in.world_position).xy*0.3)*24.0)+floor(time*1.5));
        let cut=1.0-parameters.z;
        let edge=smoothstep(cut-parameters.w,cut,n)-smoothstep(cut,cut+parameters.w,n);
        if n>cut {discard;}
        alpha=base.a;
        rgb+=vec3<f32>(1.0,0.85,0.45)*edge*(0.35+parameters.x*0.5);
    }
    return vec4<f32>(rgb,alpha);
}
