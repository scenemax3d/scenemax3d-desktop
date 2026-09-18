use std::{env, fs, path::PathBuf};
fn main() {
    let out = PathBuf::from(env::var_os("OUT_DIR").unwrap());
    let marker = out
        .ancestors()
        .nth(3)
        .unwrap()
        .join("scenemax_projector_nextgen.effekseer_native");
    if env::var_os("CARGO_FEATURE_EFFEKSEER_NATIVE").is_some() {
        fs::write(marker, b"effekseer_native=1\n").expect("Write runtime capability marker");
    } else if marker.exists() {
        fs::remove_file(marker).expect("Remove stale runtime capability marker");
    }
}
