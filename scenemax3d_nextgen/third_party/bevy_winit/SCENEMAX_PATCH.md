# SceneMax Windows background updates

This is Bevy `bevy_winit` 0.20.0, patched through the workspace Cargo manifest.

Windows can withhold redraw events for covered or minimized windows even when
the Bevy `Window.visible` flag remains true. A reactive background timer must
therefore be allowed to update the application without a preceding redraw.
Otherwise simulation and network polling can wait until the window gains focus.

The patch in `src/state.rs` honors expired reactive timers on unfocused Windows
windows, with at most one timer-driven update per event-loop wakeup. Focused
continuous rendering keeps its existing behavior. The runtime uses
`WinitSettings::game()` to request a 60 Hz background timer.

Validation:

- `cargo test -p bevy_winit --lib`
- `python Tests/background_multiplayer.py`
- `python Tests/background_multiplayer.py --minimize-delay` allows 30 seconds
  to minimize the small sender window before the fullscreen peer sends traffic.
  Do not restore the sender until the test exits. The fixture checks replies
  and replicated position while the sender remains in the background.

Revisit this patch when upgrading Bevy; do not discard the timeout handling
without verifying covered and minimized native multiplayer clients.
