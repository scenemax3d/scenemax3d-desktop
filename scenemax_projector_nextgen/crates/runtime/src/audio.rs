use super::*;

pub(super) fn load_audio_index(
    asset_server: &AssetServer,
    asset_root: Option<&Path>,
    builtin_asset_root: Option<&Path>,
) -> HashMap<String, SceneMaxAudioAsset> {
    let mut index = HashMap::new();
    if let Some(builtin_root) = builtin_asset_root {
        load_audio_index_file(
            asset_server,
            &builtin_root.join("audio").join("audio.json"),
            "builtin://",
            &mut index,
        );
        load_audio_index_file(
            asset_server,
            &builtin_root.join("audio").join("audio-ext.json"),
            "builtin://",
            &mut index,
        );
    }
    if let Some(asset_root) = asset_root {
        load_audio_index_file(
            asset_server,
            &asset_root.join("audio").join("audio.json"),
            "",
            &mut index,
        );
        load_audio_index_file(
            asset_server,
            &asset_root.join("audio").join("audio-ext.json"),
            "",
            &mut index,
        );
    }
    index
}

fn load_audio_index_file(
    asset_server: &AssetServer,
    path: &Path,
    asset_prefix: &str,
    index: &mut HashMap<String, SceneMaxAudioAsset>,
) {
    let Ok(text) = fs::read_to_string(path) else {
        return;
    };
    let Ok(root) = serde_json::from_str::<serde_json::Value>(&text) else {
        return;
    };
    let Some(sounds) = root.get("sounds").and_then(serde_json::Value::as_array) else {
        return;
    };
    for sound in sounds {
        let Some(name) = sound.get("name").and_then(serde_json::Value::as_str) else {
            continue;
        };
        let Some(path) = sound.get("path").and_then(serde_json::Value::as_str) else {
            continue;
        };
        let asset_path = format!("{asset_prefix}{}", path.replace('\\', "/"));
        index.insert(
            audio_key(name),
            SceneMaxAudioAsset {
                name: name.to_owned(),
                path: asset_path.clone(),
                handle: asset_server.load(asset_path),
            },
        );
    }
}

pub(super) fn apply_audio_statement(
    audio: &AudioStatement,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
    runtime_assets: &mut SceneMaxRuntimeAssets,
    commands: &mut Commands,
) {
    let Some(sound) = resolve_audio_sound_name(
        audio,
        vars,
        scope,
        guards_by_name,
        transforms_by_name,
        collider_bounds,
    ) else {
        return;
    };
    let key = audio_key(&sound);
    match audio.action {
        AudioAction::Play => play_audio_statement(
            audio,
            &sound,
            &key,
            vars,
            scope,
            guards_by_name,
            transforms_by_name,
            collider_bounds,
            runtime_assets,
            commands,
        ),
        AudioAction::Stop => stop_audio_statement(&sound, &key, runtime_assets, commands),
    }
}

fn play_audio_statement(
    audio: &AudioStatement,
    sound: &str,
    key: &str,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
    runtime_assets: &mut SceneMaxRuntimeAssets,
    commands: &mut Commands,
) {
    let Some(asset) = runtime_assets.audio_by_name.get(key).cloned() else {
        write_runtime_diagnostic_line(format!("AUDIO:MISS action=play name={sound}"));
        return;
    };
    if should_ignore_looped_audio_play(audio.looped, key, runtime_assets) {
        write_runtime_diagnostic_line(format!(
            "AUDIO:PLAY name={} path={} loop=1 ignored=1",
            asset.name, asset.path
        ));
        return;
    }
    let volume = resolved_audio_volume(
        audio.volume.as_ref(),
        vars,
        scope,
        guards_by_name,
        transforms_by_name,
        collider_bounds,
    );
    let settings = if audio.looped {
        PlaybackSettings {
            mode: PlaybackMode::Loop,
            volume,
            ..default()
        }
    } else {
        PlaybackSettings::DESPAWN.with_volume(volume)
    };
    let entity = commands
        .spawn((AudioPlayer(asset.handle.clone()), settings))
        .id();
    if audio.looped {
        runtime_assets
            .looping_audio_by_name
            .insert(key.to_owned(), entity);
    }
    write_runtime_diagnostic_line(format!(
        "AUDIO:PLAY name={} path={} loop={} volume={}",
        asset.name,
        asset.path,
        audio.looped as u8,
        audio_volume_for_log(volume)
    ));
}

fn should_ignore_looped_audio_play(
    looped: bool,
    key: &str,
    runtime_assets: &SceneMaxRuntimeAssets,
) -> bool {
    looped && runtime_assets.looping_audio_by_name.contains_key(key)
}

fn stop_audio_statement(
    sound: &str,
    key: &str,
    runtime_assets: &mut SceneMaxRuntimeAssets,
    commands: &mut Commands,
) {
    if let Some(entity) = runtime_assets.looping_audio_by_name.remove(key) {
        commands.entity(entity).despawn();
        write_runtime_diagnostic_line(format!("AUDIO:STOP name={sound} found=1"));
    } else {
        write_runtime_diagnostic_line(format!("AUDIO:STOP name={sound} found=0"));
    }
}

fn resolve_audio_sound_name(
    audio: &AudioStatement,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> Option<String> {
    if let Some(sound) = audio.sound.as_ref() {
        return (!sound.trim().is_empty()).then(|| sound.trim().to_owned());
    }
    let value = audio.sound_value.as_ref()?;
    let sound = resolve_assignment_value_scoped_with_guards(
        value,
        vars,
        scope,
        guards_by_name,
        transforms_by_name,
        collider_bounds,
    )
    .map(format_scenemax_number)
    .unwrap_or_else(|| assignment_value_fallback_text(value));
    (!sound.trim().is_empty()).then(|| sound.trim().to_owned())
}

fn resolved_audio_volume(
    volume: Option<&AssignmentValue>,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> Volume {
    let value = volume
        .and_then(|value| {
            resolve_assignment_value_scoped_with_guards(
                value,
                vars,
                scope,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
            )
        })
        .unwrap_or(100.0)
        .clamp(0.0, 100.0);
    Volume::Linear(value / 100.0)
}

fn audio_key(name: &str) -> String {
    name.trim().to_ascii_lowercase()
}

fn audio_volume_for_log(volume: Volume) -> String {
    match volume {
        Volume::Linear(value) => format_scenemax_number(value * 100.0),
        Volume::Decibels(value) => format!("{}db", format_scenemax_number(value)),
    }
}

#[cfg(test)]
mod audio_tests {
    use super::*;

    #[test]
    fn resolves_unquoted_audio_symbol_as_resource_name_when_not_a_number() {
        let audio = AudioStatement {
            action: AudioAction::Play,
            sound: None,
            sound_value: Some(AssignmentValue::Symbol("kick1".to_owned())),
            looped: false,
            volume: None,
        };
        let vars = SceneMaxVars::default();
        let guards = HashMap::new();

        assert_eq!(
            resolve_audio_sound_name(&audio, &vars, None, &guards, None, None).as_deref(),
            Some("kick1")
        );
    }

    #[test]
    fn ignores_looped_play_when_same_audio_is_already_looping() {
        let mut runtime_assets = SceneMaxRuntimeAssets::default();
        runtime_assets
            .looping_audio_by_name
            .insert(audio_key("Neon_Dojo1"), Entity::PLACEHOLDER);

        assert!(should_ignore_looped_audio_play(
            true,
            &audio_key("neon_dojo1"),
            &runtime_assets
        ));
        assert!(!should_ignore_looped_audio_play(
            false,
            &audio_key("neon_dojo1"),
            &runtime_assets
        ));
        assert!(!should_ignore_looped_audio_play(
            true,
            &audio_key("other_track"),
            &runtime_assets
        ));
    }
}
