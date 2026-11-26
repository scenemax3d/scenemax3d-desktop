use super::*;
use std::sync::mpsc::{self, Receiver, SyncSender};

struct Submission {
    code: String,
    scope: Option<SceneMaxScopeFrame>,
    close_on_error: bool,
    reply: async_channel::Sender<Result<(), String>>,
}

/// Worker-side handle for submitting commands to a running projector.
#[derive(Clone)]
pub struct PartialCodeSender(SyncSender<Submission>);

pub enum PairingStatus {
    Code(String),
    Paired,
    Closed,
    Error(String),
}

/// Game-side end of a live command channel.
pub struct PartialCodeInput {
    requests: Receiver<Submission>,
    pairing_ui: Option<(SyncSender<()>, Receiver<PairingStatus>)>,
}

impl PartialCodeInput {
    pub(super) fn has_pairing_ui(&self) -> bool {
        self.pairing_ui.is_some()
    }

    /// Enable native pairing from the keyboard and show the current pairing code.
    pub fn with_pairing_ui(
        mut self,
        start: SyncSender<()>,
        status: Receiver<PairingStatus>,
    ) -> Self {
        self.pairing_ui = Some((start, status));
        self
    }
}

pub fn partial_code_channel() -> (PartialCodeSender, PartialCodeInput) {
    let (sender, receiver) = mpsc::sync_channel(32);
    (
        PartialCodeSender(sender),
        PartialCodeInput {
            requests: receiver,
            pairing_ui: None,
        },
    )
}

impl PartialCodeSender {
    /// Call from a worker thread. Returns once the current scene accepts or
    /// rejects the snippet. Actions that wait continue on later game updates.
    #[cfg(not(target_arch = "wasm32"))]
    pub fn run_partial_code(
        &self,
        code: impl Into<String>,
        scope: Option<SceneMaxScopeFrame>,
        close_on_error: bool,
    ) -> Result<(), String> {
        let (reply, result) = async_channel::bounded(1);
        self.0
            .send(Submission {
                code: code.into(),
                scope,
                close_on_error,
                reply,
            })
            .map_err(|_| "projector closed".to_owned())?;
        result
            .recv_blocking()
            .map_err(|_| "projector closed".to_owned())?
    }
    /// Queue without blocking the browser event loop and await game admission.
    pub async fn submit_async(&self, code: String) -> Result<(), String> {
        let (reply, result) = async_channel::bounded(1);
        self.0
            .try_send(Submission {
                code,
                scope: None,
                close_on_error: false,
                reply,
            })
            .map_err(|e| e.to_string())?;
        result
            .recv()
            .await
            .map_err(|_| "projector closed".to_owned())?
    }
}

pub(super) fn update_pairing_ui(
    input: NonSend<PartialCodeInput>,
    keys: Res<ButtonInput<KeyCode>>,
    mut commands: Commands,
    mut clipboard: ResMut<bevy::clipboard::Clipboard>,
    mut pending: Local<Option<String>>,
    mut paired: Local<bool>,
    mut overlay: Local<Option<(Entity, std::time::Instant)>>,
) {
    let Some((pair, messages)) = &input.pairing_ui else {
        return;
    };
    let shortcut = keys.just_pressed(KeyCode::F12)
        && keys.any_pressed([KeyCode::ControlLeft, KeyCode::ControlRight]);
    let mut notice = None;
    let mut copy = false;
    for message in messages.try_iter() {
        match message {
            PairingStatus::Code(code) => {
                *pending = Some(code);
                copy = true;
            }
            PairingStatus::Paired => {
                *pending = None;
                *paired = true;
            }
            PairingStatus::Closed => {
                *pending = None;
                *paired = false;
            }
            PairingStatus::Error(error) => {
                *pending = None;
                *paired = false;
                notice = Some(error);
            }
        }
    }
    if shortcut {
        if pending.is_some() {
            copy = true;
        } else if *paired {
            notice = Some("Game MCP already paired".into());
        } else {
            let _ = pair.try_send(());
        }
    }
    if copy && let Some(code) = pending.as_deref() {
        notice = Some(match clipboard.set_text(code) {
            Ok(()) => format!("Game MCP pairing code copied: {code}"),
            Err(_) => format!("Clipboard unavailable. Game MCP pairing code: {code}"),
        });
    }
    let now = std::time::Instant::now();
    if overlay
        .as_ref()
        .is_some_and(|(_, until)| now >= *until || notice.is_some())
        && let Some((entity, _)) = overlay.take()
    {
        commands.entity(entity).try_despawn();
    }
    if let Some(message) = notice {
        *overlay = Some((
            commands
                .spawn((
                    Node {
                        position_type: PositionType::Absolute,
                        right: px(12.),
                        top: px(12.),
                        padding: UiRect::all(px(8.)),
                        ..default()
                    },
                    GlobalZIndex(i32::MAX - 2),
                    Pickable::IGNORE,
                    BackgroundColor(Color::srgba(0.06, 0.07, 0.09, 0.95)),
                    Text::new(message),
                    TextFont::from_font_size(18.),
                    TextColor(Color::WHITE),
                ))
                .id(),
            now + std::time::Duration::from_secs(1),
        ));
    }
}

pub(super) fn receive(
    input: NonSend<PartialCodeInput>,
    startup: Res<SceneMaxStartupActionState>,
    mut scene: ResMut<SceneMaxStartupProgram>,
    context: Res<SceneMaxLaunchContext>,
    mut cameras: ResMut<SceneMaxCameraSystem>,
    mut pools: ResMut<SceneMaxObjectPools>,
    mut queue: ResMut<DelayedActionQueue>,
    mut exit: MessageWriter<AppExit>,
) {
    if !startup.applied || queue.pending_scene.is_some() {
        return;
    }
    // Admit one submission per update so the dispatcher runs it before a
    // later submission can replace its definitions.
    let Ok(request) = input.requests.try_recv() else {
        return;
    };
    let script_dir = scene.1.as_deref().or(context.script_root.as_deref());
    let parsed = parse(&request.code, script_dir).inspect(|program| {
        load_cinematic_rigs(
            program,
            &mut cameras,
            script_dir,
            context.asset_root.as_deref(),
        );
    });
    let result = parsed
        .map(|program| {
            pools
                .live_bone_targets
                .extend(collect_bone_alias_targets(&program));
            if request.scope.is_none() {
                pools
                    .shared_variables
                    .extend(collect_shared_assignment_names(&program));
            }
            let active = scene.0.get_or_insert_with(|| Program::new(Vec::new()));
            schedule(program, active, &mut queue, request.scope);
        })
        .map_err(|error| format!("{error:#}"));
    if request.close_on_error && result.is_err() {
        exit.write(AppExit::error());
    }
    let _ = request.reply.try_send(result);
}

fn parse(code: &str, script_dir: Option<&Path>) -> Result<Program> {
    let parsed = scenemax_parser::parse_program(code)?;
    let program = expand_code_includes(
        parsed,
        script_dir.unwrap_or(Path::new(".")),
        &mut HashSet::new(),
        true,
    )?;
    let mut unsupported = BTreeMap::new();
    collect_unsupported_statements(&program.statements, &mut unsupported);
    if let Some((text, _)) = unsupported.first_key_value() {
        anyhow::bail!("unsupported SceneMax command: {text}");
    }
    anyhow::ensure!(
        program.screen_mode == ScreenMode::Unspecified,
        "screen mode is a startup setting"
    );
    Ok(program)
}

fn schedule(
    program: Program,
    active: &mut Program,
    queue: &mut DelayedActionQueue,
    mut scope: Option<SceneMaxScopeFrame>,
) {
    let mut actions = Vec::new();
    for statement in program.statements {
        if !matches!(
            statement,
            Statement::FunctionDef(_) | Statement::GuardDef { .. }
        ) {
            actions.push(statement);
            continue;
        }
        let definitions = if let Some(frame) = scope.as_mut() {
            std::sync::Arc::make_mut(
                frame
                    .partial_definitions
                    .get_or_insert_with(|| std::sync::Arc::new(Program::new(Vec::new()))),
            )
        } else {
            &mut *active
        };
        // Replace in place: top-level event controllers use statement indices.
        let existing =
            definitions
                .statements
                .iter_mut()
                .rev()
                .find(|old| match (&**old, &statement) {
                    (Statement::FunctionDef(a), Statement::FunctionDef(b)) => a.name == b.name,
                    (Statement::GuardDef { name: a, .. }, Statement::GuardDef { name: b, .. }) => {
                        a == b
                    }
                    _ => false,
                });
        if let Some(existing) = existing {
            *existing = statement;
        } else {
            definitions.statements.push(statement);
        }
    }
    enqueue_delayed_actions(Some(queue), 0., actions, None, scope);
}

#[cfg(test)]
mod tests {
    use super::*;
    use bevy::ecs::system::RunSystemOnce;

    #[test]
    fn scoped_snippet_resumes_with_its_bindings_and_private_function() {
        let mut world =
            crate::input_gameplay_tests::world("increment = {\n finishes = finishes + 100\n}");
        let mut scope = SceneMaxScopeFrame::default();
        scope.begin_activation();
        scope.vars.insert("amount".into(), 7.);
        let program = parse(
            "increment = {\n finishes = finishes + amount\n}\nwait 0.1 seconds\nrun increment",
            None,
        )
        .unwrap();
        world.resource_scope(|world, mut scene: Mut<SceneMaxStartupProgram>| {
            schedule(
                program,
                scene.0.as_mut().unwrap(),
                &mut world.resource_mut::<DelayedActionQueue>(),
                Some(scope),
            );
        });
        for _ in 0..4 {
            world.run_system_once(update_delayed_actions).unwrap();
        }
        assert_eq!(world.resource::<SceneMaxVars>().0["finishes"], 7.);
        let scene = world.resource::<SceneMaxStartupProgram>();
        assert_eq!(
            scene.0.as_ref().unwrap(),
            &scenemax_parser::parse_program("increment = {\n finishes = finishes + 100\n}")
                .unwrap()
        );
    }

    #[test]
    fn live_includes_resolve_relative_to_scene_and_reject_nested_failures() {
        let dir = tempfile::tempdir().unwrap();
        std::fs::write(dir.path().join("commands.code"), "var amount = 3").unwrap();
        let source = "add \"commands.code\" code";
        assert_eq!(
            parse(source, Some(dir.path())).unwrap(),
            parse("var amount = 3", None).unwrap()
        );
        std::fs::write(
            dir.path().join("commands.code"),
            "add \"missing.code\" code",
        )
        .unwrap();
        assert!(parse(source, Some(dir.path())).is_err());
    }
}
