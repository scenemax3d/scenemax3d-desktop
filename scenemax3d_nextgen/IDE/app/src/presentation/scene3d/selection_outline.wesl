import bevy_ui_render::ui_vertex_output::UiVertexOutput;

@group(1) @binding(0) var mask: texture_2d<f32>;

// Work in physical pixels, so zooming never changes the outline thickness.
@fragment
fn fragment(in: UiVertexOutput) -> @location(0) vec4<f32> {
    let size = vec2<i32>(textureDimensions(mask));
    let pixel = clamp(vec2<i32>(in.uv * vec2<f32>(size)), vec2<i32>(0), size - 1);
    let center = textureLoad(mask, pixel, 0).a;
    var edge = 0.0;
    for (var y = -3; y <= 3; y++) {
        for (var x = -3; x <= 3; x++) {
            if (x * x + y * y <= 9) {
                let sample_pixel = clamp(pixel + vec2<i32>(x, y), vec2<i32>(0), size - 1);
                edge = max(edge, textureLoad(mask, sample_pixel, 0).a);
            }
        }
    }
    return vec4<f32>(1.0, 0.62, 0.04, clamp(edge - center, 0.0, 1.0));
}
