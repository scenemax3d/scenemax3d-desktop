use super::*;

pub(super) fn find_builtin_resources_root(
    project_root: Option<&Path>,
    script_root: Option<&Path>,
    active_asset_root: Option<&Path>,
) -> Option<PathBuf> {
    let active_asset_root = active_asset_root.and_then(canonicalize_existing);
    let mut candidates = Vec::new();

    if let Ok(path) = env::var("SCENEMAX_BUILTIN_RESOURCES") {
        candidates.push(PathBuf::from(path));
    }
    if let Ok(current_dir) = env::current_dir() {
        add_resources_ancestor_candidates(&current_dir, &mut candidates);
    }
    if let Ok(current_exe) = env::current_exe()
        && let Some(parent) = current_exe.parent()
    {
        add_resources_ancestor_candidates(parent, &mut candidates);
    }
    if let Some(project_root) = project_root {
        add_resources_ancestor_candidates(project_root, &mut candidates);
    }
    if let Some(script_root) = script_root {
        add_resources_ancestor_candidates(script_root, &mut candidates);
    }

    let mut seen = HashSet::new();
    candidates.into_iter().find(|candidate| {
        let Some(canonical) = canonicalize_existing(candidate) else {
            return false;
        };
        if !seen.insert(canonical.clone()) {
            return false;
        }
        if active_asset_root.as_ref() == Some(&canonical) {
            return false;
        }
        canonical.join("Models").is_dir() || canonical.join("models").is_dir()
    })
}

pub(super) fn add_resources_ancestor_candidates(base: &Path, candidates: &mut Vec<PathBuf>) {
    for ancestor in base.ancestors() {
        candidates.push(ancestor.join("resources"));
    }
}

pub(super) fn canonicalize_existing(path: impl AsRef<Path>) -> Option<PathBuf> {
    path.as_ref().canonicalize().ok()
}

pub(super) fn initialize_runtime_logger(project_root: Option<&Path>, script_root: Option<&Path>) {
    let base = project_root
        .or(script_root)
        .map(Path::to_path_buf)
        .unwrap_or_else(|| std::env::current_dir().unwrap_or_else(|_| PathBuf::from(".")));
    let path = base.join("scenemax-nextgen-runtime.log");
    if let Some(parent) = path.parent() {
        let _ = fs::create_dir_all(parent);
    }
    let _ = fs::remove_file(&path);
    if let Ok(mut log_file) = SCENEMAX_RUNTIME_LOG_FILE.lock() {
        *log_file = Some(path);
    }
    write_runtime_diagnostic_line("initialized SceneMax NextGen runtime log");
    write_nextgen_launch_diagnostics();
}

fn write_nextgen_launch_diagnostics() {
    for key in [
        "SCENEMAX_NEXTGEN_LAUNCH_ROOT",
        "SCENEMAX_NEXTGEN_LAUNCH_DEBUG_EXE",
        "SCENEMAX_NEXTGEN_LAUNCH_RELEASE_EXE",
        "SCENEMAX_NEXTGEN_LAUNCH_BUILT_EXE",
        "SCENEMAX_NEXTGEN_LAUNCH_CARGO_EXE",
        "SCENEMAX_NEXTGEN_LAUNCH_SELECTED",
        "SCENEMAX_NEXTGEN_LAUNCH_USE_CARGO",
        "SCENEMAX_NEXTGEN_LAUNCH_BUILT_CURRENT",
    ] {
        if let Ok(value) = env::var(key)
            && !value.trim().is_empty()
        {
            write_runtime_diagnostic_line(format!("LAUNCH:{key}={value}"));
        }
    }
}

pub(super) fn write_runtime_diagnostic_line(message: impl AsRef<str>) {
    let Some(path) = SCENEMAX_RUNTIME_LOG_FILE
        .lock()
        .ok()
        .and_then(|path| path.clone())
    else {
        return;
    };
    let line = format!("[RUNTIME] {}\n", message.as_ref());
    if let Ok(mut file) = fs::OpenOptions::new().create(true).append(true).open(path) {
        let _ = file.write_all(line.as_bytes());
    }
}

pub(super) fn runtime_verbose_logging() -> bool {
    static VERBOSE: OnceLock<bool> = OnceLock::new();
    *VERBOSE.get_or_init(|| {
        env::var("SCENEMAX_RUNTIME_VERBOSE_LOG")
            .map(|value| {
                matches!(
                    value.trim().to_ascii_lowercase().as_str(),
                    "1" | "true" | "yes" | "on"
                )
            })
            .unwrap_or(false)
    })
}

pub(super) fn write_runtime_log_line(level: LoggerLevel, message: &str) {
    let level_text = match level {
        LoggerLevel::Info => "INFO",
        LoggerLevel::Debug => "DEBUG",
        LoggerLevel::Error => "ERROR",
    };
    tracing::info!(level = level_text, message, "SceneMax logger");
    let Some(path) = SCENEMAX_RUNTIME_LOG_FILE
        .lock()
        .ok()
        .and_then(|path| path.clone())
    else {
        return;
    };
    let line = format!("[{level_text}] {message}\n");
    if let Ok(mut file) = fs::OpenOptions::new().create(true).append(true).open(path) {
        let _ = file.write_all(line.as_bytes());
    }
}

pub(super) fn write_key_event_probe(
    prefix: &str,
    key: &str,
    trigger: KeyTrigger,
    _vars: &SceneMaxVars,
    _guards_by_name: &HashMap<String, Condition>,
    _transforms_by_name: &HashMap<String, Transform>,
    _collider_bounds: &SceneMaxColliderBounds,
) {
    if !runtime_verbose_logging() {
        return;
    }
    let message = format!(
        "{prefix} key={} trigger={}",
        key,
        key_trigger_label(trigger)
    );
    write_runtime_log_line(LoggerLevel::Info, &message);
}

pub(super) fn key_trigger_label(trigger: KeyTrigger) -> &'static str {
    match trigger {
        KeyTrigger::Pressed => "pressed",
        KeyTrigger::PressedOnce => "pressed_once",
        KeyTrigger::Released => "released",
    }
}

pub(super) fn write_state_assignment_probe(
    name: &str,
    previous: Option<f32>,
    value: f32,
    force_local: bool,
) {
    if !runtime_verbose_logging() {
        return;
    }
    if previous.is_some_and(|previous| (previous - value).abs() <= f32::EPSILON) {
        return;
    }
    let previous = previous
        .map(format_scenemax_number)
        .unwrap_or_else(|| "null".to_owned());
    write_runtime_log_line(
        LoggerLevel::Info,
        &format!(
            "STATE:SET name={} from={} to={} local={}",
            name,
            previous,
            format_scenemax_number(value),
            force_local as u8
        ),
    );
}

pub(super) fn load_startup_program(launch: &ProjectorLaunch) -> SceneMaxStartupProgram {
    let Some(script_path) = launch
        .script
        .clone()
        .or_else(|| default_script_path(launch.project_root.as_deref()))
    else {
        tracing::info!("no SceneMax script was supplied; using placeholder scene");
        return SceneMaxStartupProgram::default();
    };

    match load_scene_entry_program(&script_path) {
        Ok((program, script_root)) => {
            let function_names = sorted_function_names(&program);
            write_runtime_diagnostic_line(format!(
                "SCRIPT:READY root={} statements={} functions={}",
                script_root.display(),
                program.statements.len(),
                if function_names.is_empty() {
                    "<none>".to_owned()
                } else {
                    function_names.join(",")
                }
            ));
            log_lifecycle_when_summary(&program);
            tracing::info!(
                path = %script_path.display(),
                effective_root = %script_root.display(),
                statements = program.statements.len(),
                "loaded SceneMax startup script graph"
            );
            SceneMaxStartupProgram(Some(program), Some(script_root))
        }
        Err(error) => {
            tracing::error!(
                path = %script_path.display(),
                %error,
                "failed to load SceneMax startup script graph"
            );
            SceneMaxStartupProgram::default()
        }
    }
}

pub(super) fn log_lifecycle_when_summary(program: &Program) {
    for (index, statement) in program.statements.iter().enumerate() {
        let Statement::WhenEvent(event) = statement else {
            continue;
        };
        if lifecycle_probe_condition(&event.condition) {
            write_runtime_diagnostic_line(format!(
                "SCRIPT:LIFECYCLE_WHEN stmt={} guard={} condition={} actions={}",
                index,
                event
                    .guard
                    .as_ref()
                    .map(describe_condition_brief)
                    .unwrap_or_else(|| "-".to_owned()),
                describe_condition_brief(&event.condition),
                describe_statement_list(&event.actions)
            ));
        }
    }
}

pub(super) fn load_scene_entry_program(script_path: &Path) -> Result<(Program, PathBuf)> {
    let (root_program, root_script_dir) = load_script_with_adds(script_path, &mut HashSet::new())?;
    if has_scene_content(&root_program) {
        return Ok((root_program, root_script_dir));
    }

    if let Some(scene) = root_program
        .statements
        .iter()
        .find_map(|statement| match statement {
            Statement::SwitchTo { scene } => Some(scene),
            _ => None,
        })
    {
        let scene_main = root_script_dir.join(scene).join("main");
        tracing::info!(
            scene,
            path = %scene_main.display(),
            "startup script switches to scene"
        );
        let (mut program, scene_script_dir) =
            load_script_with_adds(&scene_main, &mut HashSet::new())?;
        if program.screen_mode == ScreenMode::Unspecified {
            program.screen_mode = root_program.screen_mode;
        }
        return Ok((program, scene_script_dir));
    }

    Ok((root_program, root_script_dir))
}

pub(super) fn load_script_with_adds(
    script_path: &Path,
    visited: &mut HashSet<PathBuf>,
) -> Result<(Program, PathBuf)> {
    let script_path = normalize_script_path(script_path);
    if !visited.insert(script_path.clone()) {
        tracing::warn!(path = %script_path.display(), "skipping recursive Add Code include");
        return Ok((
            Program::new(Vec::new()),
            script_path
                .parent()
                .unwrap_or_else(|| Path::new("."))
                .to_path_buf(),
        ));
    }

    let raw_source = fs::read_to_string(&script_path)?;
    let (source, source_rel) = strip_staged_source_metadata(&raw_source);
    let script_dir = staged_script_dir(&script_path, source_rel.as_deref());
    let parsed = scenemax_parser::parse_program(&source)?;
    log_unsupported_summary(&script_path, &parsed);
    write_runtime_diagnostic_line(format!(
        "SCRIPT:LOAD path={} dir={} statements={} source_rel={}",
        script_path.display(),
        script_dir.display(),
        parsed.statements.len(),
        source_rel
            .as_ref()
            .map(|path| path.display().to_string())
            .unwrap_or_else(|| "<none>".to_owned())
    ));
    let mut statements = Vec::new();
    let mut screen_mode = parsed.screen_mode;

    for statement in parsed.statements {
        match statement {
            Statement::AddCode { path } => {
                let include_path = resolve_code_path(&script_dir, &path);
                tracing::info!(
                    path,
                    resolved = %include_path.display(),
                    "loading Add Code include"
                );
                write_runtime_diagnostic_line(format!(
                    "SCRIPT:ADD path={} resolved={}",
                    path,
                    include_path.display()
                ));
                match load_script_with_adds(&include_path, visited) {
                    Ok((program, _)) => {
                        if screen_mode == ScreenMode::Unspecified {
                            screen_mode = program.screen_mode;
                        }
                        statements.extend(program.statements);
                    }
                    Err(error) => {
                        write_runtime_diagnostic_line(format!(
                            "SCRIPT:ADD_FAIL path={} error={error}",
                            include_path.display()
                        ));
                        tracing::warn!(
                            path = %include_path.display(),
                            %error,
                            "failed to load Add Code include"
                        );
                    }
                }
            }
            statement => statements.push(statement),
        }
    }

    Ok((
        Program {
            statements,
            screen_mode,
        },
        script_dir,
    ))
}

fn strip_staged_source_metadata(source: &str) -> (String, Option<PathBuf>) {
    let mut rest = source;
    let mut source_rel = None;
    let mut consumed_any = false;

    while let Some(after_marker) = rest.strip_prefix("//$[") {
        let Some(key_end) = after_marker.find("]=") else {
            break;
        };
        let key = &after_marker[..key_end];
        let after_key = &after_marker[key_end + 2..];
        let Some(value_end) = after_key.find(';') else {
            break;
        };
        let value = &after_key[..value_end];
        if key == "source_rel" {
            let normalized = value
                .trim()
                .trim_start_matches(|character| character == '/' || character == '\\');
            if !normalized.is_empty() {
                source_rel = Some(PathBuf::from(normalized));
            }
        }
        rest = &after_key[value_end + 1..];
        consumed_any = true;
    }

    if consumed_any {
        (rest.to_owned(), source_rel)
    } else {
        (source.to_owned(), None)
    }
}

fn staged_script_dir(script_path: &Path, source_rel: Option<&Path>) -> PathBuf {
    let base_dir = script_path.parent().unwrap_or_else(|| Path::new("."));
    source_rel
        .and_then(Path::parent)
        .map(|parent| base_dir.join(parent))
        .unwrap_or_else(|| base_dir.to_path_buf())
}

fn sorted_function_names(program: &Program) -> Vec<String> {
    let mut names = collect_functions_by_name(program)
        .keys()
        .cloned()
        .collect::<Vec<_>>();
    names.sort();
    names
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn staged_source_rel_preserves_first_statement_and_resolves_relative_adds() {
        let root = std::env::temp_dir().join(format!(
            "scenemax_staged_source_rel_{}_{}",
            std::process::id(),
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ));
        let running_dir = root.join("running");
        let scene_dir = running_dir.join("game_intro");
        fs::create_dir_all(&scene_dir).unwrap();
        fs::write(
            running_dir.join("main"),
            "//$[source_rel]=/game_intro/main;//$[project]=fighting_game_project;intro.draw intro_page: stretch\nadd \"game_intro_ui_fx.code\" code\nrun show_game_intro_ui async\n",
        )
        .unwrap();
        fs::write(
            scene_dir.join("game_intro_ui_fx.code"),
            "show_game_intro_ui = {\n  UI.load \"game_intro_ui\"\n}\n",
        )
        .unwrap();

        let (program, script_root) = load_scene_entry_program(&running_dir.join("main")).unwrap();

        assert_eq!(script_root, scene_dir);
        assert!(matches!(
            program.statements.first(),
            Some(Statement::ChannelDraw(draw)) if draw.channel == "intro" && !draw.clear
        ));
        assert!(collect_functions_by_name(&program).contains_key("show_game_intro_ui"));
        assert!(program.statements.iter().any(|statement| matches!(
            statement,
            Statement::Async { actions }
                if matches!(actions.first(), Some(Statement::RunFunction { name, .. }) if name == "show_game_intro_ui")
        )));

        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn scene_entry_preserves_root_screen_mode_after_switch() {
        let root = std::env::temp_dir().join(format!(
            "scenemax_screen_mode_scene_entry_{}_{}",
            std::process::id(),
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ));
        let running_dir = root.join("running");
        let scene_dir = running_dir.join("game_intro");
        fs::create_dir_all(&scene_dir).unwrap();
        fs::write(
            running_dir.join("main"),
            "screen.mode full\nswitch to \"game_intro\"\n",
        )
        .unwrap();
        fs::write(scene_dir.join("main"), "UI.load \"game_intro_ui\"\n").unwrap();

        let (program, script_root) = load_scene_entry_program(&running_dir.join("main")).unwrap();

        assert_eq!(script_root, scene_dir);
        assert_eq!(program.screen_mode, ScreenMode::Full);
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn include_expansion_preserves_functions_after_nested_do_while_else() {
        let root = std::env::temp_dir().join(format!(
            "scenemax_train_include_{}_{}",
            std::process::id(),
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ));
        let scene_dir = root.join("scene");
        let utils_dir = scene_dir.join("utils");
        let states_dir = scene_dir.join("game_states");
        fs::create_dir_all(&utils_dir).unwrap();
        fs::create_dir_all(&states_dir).unwrap();
        fs::write(
            scene_dir.join("main"),
            "add \"utils/train_stage\" code\nadd \"game_states/game_start\" code\nrun game_start\n",
        )
        .unwrap();
        fs::write(
            utils_dir.join("train_stage"),
            "train_helicopter_sound_loop = {\n  do\n    wait 0.35 seconds\n    if(game_status==GAME_STATE_START) {\n      if(train_helicopter_sound_playing==0) {\n        train_helicopter_sound_playing=1\n      }\n    } else {\n      if(train_helicopter_sound_playing==1) {\n        train_helicopter_sound_playing=0\n      }\n    }\n  while train_stage_started==1\n\n  if(train_helicopter_sound_playing==1) {\n    train_helicopter_sound_playing=0\n  }\n}\n\ntrain_stage_init = {\n  train_stage_started=1\n}\n\ntrain_start_fighters_from_above = {\n  player1.switch to character mode : gravity 60\n}\n",
        )
        .unwrap();
        fs::write(
            states_dir.join("game_start"),
            "game_start = {\n  run train_stage_init\n  run train_start_fighters_from_above Async\n}\n",
        )
        .unwrap();

        let (program, _) = load_scene_entry_program(&scene_dir.join("main")).unwrap();
        let functions = collect_functions_by_name(&program);

        assert!(functions.contains_key("train_helicopter_sound_loop"));
        assert!(functions.contains_key("train_stage_init"));
        assert!(functions.contains_key("train_start_fighters_from_above"));

        let _ = fs::remove_dir_all(root);
    }
}

pub(super) fn log_unsupported_summary(script_path: &Path, program: &Program) {
    let mut unsupported = BTreeMap::<String, usize>::new();
    collect_unsupported_statements(&program.statements, &mut unsupported);
    if unsupported.is_empty() {
        return;
    }
    let total = unsupported.values().copied().sum::<usize>();
    let examples = unsupported
        .iter()
        .take(8)
        .map(|(text, count)| format!("{count}x {text}"))
        .collect::<Vec<_>>()
        .join(" | ");
    tracing::info!(
        path = %script_path.display(),
        unsupported = total,
        examples,
        "SceneMax parser compatibility gaps in script"
    );
}

pub(super) fn collect_unsupported_statements(
    statements: &[Statement],
    unsupported: &mut BTreeMap<String, usize>,
) {
    for statement in statements {
        match statement {
            Statement::Unsupported { text } => {
                *unsupported.entry(text.clone()).or_default() += 1;
            }
            Statement::KeyEvent(event) => {
                collect_unsupported_statements(&event.actions, unsupported)
            }
            Statement::WhenEvent(event) => {
                collect_unsupported_statements(&event.actions, unsupported)
            }
            Statement::FunctionDef(function) => {
                collect_unsupported_statements(&function.actions, unsupported);
            }
            Statement::If(statement) => {
                collect_unsupported_statements(&statement.actions, unsupported);
                collect_unsupported_statements(&statement.else_actions, unsupported);
            }
            Statement::Guarded { actions, .. }
            | Statement::Repeat { actions, .. }
            | Statement::DoWhile { actions, .. }
            | Statement::LoopContinue { actions, .. }
            | Statement::Async { actions } => collect_unsupported_statements(actions, unsupported),
            _ => {}
        }
    }
}

pub(super) fn has_scene_content(program: &Program) -> bool {
    program.statements.iter().any(|statement| {
        matches!(
            statement,
            Statement::ModelDecl { .. }
                | Statement::LightDecl(_)
                | Statement::CameraPosition(_)
                | Statement::CameraRotation(_)
        )
    })
}

pub(super) fn has_ui_runtime_content(program: &Program) -> bool {
    program
        .statements
        .iter()
        .any(statement_has_ui_runtime_content)
}

pub(super) fn statement_has_ui_runtime_content(statement: &Statement) -> bool {
    if matches!(
        statement,
        Statement::UiSetProperty(_) | Statement::ChannelDraw(_) | Statement::Print(_)
    ) {
        return true;
    }
    if scenemax_ui_action_from_statement(statement).is_some() {
        return true;
    }
    match statement {
        Statement::KeyEvent(event) => event.actions.iter().any(statement_has_ui_runtime_content),
        Statement::WhenEvent(event) => event.actions.iter().any(statement_has_ui_runtime_content),
        Statement::FunctionDef(function) => function
            .actions
            .iter()
            .any(statement_has_ui_runtime_content),
        Statement::If(statement) => {
            statement
                .actions
                .iter()
                .any(statement_has_ui_runtime_content)
                || statement
                    .else_actions
                    .iter()
                    .any(statement_has_ui_runtime_content)
        }
        Statement::Guarded { actions, .. }
        | Statement::Repeat { actions, .. }
        | Statement::DoWhile { actions, .. }
        | Statement::LoopContinue { actions, .. }
        | Statement::Async { actions } => actions.iter().any(statement_has_ui_runtime_content),
        _ => false,
    }
}

pub(super) fn normalize_script_path(path: &Path) -> PathBuf {
    if path.is_file() {
        return path.to_path_buf();
    }
    let code_path = path.with_extension("code");
    if code_path.is_file() {
        return code_path;
    }
    path.to_path_buf()
}

pub(super) fn resolve_code_path(script_dir: &Path, path: &str) -> PathBuf {
    let relative = path
        .trim_start_matches('/')
        .replace('/', std::path::MAIN_SEPARATOR_STR);
    normalize_script_path(&script_dir.join(relative))
}

pub(super) fn default_script_path(project_root: Option<&Path>) -> Option<PathBuf> {
    let root = project_root?;
    let direct = [
        root.join("running").join("main"),
        root.join("running").join("main.code"),
        root.join("main"),
        root.join("main.code"),
    ]
    .into_iter()
    .find(|path| path.is_file());
    direct.or_else(|| default_scripts_subdir_main(root))
}

fn default_scripts_subdir_main(root: &Path) -> Option<PathBuf> {
    let scripts_dir = root.join("scripts");
    let entries = fs::read_dir(scripts_dir).ok()?;
    let mut candidates = Vec::new();
    for entry in entries.flatten() {
        let path = entry.path();
        if !path.is_dir() {
            continue;
        }
        for file_name in ["main", "main.code"] {
            let candidate = path.join(file_name);
            if candidate.is_file() {
                candidates.push(candidate);
            }
        }
    }
    (candidates.len() == 1).then(|| candidates.remove(0))
}

pub(super) fn setup_scenemax_program(
    mut commands: Commands,
    asset_server: Res<AssetServer>,
    context: Res<SceneMaxLaunchContext>,
    startup_program: Res<SceneMaxStartupProgram>,
    mut vars: ResMut<SceneMaxVars>,
    mut object_pools: ResMut<SceneMaxObjectPools>,
    mut camera_system: ResMut<SceneMaxCameraSystem>,
    mut delayed_actions: ResMut<DelayedActionQueue>,
    mut ui_queue: ResMut<SceneMaxUiActionQueue>,
    mut collider_bounds: ResMut<SceneMaxColliderBounds>,
    mut meshes: ResMut<Assets<Mesh>>,
    mut materials: ResMut<Assets<StandardMaterial>>,
    mut character_configs: ResMut<Assets<SceneMaxControlSchemeConfig>>,
) {
    let Some(program) = startup_program.0.as_ref() else {
        setup_placeholder_model(commands, meshes, materials);
        return;
    };

    let Some(asset_root) = context.asset_root.as_ref() else {
        tracing::error!("SceneMax NextGen requires a project root with a resources folder");
        setup_placeholder_model(commands, meshes, materials);
        return;
    };

    object_pools.aliases.clear();
    object_pools.pools.clear();
    delayed_actions.actions.clear();
    collider_bounds.clear();
    apply_initial_assignments(program, &mut vars);
    apply_camera_systems(program, &mut camera_system);
    load_cinematic_rigs(
        program,
        &mut camera_system,
        context.script_root.as_deref(),
        context.asset_root.as_deref(),
    );
    let startup_gltfs = spawn_scenemax_program(
        &mut commands,
        &asset_server,
        asset_root,
        context.builtin_asset_root.as_deref(),
        program,
        &mut vars,
        &mut object_pools,
        &mut camera_system,
        &mut delayed_actions,
        &mut ui_queue,
        &mut collider_bounds,
        &mut meshes,
        &mut materials,
        &mut character_configs,
    );
    commands.insert_resource(SceneMaxStartupActionState::waiting_for_gltfs(startup_gltfs));
}

pub(super) fn spawn_scenemax_program(
    commands: &mut Commands,
    asset_server: &AssetServer,
    asset_root: &Path,
    builtin_asset_root: Option<&Path>,
    program: &Program,
    vars: &mut SceneMaxVars,
    object_pools: &mut SceneMaxObjectPools,
    _camera_system: &mut SceneMaxCameraSystem,
    _delayed_actions: &mut DelayedActionQueue,
    _ui_queue: &mut SceneMaxUiActionQueue,
    collider_bounds: &mut SceneMaxColliderBounds,
    meshes: &mut ResMut<Assets<Mesh>>,
    materials: &mut ResMut<Assets<StandardMaterial>>,
    character_configs: &mut ResMut<Assets<SceneMaxControlSchemeConfig>>,
) -> Vec<Handle<Gltf>> {
    let animations_by_target = collect_animations_by_target(program);
    let visibility_by_target = collect_visibility_by_target(program);
    let turn_by_target = collect_turn_by_target(program);
    let functions_by_name = collect_functions_by_name(program);
    let guards_by_name = collect_guards_by_name(program);
    let attaches_by_target = collect_attaches_by_target(program);
    let mut startup_gltfs = Vec::new();
    let mut model_declarations = collect_model_declarations(program);
    model_declarations.extend(instantiate_object_pool_declarations(
        program,
        &functions_by_name,
        object_pools,
    ));
    let mut spawned_any = false;
    let mut entities_by_name = HashMap::new();
    let mut transforms_by_name = HashMap::new();
    let sprite_index = load_sprite_index(asset_root, builtin_asset_root);
    let sprite_context = SceneMaxLaunchContext {
        script_root: None,
        asset_root: Some(asset_root.to_path_buf()),
        builtin_asset_root: builtin_asset_root.map(Path::to_path_buf),
        window_width: 0,
        window_height: 0,
    };

    let light_declarations = collect_light_declarations(program);
    if light_declarations.is_empty() {
        set_fallback_lighting_enabled(commands, true);
        apply_default_ambient_light(commands);
    } else {
        set_fallback_lighting_enabled(commands, false);
        for light in &light_declarations {
            let (entity, transform) = spawn_scenemax_light_decl(
                commands,
                light,
                vars,
                None,
                &guards_by_name,
                Some(&transforms_by_name),
                Some(collider_bounds),
            );
            entities_by_name.insert(light.name.clone(), entity);
            transforms_by_name.insert(light.name.clone(), transform);
            spawned_any = true;
        }
    }

    for ModelRuntimeDecl {
        name,
        resource,
        options,
    } in &model_declarations
    {
        if options.collider {
            let transform = collider_decl_transform(
                name,
                options,
                &attaches_by_target,
                &transforms_by_name,
                vars,
                &guards_by_name,
                Some(collider_bounds),
            );
            let entity = spawn_scenemax_collider_decl(
                commands,
                name,
                resource,
                options,
                transform,
                attaches_by_target.get(name),
            );
            register_collider_bounds(collider_bounds, name, options, transform);
            if let Some(attach) = attaches_by_target.get(name) {
                register_collider_owner(collider_bounds, name, &attach_owner(&attach.subject));
            }
            entities_by_name.insert(name.clone(), entity);
            transforms_by_name.insert(name.clone(), transform);
            spawned_any = true;
            tracing::info!(name, resource, "spawned SceneMax collider");
            continue;
        }

        if let Some(asset_id) = effekseer_asset_id(resource) {
            let transform = transform_from_options_resolved(
                options,
                None,
                vars,
                &guards_by_name,
                Some(&transforms_by_name),
                Some(collider_bounds),
            );
            let effect_path = resolve_effekseer_effect_path(asset_root, &asset_id);
            let entity_id = commands
                .spawn((
                    SceneMaxEntity {
                        name: name.clone(),
                        runtime_name: format!("{name}@1"),
                    },
                    SceneMaxEffekseerEffect {
                        instance_id: next_effekseer_instance_id(),
                        asset_id: asset_id.clone(),
                        effect_path: effect_path.clone(),
                        one_shot_duration_seconds: effekseer_one_shot_duration_seconds(
                            effect_path.as_deref(),
                        ),
                    },
                    transform,
                    initial_visibility(name, options, &visibility_by_target),
                ))
                .id();
            entities_by_name.insert(name.clone(), entity_id);
            transforms_by_name.insert(name.clone(), transform);
            spawned_any = true;
            write_runtime_diagnostic_line(format!(
                "EFFEKSEER:DECL target={} asset={} path={} renderer={}",
                name,
                asset_id,
                effect_path
                    .as_ref()
                    .map(|path| path.display().to_string())
                    .unwrap_or_else(|| "<missing>".to_owned()),
                effekseer_renderer_label()
            ));
            tracing::warn!(
                name,
                asset = asset_id,
                renderer = effekseer_renderer_label(),
                "registered Effekseer effect for the Bevy bridge"
            );
            continue;
        }

        if options.sprite {
            if let Some((entity_id, transform)) = spawn_scenemax_sprite_decl(
                commands,
                asset_server,
                &sprite_context,
                meshes,
                materials,
                name,
                resource,
                options,
                &sprite_index,
                &visibility_by_target,
            ) {
                insert_physics_components(commands, entity_id, name, resource, options, &transform);
                entities_by_name.insert(name.clone(), entity_id);
                transforms_by_name.insert(name.clone(), transform);
                spawned_any = true;
                continue;
            }
            tracing::warn!(name, resource, "SceneMax sprite resource was not found");
            write_runtime_diagnostic_line(format!(
                "sprite resource {name}=>{resource} was not found; project assets={}; builtin assets={}",
                asset_root.display(),
                builtin_asset_root
                    .map(|path| path.display().to_string())
                    .unwrap_or_else(|| "<none>".to_owned())
            ));
        }

        if let Some(primitive) = primitive_mesh(
            options,
            resource,
            asset_server,
            asset_root,
            builtin_asset_root,
            meshes,
            materials,
        ) {
            let transform = primitive_transform_from_options_resolved(
                options,
                vars,
                &guards_by_name,
                Some(&transforms_by_name),
                Some(collider_bounds),
            );
            let entity = commands
                .spawn((
                    SceneMaxEntity {
                        name: name.clone(),
                        runtime_name: format!("{name}@1"),
                    },
                    primitive.0,
                    primitive.1,
                    transform,
                    initial_visibility(name, options, &visibility_by_target),
                ))
                .id();
            insert_physics_components(commands, entity, name, resource, options, &transform);
            entities_by_name.insert(name.clone(), entity);
            transforms_by_name.insert(name.clone(), transform);
            spawned_any = true;
            tracing::info!(name, resource, "spawned SceneMax primitive");
            continue;
        }

        match scenemax_assets::resolve_model_resource_with_builtin_fallback(
            asset_root,
            builtin_asset_root,
            resource,
        ) {
            Ok(model) => {
                let runtime_name = format!("{name}@1");
                let asset_path = model.asset_path;
                let bevy_visual_offset_y = model
                    .character_physics
                    .as_ref()
                    .and_then(|character| character.bevy_visual_offset_y);
                let gltf: Handle<Gltf> = asset_server.load(asset_path.clone());
                let scene = WorldAssetRoot(
                    asset_server.load(GltfAssetLabel::Scene(0).from_asset(asset_path.clone())),
                );
                let transform = transform_from_options_resolved(
                    options,
                    model.scale,
                    vars,
                    &guards_by_name,
                    Some(&transforms_by_name),
                    Some(collider_bounds),
                );
                let entity_id = commands
                    .spawn((
                        SceneMaxEntity {
                            name: name.clone(),
                            runtime_name,
                        },
                        SceneMaxModelResource {
                            resource: resource.clone(),
                        },
                        SceneMaxGltf { gltf: gltf.clone() },
                        scene,
                        transform,
                        initial_visibility(name, options, &visibility_by_target),
                    ))
                    .id();
                insert_gltf_visual_offset(commands, entity_id, bevy_visual_offset_y);

                if let Some(animation) = animations_by_target.get(name) {
                    commands.entity(entity_id).insert(AnimationToPlay {
                        clip: animation.clip.clone(),
                        runtime_clip: animation.clip.clone(),
                        looped: animation.looped,
                        speed: animation.speed,
                        gltf: gltf.clone(),
                        target_model_resource: Some(resource.clone()),
                        baked_external: None,
                        bake_request: None,
                        external_retarget: Default::default(),
                        external_source: false,
                        tried_external_source: false,
                        visual_transform_preapplied: false,
                        retarget_wait_logged: false,
                    });
                }
                insert_physics_components(commands, entity_id, name, resource, options, &transform);

                entities_by_name.insert(name.clone(), entity_id);
                transforms_by_name.insert(name.clone(), transform);
                startup_gltfs.push(gltf);
                spawned_any = true;
                tracing::info!(
                    name,
                    resource,
                    path = %asset_path,
                    "spawned SceneMax GLTF model"
                );
                write_runtime_diagnostic_line(format!(
                    "resolved GLTF model {name}=>{resource} at {asset_path}"
                ));
            }
            Err(scenemax_assets::AssetLookupError::UnsupportedModelFormat {
                asset_path, ..
            }) => {
                let transform = transform_from_options_resolved(
                    options,
                    None,
                    vars,
                    &guards_by_name,
                    Some(&transforms_by_name),
                    Some(collider_bounds),
                );
                let entity_id = spawn_unsupported_model_placeholder(
                    commands,
                    meshes,
                    materials,
                    name,
                    resource,
                    options,
                    transform,
                    &visibility_by_target,
                );
                insert_physics_components(commands, entity_id, name, resource, options, &transform);
                entities_by_name.insert(name.clone(), entity_id);
                transforms_by_name.insert(name.clone(), transform);
                spawned_any = true;
                tracing::warn!(
                    name,
                    resource,
                    path = %asset_path,
                    "spawned placeholder for unsupported SceneMax model format"
                );
                write_runtime_diagnostic_line(format!(
                    "placeholder for unsupported model {name}=>{resource}; path={asset_path}"
                ));
            }
            Err(scenemax_assets::AssetLookupError::ModelNotFound(_)) => {
                let transform = transform_from_options_resolved(
                    options,
                    None,
                    vars,
                    &guards_by_name,
                    Some(&transforms_by_name),
                    Some(collider_bounds),
                );
                let entity_id = spawn_unsupported_model_placeholder(
                    commands,
                    meshes,
                    materials,
                    name,
                    resource,
                    options,
                    transform,
                    &visibility_by_target,
                );
                insert_physics_components(commands, entity_id, name, resource, options, &transform);
                entities_by_name.insert(name.clone(), entity_id);
                transforms_by_name.insert(name.clone(), transform);
                spawned_any = true;
                tracing::warn!(
                    name,
                    resource,
                    "spawned placeholder for unresolved SceneMax model resource"
                );
                write_runtime_diagnostic_line(format!(
                    "placeholder for unresolved model {name}=>{resource}; project assets={}; builtin assets={}",
                    asset_root.display(),
                    builtin_asset_root
                        .map(|path| path.display().to_string())
                        .unwrap_or_else(|| "<none>".to_owned())
                ));
            }
            Err(error) => {
                let transform = transform_from_options_resolved(
                    options,
                    None,
                    vars,
                    &guards_by_name,
                    Some(&transforms_by_name),
                    Some(collider_bounds),
                );
                let entity_id = spawn_unsupported_model_placeholder(
                    commands,
                    meshes,
                    materials,
                    name,
                    resource,
                    options,
                    transform,
                    &visibility_by_target,
                );
                insert_physics_components(commands, entity_id, name, resource, options, &transform);
                entities_by_name.insert(name.clone(), entity_id);
                transforms_by_name.insert(name.clone(), transform);
                spawned_any = true;
                tracing::warn!(
                    name,
                    resource,
                    %error,
                    "spawned placeholder after unexpected SceneMax model lookup error"
                );
                write_runtime_diagnostic_line(format!(
                    "placeholder for unresolved model {name}=>{resource}; reason={error}"
                ));
            }
        }
    }

    apply_look_at_commands(
        program,
        commands,
        &entities_by_name,
        &mut transforms_by_name,
    );
    apply_character_modes(
        program,
        commands,
        &entities_by_name,
        &transforms_by_name,
        character_configs,
    );
    for (target, turn) in turn_by_target {
        if let Some(entity) = entities_by_name.get(&target) {
            commands
                .entity(*entity)
                .insert(timed_turn_from_statement(&turn));
        }
    }

    if !spawned_any && has_ui_runtime_content(program) {
        write_runtime_diagnostic_line(
            "skipped default placeholder because the program contains UI runtime content",
        );
    } else if !spawned_any {
        write_runtime_diagnostic_line(
            "spawned default placeholder because the program produced no renderable entities",
        );
        spawn_placeholder_model(commands, meshes, materials);
    }
    startup_gltfs
}

pub(super) fn apply_startup_runs_when_ready(
    time: Res<Time>,
    asset_server: Res<AssetServer>,
    startup_program: Res<SceneMaxStartupProgram>,
    mut startup_action_state: ResMut<SceneMaxStartupActionState>,
    mut commands: Commands,
    mut vars: ResMut<SceneMaxVars>,
    mut object_pools: ResMut<SceneMaxObjectPools>,
    mut camera_system: ResMut<SceneMaxCameraSystem>,
    mut runtime_assets: ResMut<SceneMaxRuntimeAssets>,
    mut delayed_actions: ResMut<DelayedActionQueue>,
    mut ui_queue: ResMut<SceneMaxUiActionQueue>,
    mut scene_entities: ParamSet<(
        Query<(Entity, &SceneMaxEntity, &Transform)>,
        Query<(
            Entity,
            &SceneMaxEntity,
            &Transform,
            Option<&SceneMaxGltf>,
            Option<&SceneMaxModelResource>,
        )>,
    )>,
    bone_queries: SceneMaxBoneQueries,
) {
    if startup_action_state.applied {
        return;
    }
    let Some(program) = startup_program.0.as_ref() else {
        startup_action_state.applied = true;
        return;
    };

    if !startup_action_state
        .waiting_gltfs
        .iter()
        .all(|handle| asset_server.is_loaded_with_dependencies(handle))
    {
        startup_action_state.wait_seconds += time.delta_secs();
        if !startup_action_state.waiting_logged {
            startup_action_state.waiting_logged = true;
            write_runtime_diagnostic_line(format!(
                "STARTUP:WAIT_ASSETS gltf_count={}",
                startup_action_state.waiting_gltfs.len()
            ));
        }
        return;
    }

    if startup_action_state.ready_frames == 0 {
        write_runtime_diagnostic_line(format!(
            "STARTUP:ASSETS_READY gltf_count={} wait_seconds={}",
            startup_action_state.waiting_gltfs.len(),
            format_scenemax_number(startup_action_state.wait_seconds)
        ));
    }
    startup_action_state.ready_frames = startup_action_state.ready_frames.saturating_add(1);
    if startup_action_state.ready_frames < 2 {
        return;
    }

    let mut transforms_by_name =
        build_action_transform_map(program, &object_pools, scene_entities.p0(), &bone_queries);
    let mut entities_by_name = HashMap::new();
    let mut gltfs_by_name = HashMap::new();
    let mut model_resources_by_name = HashMap::new();
    for (entity, scene_entity, _transform, gltf, model_resource) in &scene_entities.p1() {
        entities_by_name.insert(scene_entity.name.clone(), entity);
        if let Some(gltf) = gltf {
            gltfs_by_name.insert(scene_entity.name.clone(), gltf.gltf.clone());
        }
        if let Some(model_resource) = model_resource {
            model_resources_by_name
                .insert(scene_entity.name.clone(), model_resource.resource.clone());
        }
    }
    runtime_assets.gltf_handles_by_name = gltfs_by_name.clone();
    runtime_assets.model_resources_by_name = model_resources_by_name;

    let functions_by_name = collect_functions_by_name(program);
    let guards_by_name = collect_guards_by_name(program);
    write_runtime_diagnostic_line(format!(
        "STARTUP:APPLY_RUNS entities={} gltfs={} ready_frames={}",
        entities_by_name.len(),
        gltfs_by_name.len(),
        startup_action_state.ready_frames
    ));
    delayed_actions.registered_key_events.events.clear();
    delayed_actions.registered_key_events.next_id = 0;
    delayed_actions.registered_when_events.events.clear();
    delayed_actions.registered_when_events.next_id = 0;
    delayed_actions.registered_run_every.events.clear();
    delayed_actions.registered_run_every.next_id = 0;
    apply_startup_runs(
        program,
        &mut commands,
        &mut vars,
        &mut object_pools,
        &mut camera_system,
        &mut runtime_assets,
        &mut delayed_actions,
        &mut ui_queue,
        &functions_by_name,
        &entities_by_name,
        &mut transforms_by_name,
        &gltfs_by_name,
        &guards_by_name,
    );
    startup_action_state.applied = true;
}
