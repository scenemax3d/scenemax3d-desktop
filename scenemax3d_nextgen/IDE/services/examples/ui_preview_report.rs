fn main() {
    let args: Vec<_> = std::env::args().collect();
    let source = std::fs::read_to_string(&args[2]).unwrap();
    let scene = scenemax_ide_services::scene::assets::load(std::path::Path::new(&args[1]),&source).unwrap();
    println!("{}: {} widgets, {} images, {} bitmap text nodes, warnings: {:?}", scene.name,scene.widgets.len(),scene.widgets.iter().filter(|w| w.visual.as_ref().is_some_and(|v| !v.text)).count(),scene.widgets.iter().filter(|w| w.visual.as_ref().is_some_and(|v| v.text)).count(),scene.warnings);
}
