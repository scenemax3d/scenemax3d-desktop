// Original SceneMax editor shader. No finite world mesh or moving grid origin.
import bevy_pbr::render::mesh_view_bindings::view;

struct Material { color: vec4<f32> };
@group(constants::MATERIAL_BIND_GROUP) @binding(0) var<uniform> material: Material;
struct VertexOut {
    @builtin(position) clip: vec4<f32>,
    @location(0) ndc: vec2<f32>,
};
@vertex
fn vertex(@location(0) position: vec3<f32>) -> VertexOut {
    var out: VertexOut;
    out.clip = vec4<f32>(position.xy, 0.5, 1.0);
    out.ndc = position.xy;
    return out;
}
struct FragmentOut {
    @location(0) color: vec4<f32>,
    @builtin(frag_depth) depth: f32,
};
fn lines(p: vec2<f32>, footprint: vec2<f32>, spacing: f32) -> f32 {
    let width = max(footprint / spacing, vec2<f32>(0.000001));
    let cell = abs(fract(p / spacing - 0.5) - 0.5) / width;
    let coverage = 1.0 - smoothstep(0.35, 1.15, min(cell.x, cell.y));
    return coverage * (1.0 - smoothstep(0.2, 0.7, max(width.x,width.y)));
}
@fragment
fn fragment(in: VertexOut) -> FragmentOut {
    // Bevy uses reverse-Z: near=1. Using a nonzero far depth also works
    // with infinite perspective projection, where depth zero is at infinity.
    let near_h = view.world_from_clip * vec4<f32>(in.ndc,1.0,1.0);
    let far_h = view.world_from_clip * vec4<f32>(in.ndc,0.0001,1.0);
    let near = near_h.xyz / near_h.w;
    let far = far_h.xyz / far_h.w;
    let ray = far - near;
    let safe_y = select(-0.000001,0.000001,ray.y >= 0.0);
    let t = -near.y / select(safe_y,ray.y,abs(ray.y)>0.000001);
    let world = near + t * ray;
    let p = world.xz;
    // Derivatives must be evaluated before nonuniform discard branches.
    let footprint = max(fwidth(p),vec2<f32>(0.000001));
    let projected = view.clip_from_world * vec4<f32>(world,1.0);
    let depth = projected.z / projected.w;
    let height = max(abs(view.world_position.y),0.001);
    let lod = clamp(log2(height * 0.3) / log2(10.0),-3.0,8.0);
    let spacing = pow(10.0,floor(lod));
    let blend = fract(lod);
    let fine = mix(lines(p,footprint,spacing),lines(p,footprint,spacing*10.0),blend);
    let major = mix(lines(p,footprint,spacing*10.0),lines(p,footprint,spacing*100.0),blend);
    let distance = length(world-view.world_position);
    let fade = 1.0-smoothstep(height*12.0,height*65.0,distance);
    let horizon = smoothstep(0.003,0.035,abs(normalize(ray).y));
    let x_axis = 1.0-smoothstep(0.6,1.5,abs(p.y)/footprint.y);
    let z_axis = 1.0-smoothstep(0.6,1.5,abs(p.x)/footprint.x);
    var rgb = material.color.rgb;
    rgb = mix(rgb,vec3<f32>(0.65,0.12,0.10),x_axis);
    rgb = mix(rgb,vec3<f32>(0.10,0.24,0.70),z_axis);
    let alpha = max(fine*0.24+major*0.25,max(x_axis,z_axis)*0.75)*fade*horizon;
    if t <= 0.0 || abs(ray.y)<0.000001 || depth<0.0 || depth>1.0 || alpha<0.001 { discard; }
    var out: FragmentOut;
    out.color = vec4<f32>(rgb,alpha);
    out.depth = depth;
    return out;
}
