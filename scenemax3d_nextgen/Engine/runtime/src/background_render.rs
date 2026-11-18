//! Keep the native simulation live when presentation is throttled by the driver.
use bevy::{
    app::AppExit,
    ecs::{message::MessageUpdateSystems, schedule::MainThreadExecutor},
    prelude::*,
    render::pipelined_rendering::{RenderAppChannels, RenderExtractApp},
    time::TimeReceiver,
};
use std::{future::Future, pin::pin, task::{Context, Poll, Waker}};
use std::{io::Read, path::Path, time::{Duration, Instant}};

pub(super) struct BackgroundRenderPlugin;

#[derive(Resource)]
struct RenderProgress(bool);

#[derive(Resource)]
struct FrameInterval(Duration);

// The same entry-script metadata convention used by exported network settings.
// No limit is imposed on projects that do not request one.
pub(super) fn configure_frame_limit(app: &mut App, project: Option<&Path>, script: Option<&Path>) {
    let mut configured = None;
    for path in project.map(|p| p.join("scripts/main")).into_iter()
        .chain(script.map(Path::to_path_buf)) {
        if let Ok(file) = std::fs::File::open(path) {
            let mut source = String::new();
            if file.take(16384).read_to_string(&mut source).is_ok() {
                for line in source.lines() {
                    if let Some(value) = line.trim().strip_prefix("//$[max_fps]=") {
                        configured = Some(value.trim_end_matches(';').trim().to_owned());
                    }
                }
            }
        }
    }
    let configured = std::env::var("SCENEMAX_MAX_FPS").ok().or(configured);
    if let Some(interval) = configured.as_deref().and_then(frame_interval) {
        if let Some(render) = app.get_sub_app_mut(bevy::render::RenderApp) {
            render.insert_resource(FrameInterval(interval));
            crate::write_runtime_diagnostic_line(format!("RENDER max_fps={:.1}", 1.0 / interval.as_secs_f64()));
        }
    }
}

fn frame_interval(value: &str) -> Option<Duration> {
    let fps = value.trim().parse::<f64>().ok()?;
    (fps.is_finite() && (1.0..=1000.0).contains(&fps))
        .then(|| Duration::from_secs_f64(1.0 / fps))
}

fn pace_render(limit: Option<Res<FrameInterval>>, mut previous: Local<Option<Instant>>) {
    let Some(limit) = limit else { return };
    if let Some(previous) = *previous {
        if let Some(wait) = limit.0.checked_sub(previous.elapsed()) {
            std::thread::sleep(wait);
        }
    }
    *previous = Some(Instant::now());
}

impl Plugin for BackgroundRenderPlugin {
    fn build(&self, app: &mut App) {
        if let Some(render) = app.get_sub_app_mut(bevy::render::RenderApp) {
            render.add_systems(bevy::render::Render,
                (pace_render, report_render_progress).chain().after(bevy::render::RenderSystems::Cleanup));
        }
        let Some(extract) = app.get_sub_app_mut(RenderExtractApp) else { return };
        let mut original = extract.take_extract();
        extract.set_extract(move |main, render| {
            let focused = main.query::<&Window>().iter(main).any(|w| w.focused);
            if focused || !main.contains_resource::<RenderAppChannels>() {
                if let Some(extract) = original.as_mut() { extract(main, render); }
                main.resource_mut::<RenderProgress>().0 = true;
            } else {
                let advanced = extract_if_ready(main);
                main.resource_mut::<RenderProgress>().0 = advanced;
            }
        });
        app.insert_resource(RenderProgress(true));
        // Extraction consumes asset/window messages too. Keep their buffers alive
        // across skipped renders; simulation readers still advance their cursors.
        app.configure_sets(First, MessageUpdateSystems.run_if(render_advanced));
    }

    fn cleanup(&self, app: &mut App) {
        if app.world().contains_resource::<RenderProgress>() {
            // Rendering no longer clocks the simulation. Automatic Time now uses
            // the main thread's monotonic clock, even between completed renders.
            // Bevy's render sender explicitly permits a disconnected receiver.
            app.world_mut().remove_resource::<TimeReceiver>();
        }
    }
}

fn report_render_progress(mut sample: Local<Option<(std::time::Instant, u32)>>) {
    let (since, frames) = sample.get_or_insert_with(|| (std::time::Instant::now(), 0));
    *frames += 1;
    let elapsed = since.elapsed().as_secs_f32();
    if elapsed >= 1.0 {
        crate::write_runtime_diagnostic_line(format!("RENDER fps={:.1} elapsed={elapsed:.3}", *frames as f32 / elapsed));
        *since = std::time::Instant::now();
        *frames = 0;
    }
}

fn render_advanced(progress: Res<RenderProgress>) -> bool { progress.0 }

fn extract_if_ready(world: &mut World) -> bool {
    // A render task can request work on the main thread. Service it without
    // waiting for presentation or GPU fences to complete.
    if let Some(executor) = world.get_resource::<MainThreadExecutor>() {
        if let Some(ticker) = executor.0.ticker() {
            for _ in 0..64 {
                if !ticker.try_tick() { break; }
            }
        }
    }
    world.resource_scope(|world, mut channels: Mut<RenderAppChannels>| {
        let ready = {
            let mut receive = pin!(channels.recv());
            receive.as_mut().poll(&mut Context::from_waker(Waker::noop()))
        };
        match ready {
            Poll::Ready(Some(mut render)) => {
                render.extract(world);
                channels.send_blocking(render);
                true
            }
            Poll::Ready(None) => {
                world.write_message(AppExit::error());
                true
            }
            Poll::Pending => false,
        }
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use bevy::{app::SubApp, ecs::message::{MessageCursor, Messages}};

    #[test]
    fn frame_limit_rejects_invalid_values_and_converts_valid_rates() {
        for value in ["0", "-1", "NaN", "inf", "1001", "text"] {
            assert!(frame_interval(value).is_none(), "{value}");
        }
        assert_eq!(frame_interval(" 25 "), Some(Duration::from_millis(40)));
    }

    #[derive(Resource, Default)]
    struct Samples(Vec<u32>);
    #[derive(Resource, Default)]
    struct SimulationTick(u32);
    #[derive(Message)]
    struct AssetChanged;

    #[test]
    fn busy_renderer_does_not_block_simulation_and_next_extract_sees_latest_state() {
        let mut app = App::new();
        app.add_plugins(MinimalPlugins);
        let mut extract = SubApp::new();
        extract.set_extract(|_, _| panic!("background used blocking extraction"));
        app.insert_sub_app(RenderExtractApp, extract);
        app.add_plugins(BackgroundRenderPlugin);
        app.world_mut().spawn(Window { focused: false, ..default() });
        app.init_resource::<SimulationTick>().init_resource::<Samples>();
        app.add_systems(Update, |mut tick: ResMut<SimulationTick>| tick.0 += 1);
        app.add_message::<AssetChanged>();
        let (to_render, from_main) = async_channel::bounded(1);
        let (to_main, from_render) = async_channel::bounded(1);
        app.insert_resource(RenderAppChannels::new(to_render, from_render, MainThreadExecutor::new()));
        app.finish();
        app.cleanup();
        app.update();
        app.world_mut().write_message(AssetChanged);
        for _ in 0..10 { app.update(); }
        assert_eq!(app.world().resource::<SimulationTick>().0, 11);
        let mut cursor = MessageCursor::<AssetChanged>::default();
        assert_eq!(cursor.read(app.world().resource::<Messages<AssetChanged>>()).count(), 1,
                   "asset messages must survive missed renders");
        let mut render = SubApp::new();
        render.set_extract(|main, _| {
            let tick = main.resource::<SimulationTick>().0;
            main.resource_mut::<Samples>().0.push(tick);
        });
        to_main.send_blocking(render).unwrap();
        app.update();
        assert_eq!(app.world().resource::<Samples>().0, [12]);
        // Model completion of the final render before orderly channel shutdown.
        to_main.send_blocking(from_main.try_recv().unwrap()).unwrap();
    }

    #[test]
    fn simulation_clock_does_not_depend_on_render_completion() {
        let mut app = App::new();
        app.add_plugins(MinimalPlugins);
        app.insert_sub_app(RenderExtractApp, SubApp::new());
        app.add_plugins(BackgroundRenderPlugin);
        let (_, receive) = bevy::time::create_time_channels();
        app.insert_resource(receive);
        app.finish();
        app.cleanup();
        assert!(!app.world().contains_resource::<TimeReceiver>());
    }
}
