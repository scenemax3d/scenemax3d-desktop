//! Bundled artwork exported from the Java IDE; no Java runtime is required.
use bevy::prelude::*;
#[derive(Component)]
pub(crate) struct JavaIcon(pub &'static str);
pub(crate) fn register(app: &mut App) {
    bevy::asset::embedded_asset!(app, "java_icons/createBevyShaderDesignerIcon.png");
    bevy::asset::embedded_asset!(app, "java_icons/createDesignerIcon.png");
    bevy::asset::embedded_asset!(app, "java_icons/createEffekseerDesignerIcon.png");
    bevy::asset::embedded_asset!(app, "java_icons/createEnvironmentShaderDesignerIcon.png");
    bevy::asset::embedded_asset!(app, "java_icons/createIKDesignerIcon.png");
    bevy::asset::embedded_asset!(app, "java_icons/createJavaIcon.png");
    bevy::asset::embedded_asset!(app, "java_icons/createMaterialDesignerIcon.png");
    bevy::asset::embedded_asset!(app, "java_icons/createShaderDesignerIcon.png");
    bevy::asset::embedded_asset!(app, "java_icons/createSkyboxDesignerIcon.png");
    bevy::asset::embedded_asset!(app, "java_icons/createThrowMotionDesignerIcon.png");
    bevy::asset::embedded_asset!(app, "java_icons/createUIDesignerIcon.png");
    bevy::asset::embedded_asset!(app, "java_icons/createWeaponDesignerIcon.png");
    bevy::asset::embedded_asset!(app, "java_icons/csharp.png");
    bevy::asset::embedded_asset!(app, "java_icons/folder.png");
    bevy::asset::embedded_asset!(app, "java_icons/main.png");
    bevy::asset::embedded_asset!(app, "java_icons/script.png");
    bevy::asset::embedded_asset!(app, "java_icons/toolbar_ambientlight.png");
    bevy::asset::embedded_asset!(app, "java_icons/toolbar_arch.png");
    bevy::asset::embedded_asset!(app, "java_icons/toolbar_box.png");
    bevy::asset::embedded_asset!(app, "java_icons/toolbar_cinematic.png");
    bevy::asset::embedded_asset!(app, "java_icons/toolbar_cone.png");
    bevy::asset::embedded_asset!(app, "java_icons/toolbar_copy.png");
    bevy::asset::embedded_asset!(app, "java_icons/toolbar_cylinder.png");
    bevy::asset::embedded_asset!(app, "java_icons/toolbar_delete.png");
    bevy::asset::embedded_asset!(app, "java_icons/toolbar_designlights.png");
    bevy::asset::embedded_asset!(app, "java_icons/toolbar_hollowcylinder.png");
    bevy::asset::embedded_asset!(app, "java_icons/toolbar_light.png");
    bevy::asset::embedded_asset!(app, "java_icons/toolbar_model.png");
    bevy::asset::embedded_asset!(app, "java_icons/toolbar_orbit.png");
    bevy::asset::embedded_asset!(app, "java_icons/toolbar_pan.png");
    bevy::asset::embedded_asset!(app, "java_icons/toolbar_paste.png");
    bevy::asset::embedded_asset!(app, "java_icons/toolbar_path.png");
    bevy::asset::embedded_asset!(app, "java_icons/toolbar_quad.png");
    bevy::asset::embedded_asset!(app, "java_icons/toolbar_rotate.png");
    bevy::asset::embedded_asset!(app, "java_icons/toolbar_sphere.png");
    bevy::asset::embedded_asset!(app, "java_icons/toolbar_stairs.png");
    bevy::asset::embedded_asset!(app, "java_icons/toolbar_translate.png");
    bevy::asset::embedded_asset!(app, "java_icons/toolbar_wedge.png");
}
pub(crate) fn load(
    mut commands: Commands,
    icons: Query<(Entity, &JavaIcon), Added<JavaIcon>>,
    server: Option<Res<AssetServer>>,
) {
    let Some(server) = server else {
        return;
    };
    for (entity, icon) in &icons {
        commands
            .entity(entity)
            .insert(ImageNode::new(server.load(format!(
                "embedded://scenemax_ide/presentation/java_icons/{}.png",
                icon.0
            ))));
    }
}
pub(crate) fn file(name: &str, directory: bool) -> &'static str {
    if directory {
        return "folder";
    }
    if name == "main" {
        return "main";
    }
    let name = name.to_ascii_lowercase();
    if name.ends_with(".ik.json") {
        return "createIKDesignerIcon";
    }
    match name.rsplit('.').next().unwrap_or("") {
        "smdesign" => "createDesignerIcon",
        "smui" => "createUIDesignerIcon",
        "smskybox" => "createSkyboxDesignerIcon",
        "smeffectdesign" => "createEffekseerDesignerIcon",
        "bvshader" => "createBevyShaderDesignerIcon",
        "smshader" => "createShaderDesignerIcon",
        "smenvshader" => "createEnvironmentShaderDesignerIcon",
        "mat" => "createMaterialDesignerIcon",
        "smweapon" => "createWeaponDesignerIcon",
        "smmotion" => "createThrowMotionDesignerIcon",
        "smik" => "createIKDesignerIcon",
        "cs" => "csharp",
        "java" => "createJavaIcon",
        _ => "script",
    }
}
pub(crate) fn toolbar(key: &str) -> Option<&'static str> {
    Some(match key {
        "SPHERE" => "toolbar_sphere",
        "BOX" => "toolbar_box",
        "WEDGE" => "toolbar_wedge",
        "CYLINDER" => "toolbar_cylinder",
        "CONE" => "toolbar_cone",
        "HOLLOW_CYLINDER" => "toolbar_hollowcylinder",
        "QUAD" => "toolbar_quad",
        "STAIRS" => "toolbar_stairs",
        "ARCH" => "toolbar_arch",
        "MODEL" => "toolbar_model",
        "LIGHT" => "toolbar_light",
        "PATH" => "toolbar_path",
        "CINEMATIC_RIG" => "toolbar_cinematic",
        "delete" => "toolbar_delete",
        "copy" => "toolbar_copy",
        "paste" => "toolbar_paste",
        "move" => "toolbar_translate",
        "rotate" => "toolbar_rotate",
        "orbit" => "toolbar_orbit",
        "pan" => "toolbar_pan",
        "lights" => "toolbar_designlights",
        "ambient" => "toolbar_ambientlight",
        _ => return None,
    })
}
