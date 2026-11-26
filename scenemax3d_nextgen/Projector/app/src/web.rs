//! Browser bootstrap. Game files are mounted before the shared runtime starts.
use wasm_bindgen::prelude::*;

#[wasm_bindgen]
pub fn mount_file(path: &str, bytes: &[u8]) -> Result<(), JsValue> {
    scenemax_assets::filesystem::mount(path, bytes.to_vec())
        .map_err(|e| JsValue::from_str(&e.to_string()))
}

#[wasm_bindgen]
pub fn mount_alias(path: &str, source: &str) -> Result<(), JsValue> {
    scenemax_assets::filesystem::mount_alias(path, source)
        .map_err(|e| JsValue::from_str(&e.to_string()))
}

#[wasm_bindgen]
pub fn read_file(path: &str) -> Result<Vec<u8>, JsValue> {
    scenemax_assets::filesystem::read(path).map_err(|e| JsValue::from_str(&e.to_string()))
}

thread_local! {
    static GAME_INPUT: std::cell::RefCell<Option<scenemax_runtime::PartialCodeSender>> = const { std::cell::RefCell::new(None) };
}

#[wasm_bindgen]
pub async fn run_partial_code(code: String) -> Result<(), JsValue> {
    let sender = GAME_INPUT
        .with(|input| input.borrow().clone())
        .ok_or_else(|| JsValue::from_str("projector unavailable"))?;
    sender
        .submit_async(code)
        .await
        .map_err(|err| JsValue::from_str(&err))
}

#[wasm_bindgen]
pub fn start_game(entry: &str, gateway: &str) -> Result<(), JsValue> {
    console_error_panic_hook::set_once();
    if !gateway.is_empty() {
        scenemax_multiplayer::client::web_socket::configure(gateway)
            .map_err(|e| JsValue::from_str(&e))?;
    }
    let entry = std::path::PathBuf::from(entry);
    if !entry.starts_with("game/scripts")
        || entry
            .components()
            .any(|c| matches!(c, std::path::Component::ParentDir))
    {
        return Err(JsValue::from_str("Invalid game entry point"));
    }
    let (sender, input) = scenemax_runtime::partial_code_channel();
    GAME_INPUT.with(|slot| slot.replace(Some(sender)));
    let _ = scenemax_runtime::run_bevy_projector_with_input(
        scenemax_runtime::ProjectorLaunch {
            script: Some(entry),
            project_root: Some("game".into()),
            window: scenemax_runtime::WindowSettings {
                width: 1280,
                height: 720,
            },
        },
        Some(input),
    );
    Ok(())
}
