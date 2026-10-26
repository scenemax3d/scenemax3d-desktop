//! Browser interoperability gate. See Tests/fixtures/effekseer_web_interop.
#[cfg(not(target_arch = "wasm32"))]
fn main() {}

#[cfg(target_arch = "wasm32")]
fn main() {
    proof::run();
}

#[cfg(target_arch = "wasm32")]
mod proof {
    use bevy::{
        core_pipeline::{Core3d, core_3d::main_transparent_pass_3d, tonemapping::tonemapping},
        prelude::*,
        render::{
            RenderApp,
            render_resource::*,
            renderer::{RenderContext, RenderDevice, ViewQuery},
            view::{ExtractedView, ViewDepthStencilTexture, ViewTarget},
        },
    };
    use wasm_bindgen::prelude::*;

    #[wasm_bindgen]
    extern "C" {
        #[wasm_bindgen(js_namespace = scenemaxEffekseerProof, catch)]
        fn draw(
            device: &JsValue,
            pass: &JsValue,
            projection: &[f32],
            camera: &[f32],
            delta: f32,
            format: &str,
        ) -> Result<(), JsValue>;
        #[wasm_bindgen(js_namespace = scenemaxEffekseerProof)]
        fn geometry_visible() -> bool;
    }

    pub fn run() {
        let mut app = App::new();
        app.add_plugins(DefaultPlugins.set(WindowPlugin {
            primary_window: Some(Window {
                canvas: Some("#game".into()),
                resolution: (800, 600).into(),
                ..default()
            }),
            ..default()
        }))
        .add_systems(Startup, setup)
        .add_systems(Update, controls);
        app.sub_app_mut(RenderApp).add_systems(
            Core3d,
            render.after(main_transparent_pass_3d).before(tonemapping),
        );
        app.run();
    }

    fn controls(mut geometry: Query<&mut Visibility, With<Mesh3d>>) {
        for mut visibility in &mut geometry {
            *visibility = if geometry_visible() {
                Visibility::Visible
            } else {
                Visibility::Hidden
            };
        }
    }

    fn setup(
        mut commands: Commands,
        mut meshes: ResMut<Assets<Mesh>>,
        mut materials: ResMut<Assets<StandardMaterial>>,
    ) {
        commands.spawn((
            Camera3d::default(),
            Msaa::Off,
            Transform::from_xyz(0., 3., 15.).looking_at(Vec3::ZERO, Vec3::Y),
        ));
        // The left half occludes the effect; the right half leaves it visible.
        commands.spawn((
            Mesh3d(meshes.add(Cuboid::new(3., 5., 0.5))),
            MeshMaterial3d(materials.add(StandardMaterial {
                base_color: Color::srgb(0.1, 0.5, 0.8),
                unlit: true,
                ..default()
            })),
            Transform::from_xyz(-1.5, 0., 3.),
        ));
    }

    fn render(
        view: ViewQuery<(&ExtractedView, &ViewTarget, &ViewDepthStencilTexture)>,
        device: Res<RenderDevice>,
        time: Res<Time>,
        mut ctx: RenderContext,
    ) {
        let (view, target, depth) = view.into_inner();
        let Some(device) = device.wgpu_device().browser_device() else {
            return;
        };
        let format = match target.main_texture_format() {
            TextureFormat::Rgba8Unorm => "rgba8unorm",
            TextureFormat::Rgba8UnormSrgb => "rgba8unorm-srgb",
            TextureFormat::Bgra8Unorm => "bgra8unorm",
            TextureFormat::Bgra8UnormSrgb => "bgra8unorm-srgb",
            TextureFormat::Rgba16Float => "rgba16float",
            TextureFormat::Rg11b10Ufloat => "rg11b10ufloat",
            other => {
                error!(?other, "Unsupported proof target format");
                return;
            }
        };
        let pass = ctx
            .command_encoder()
            .begin_render_pass(&RenderPassDescriptor {
                label: Some("SceneMax Effekseer interoperability proof"),
                color_attachments: &[Some(target.get_color_attachment())],
                depth_stencil_attachment: Some(depth.get_attachment(StoreOp::Store)),
                ..default()
            });
        if let Some(raw) = pass.browser_render_pass() {
            let projection = view.clip_from_view.to_cols_array();
            let camera = view.world_from_view.to_matrix().inverse().to_cols_array();
            if let Err(error) = draw(device, raw, &projection, &camera, time.delta_secs(), format) {
                error!(?error, "Effekseer proof failed");
            }
        }
    }
}
