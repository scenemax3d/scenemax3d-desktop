use scenemax_runtime::{PairingStatus, PartialCodeInput, PartialCodeSender};
use std::{sync::mpsc, thread};

pub(super) fn install(
    game: PartialCodeSender,
    input: PartialCodeInput,
    pair_on_launch: bool,
) -> PartialCodeInput {
    let (status, messages) = mpsc::channel();
    let (start, requests) = mpsc::sync_channel(0);
    thread::spawn(move || {
        let mut launch = pair_on_launch;
        while launch || requests.recv().is_ok() {
            launch = false;
            let result = scenemax_game_mcp::run_game(
                |code| {
                    let event = if code.is_empty() {
                        PairingStatus::Paired
                    } else {
                        eprintln!("Game MCP pairing code: {code}");
                        PairingStatus::Code(code.to_owned())
                    };
                    let _ = status.send(event);
                },
                |code| game.run_partial_code(code, None, false).is_ok(),
            );
            let message = match result {
                Ok(()) => PairingStatus::Closed,
                Err(error) => {
                    PairingStatus::Error(format!("Game MCP: {error}. Press Ctrl+F12 to retry."))
                }
            };
            let _ = status.send(message);
        }
    });
    input.with_pairing_ui(start, messages)
}
