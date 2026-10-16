//! Compact retained Git dialogs matching the desktop staging, branch and history workflows.
use crate::application::git::{Action, Page, State};
use bevy::{
    input_focus::{FocusCause, InputFocus, tab_navigation::TabGroup},
    prelude::*,
    text::EditableText,
};
use scenemax_ide_core::git::Operation;
use scenemax_ide_ui::{button, label, property, theme::*};

#[derive(Component)]
pub(crate) struct Field(&'static str);
#[derive(Component, Clone)]
pub(crate) enum Click {
    Action(Action),
    Invoke(&'static str),
    Remote,
}
#[derive(Component)]
pub(crate) struct Summary;
#[derive(Component)]
pub(crate) struct Status;
#[derive(Component)]
pub(crate) struct Output;
#[derive(Component)]
pub(crate) struct Rows;
#[derive(Component)]
pub(crate) struct Confirm;
#[derive(Resource, Default)]
pub(crate) struct View {
    host: Option<Entity>,
    form: u64,
    rows: u64,
    output_revision: u64,
}

fn text(c: &mut Commands, p: Entity, s: impl Into<String>, size: f32) -> Entity {
    c.spawn((
        label(s, size),
        Node {
            flex_shrink: 0.,
            ..default()
        },
        ChildOf(p),
    ))
    .id()
}
fn row(c: &mut Commands, p: Entity) -> Entity {
    c.spawn((
        Node {
            column_gap: px(6.),
            row_gap: px(5.),
            flex_wrap: FlexWrap::Wrap,
            align_items: AlignItems::Center,
            flex_shrink: 0.,
            ..default()
        },
        ChildOf(p),
    ))
    .id()
}
fn action(c: &mut Commands, p: Entity, title: &str, a: Click) {
    button(c, p, title, a);
}
fn invoke(c: &mut Commands, p: Entity, title: &str, key: &'static str) {
    action(c, p, title, Click::Invoke(key));
}
fn table_row(
    c: &mut Commands,
    parent: Entity,
    columns: &[(String, f32)],
    click: Click,
    color: Color,
) -> Entity {
    let e = button(c, parent, "", click);
    c.entity(e).entry::<Node>().and_modify(|mut n| {
        n.width = percent(100.);
        n.min_height = px(25.);
        n.justify_content = JustifyContent::Start;
        n.overflow = Overflow::clip();
    });
    for (value, width) in columns {
        let width = *width;
        let cell = text(c, e, value, 12.);
        c.entity(cell).insert(TextColor(color));
        c.entity(cell).entry::<Node>().and_modify(move |mut n| {
            n.min_width = px(0.);
            if width > 0. {
                n.width = px(width);
            } else {
                n.flex_grow = 1.;
                n.flex_shrink = 1.;
            }
        });
    }
    e
}
fn field(
    c: &mut Commands,
    p: Entity,
    title: &str,
    key: &'static str,
    state: &State,
    multi: bool,
) -> Entity {
    text(c, p, title, 12.);
    let e = property::input(c, p, Field(key), &state.field(key));
    c.entity(e).insert((
        BackgroundColor(Color::srgb(0.12, 0.14, 0.15)),
        BorderColor::all(EDGE),
    ));
    if multi {
        c.entity(e).insert(EditableText {
            allow_newlines: true,
            max_characters: Some(8192),
            ..EditableText::new(state.field(key))
        });
        c.entity(e)
            .entry::<Node>()
            .and_modify(|mut n| n.height = px(65.));
    }
    e
}
fn operation(key: &str, s: &State) -> Result<Operation, String> {
    let selected = || s.selected.iter().cloned().collect::<Vec<_>>();
    let branch = || {
        s.snapshot
            .branches
            .iter()
            .find(|b| b.name == s.branch)
            .ok_or("Select a branch".to_string())
    };
    Ok(match key {
        "stage" | "unstage" | "discard" => {
            let paths = selected();
            if paths.is_empty() {
                return Err("Select one or more files".into());
            }
            match key {
                "stage" => Operation::Stage(paths),
                "unstage" => Operation::Unstage(paths),
                _ => Operation::Discard(paths),
            }
        }
        "stage_all" => Operation::Stage(vec![]),
        "unstage_all" => Operation::Unstage(vec![]),
        "diff" | "diff_staged" => Operation::Diff(
            s.selected.iter().next().ok_or("Select a file")?.clone(),
            key == "diff_staged",
        ),
        "commit" | "commit_push" => Operation::Commit(s.field("message"), key == "commit_push"),
        "new" | "new_switch" => {
            Operation::CreateBranch(s.field("branch_name"), key == "new_switch")
        }
        "switch" => {
            let b = branch()?;
            Operation::Switch(b.name.clone(), b.remote)
        }
        "merge" => Operation::Merge(branch()?.name.clone()),
        "rename" => {
            let b = branch()?;
            if b.remote {
                return Err("Select a local branch to rename".into());
            }
            Operation::Rename(b.name.clone(), s.field("branch_name"))
        }
        "delete" | "force_delete" => {
            let b = branch()?;
            if b.remote || b.current {
                return Err("Select another local branch to delete".into());
            }
            Operation::Delete(b.name.clone(), key == "force_delete")
        }
        "apply" => Operation::Configure(s.config()),
        "test" => Operation::TestRemote(s.field("origin")),
        "clone" => {
            let value = s.field("destination");
            if value.trim().is_empty() {
                return Err("Enter a clone destination".into());
            }
            let path = std::path::PathBuf::from(value);
            Operation::Clone(
                s.field("url"),
                if path.is_absolute() {
                    path
                } else {
                    s.project.join(path)
                },
            )
        }
        "browse" => Operation::Browse,
        "continue_merge" => Operation::Continue(false),
        "abort_merge" => Operation::Abort(false),
        "continue_rebase" => Operation::Continue(true),
        "abort_rebase" => Operation::Abort(true),
        "execute" => match s.page {
            Page::Init => Operation::Init,
            Page::Push => Operation::Push,
            Page::Pull => Operation::Pull(false),
            Page::Rebase => Operation::Pull(true),
            Page::Fetch => Operation::Fetch,
            Page::Stash => Operation::Stash,
            Page::Pop => Operation::Pop,
            Page::Ignore => Operation::Ignore,
            _ => return Err("Select an operation".into()),
        },
        _ => return Err("Unknown Git action".into()),
    })
}
pub(crate) fn collect(
    mut state: ResMut<State>,
    fields: Query<(&Field, &EditableText)>,
    buttons: Query<(&Interaction, &Click), Changed<Interaction>>,
    keys: Res<ButtonInput<KeyCode>>,
) {
    if !state.open {
        return;
    }
    for (field, value) in &fields {
        let value = value.value().to_string();
        if state.fields.get(field.0) != Some(&value) {
            state.fields.insert(field.0.into(), value);
        }
    }
    if keys.just_pressed(KeyCode::Escape) && !state.busy {
        let action = if state.confirm.is_some() {
            Action::Dismiss
        } else {
            Action::Close
        };
        state.actions.push_back(action);
    }
    for (interaction, click) in &buttons {
        if *interaction != Interaction::Pressed {
            continue;
        }
        if state.busy && !matches!(click, Click::Action(Action::Cancel)) {
            continue;
        }
        if state.confirm.is_some()
            && !matches!(click, Click::Action(Action::Confirm | Action::Dismiss))
        {
            continue;
        }
        match click {
            Click::Action(a) => state.actions.push_back(a.clone()),
            Click::Invoke(key) => match operation(key, &state) {
                Ok(op) => state.actions.push_back(Action::Run(op)),
                Err(e) => state.status = e,
            },
            Click::Remote => {
                state.show_remote = !state.show_remote;
                state.revision += 1;
            }
        }
    }
}
fn form(c: &mut Commands, s: &State, focus: &mut Option<ResMut<InputFocus>>) -> Entity {
    let overlay = c
        .spawn((
            Node {
                position_type: PositionType::Absolute,
                width: percent(100.),
                height: percent(100.),
                align_items: AlignItems::Center,
                justify_content: JustifyContent::Center,
                ..default()
            },
            GlobalZIndex(400),
            BackgroundColor(Color::srgba(0., 0., 0., 0.6)),
            TabGroup::modal(),
        ))
        .id();
    let card = c
        .spawn((
            Node {
                width: px(1040.),
                max_width: percent(95.),
                height: px(790.),
                max_height: percent(94.),
                padding: px(18.).all(),
                row_gap: px(8.),
                flex_direction: FlexDirection::Column,
                border: px(1.).all(),
                ..default()
            },
            BorderColor::all(EDGE),
            BackgroundColor(BG),
            ChildOf(overlay),
        ))
        .id();
    text(c, card, format!("Git  ·  {}", s.page.title()), 22.);
    let summary = text(c, card, "", 12.);
    c.entity(summary).insert(Summary);
    let navigation = row(c, card);
    for page in [Page::Commit, Page::Branches, Page::History, Page::Config] {
        action(
            c,
            navigation,
            page.title(),
            Click::Action(Action::Open(page)),
        );
    }
    action(c, navigation, "Refresh", Click::Action(Action::Refresh));
    let body = property::scroll_column(c, card);
    let mut first = None;
    match s.page {
        Page::Commit => {
            text(
                c,
                body,
                "Select files to stage. Index and worktree columns show separate changes.",
                12.,
            );
            text(c, body, "SELECT     INDEX / WORKTREE       FILE", 11.);
            let list = c
                .spawn((
                    Rows,
                    Node {
                        flex_direction: FlexDirection::Column,
                        min_height: px(150.),
                        row_gap: px(2.),
                        ..default()
                    },
                    ChildOf(body),
                ))
                .id();
            let _ = list;
            let tools = row(c, body);
            for (name, key) in [
                ("Stage Selected", "stage"),
                ("Unstage Selected", "unstage"),
                ("Stage All", "stage_all"),
                ("Unstage All", "unstage_all"),
                ("Discard Changes", "discard"),
                ("Diff", "diff"),
                ("Staged Diff", "diff_staged"),
            ] {
                invoke(c, tools, name, key);
            }
            first = Some(field(c, body, "Commit Message:", "message", s, true));
            let tools = row(c, body);
            invoke(c, tools, "Commit", "commit");
            invoke(c, tools, "Commit & Push", "commit_push");
            let tools = row(c, body);
            for (name, key) in [
                ("Continue Merge", "continue_merge"),
                ("Abort Merge", "abort_merge"),
                ("Continue Rebase", "continue_rebase"),
                ("Abort Rebase", "abort_rebase"),
            ] {
                invoke(c, tools, name, key);
            }
        }
        Page::Branches => {
            action(c, body, "Show / hide remote branches", Click::Remote);
            c.spawn((
                Rows,
                Node {
                    flex_direction: FlexDirection::Column,
                    min_height: px(130.),
                    ..default()
                },
                ChildOf(body),
            ));
            first = Some(field(
                c,
                body,
                "New branch / rename to:",
                "branch_name",
                s,
                false,
            ));
            let tools = row(c, body);
            for (name, key) in [
                ("New Branch", "new"),
                ("New & Switch", "new_switch"),
                ("Switch To", "switch"),
                ("Merge Into Current", "merge"),
                ("Rename", "rename"),
                ("Delete", "delete"),
                ("Force Delete…", "force_delete"),
            ] {
                invoke(c, tools, name, key);
            }
        }
        Page::History => {
            first = Some(field(
                c,
                body,
                "Show last commits (1–500), then Refresh:",
                "limit",
                s,
                false,
            ));
            text(
                c,
                body,
                "COMMIT          AUTHOR                       DATE                   MESSAGE",
                11.,
            );
            c.spawn((
                Rows,
                Node {
                    flex_direction: FlexDirection::Column,
                    ..default()
                },
                ChildOf(body),
            ));
        }
        Page::Config => {
            text(c, body, "Global identity", 16.);
            first = Some(field(c, body, "User Name:", "name", s, false));
            field(c, body, "User Email:", "email", s, false);
            field(c, body, "Default Branch:", "default_branch", s, false);
            field(
                c,
                body,
                "Credential Helper (manager / wincred / osxkeychain / cache / store / libsecret):",
                "helper",
                s,
                false,
            );
            text(c, body, "Project remote", 16.);
            field(c, body, "Remote URL (origin):", "origin", s, false);
            text(
                c,
                body,
                "Authentication uses your existing Git credentials or SSH agent. No passwords are stored by this IDE.",
                12.,
            );
            let tools = row(c, body);
            invoke(c, tools, "Test Connection", "test");
            invoke(c, tools, "Apply", "apply");
        }
        Page::Clone => {
            first = Some(field(c, body, "Repository URL:", "url", s, false));
            field(
                c,
                body,
                "Clone Into (new directory):",
                "destination",
                s,
                false,
            );
            let tools = row(c, body);
            invoke(c, tools, "Browse…", "browse");
            invoke(c, tools, "Clone", "clone");
            action(
                c,
                tools,
                "Open Cloned Project",
                Click::Action(Action::OpenClone),
            );
        }
        _ => {
            text(
                c,
                body,
                match s.page {
                    Page::Init => "Initialize Git in the current project directory.",
                    Page::Push => {
                        "Push current branch. If no upstream exists, set origin as its upstream."
                    }
                    Page::Pull => "Fetch and merge the upstream branch into the current branch.",
                    Page::Rebase => "Fetch and replay local commits onto the upstream branch.",
                    Page::Fetch => "Fetch updates from all remotes without changing working files.",
                    Page::Stash => {
                        "Save tracked changes in the stash. Untracked files remain in place."
                    }
                    Page::Pop => {
                        "Apply the latest stash and remove it only if application succeeds."
                    }
                    _ => "Create a SceneMax .gitignore. An existing file is preserved.",
                },
                14.,
            );
            if matches!(s.page, Page::Stash | Page::Pop) {
                text(
                    c,
                    body,
                    if s.snapshot.stashes.is_empty() {
                        "No saved stashes"
                    } else {
                        &s.snapshot.stashes
                    },
                    12.,
                );
            }
            invoke(c, body, s.page.title(), "execute");
        }
    }
    let status = text(c, card, "", 12.);
    c.entity(status).insert(Status);
    let logframe = c
        .spawn((
            Node {
                height: px(190.),
                min_height: px(100.),
                flex_shrink: 0.,
                flex_direction: FlexDirection::Column,
                border: px(1.).all(),
                ..default()
            },
            BorderColor::all(EDGE),
            BackgroundColor(Color::srgb(0.10, 0.12, 0.13)),
            ChildOf(card),
        ))
        .id();
    let log = property::scroll_column(c, logframe);
    let output = text(c, log, "", 12.);
    c.entity(output).insert(Output);
    let footer = row(c, card);
    action(c, footer, "Cancel Operation", Click::Action(Action::Cancel));
    action(c, footer, "Close", Click::Action(Action::Close));
    if let (Some(field), Some(focus)) = (first, focus.as_mut()) {
        focus.set(field, FocusCause::Navigated);
    }
    overlay
}
type OutputText<'w, 's> =
    Query<'w, 's, &'static mut Text, (With<Output>, Without<Summary>, Without<Status>)>;
#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct Widgets<'w, 's> {
    lists: Query<'w, 's, (Entity, Option<&'static Children>), With<Rows>>,
    confirm: Query<'w, 's, Entity, With<Confirm>>,
    summary: Query<'w, 's, &'static mut Text, With<Summary>>,
    status: Query<'w, 's, &'static mut Text, (With<Status>, Without<Summary>)>,
    output: OutputText<'w, 's>,
}
pub(crate) fn refresh(
    mut c: Commands,
    state: Res<State>,
    mut view: ResMut<View>,
    mut focus: Option<ResMut<InputFocus>>,
    widgets: Widgets,
) {
    let Widgets {
        lists,
        confirm,
        mut summary,
        mut status,
        mut output,
    } = widgets;
    if !state.open {
        for e in &confirm {
            c.entity(e).despawn();
        }
        if let Some(host) = view.host.take() {
            c.entity(host).despawn();
        }
        return;
    }
    if view.host.is_none() || view.form != state.form_revision {
        if let Some(host) = view.host.take() {
            c.entity(host).despawn();
        }
        view.host = Some(form(&mut c, &state, &mut focus));
        view.form = state.form_revision;
        view.rows = u64::MAX;
        view.output_revision = u64::MAX;
        return;
    }
    for mut text in &mut summary {
        let root = state.snapshot.root.as_deref().unwrap_or(&state.project);
        let value = format!(
            "{}  ·  Branch: {}  ·  {} changed files  ·  {}\nRepository: {}\nOrigin: {}",
            state.snapshot.version,
            state.snapshot.branch,
            state.snapshot.files.len(),
            state.snapshot.upstream,
            root.display(),
            if state.snapshot.config.origin.is_empty() {
                "(no remote configured)"
            } else {
                &state.snapshot.config.origin
            }
        );
        if text.0 != value {
            text.0 = value;
        }
    }
    for mut text in &mut status {
        if text.0 != state.status {
            text.0 = state.status.clone();
        }
    }
    if view.output_revision != state.output_revision {
        view.output_revision = state.output_revision;
        for mut text in &mut output {
            let mut value = state.output.chars().take(24000).collect::<String>();
            if state.output.chars().count() > 24000 {
                value.push_str("\n[Display limited to 24,000 characters]");
            }
            if text.0 != value {
                text.0 = value;
            }
        }
    }
    if view.rows == state.revision {
        return;
    }
    view.rows = state.revision;
    for (list, children) in &lists {
        if let Some(children) = children {
            for child in children.iter() {
                c.entity(child).despawn();
            }
        }
        match state.page {
            Page::Commit => {
                if state.snapshot.files.is_empty() {
                    text(
                        &mut c,
                        list,
                        if state.busy {
                            "Loading repository…"
                        } else if state.snapshot.root.is_none() {
                            "No repository found. Use Git → Initialize Repository or Clone Repository."
                        } else {
                            "Working tree clean"
                        },
                        13.,
                    );
                }
                for file in &state.snapshot.files {
                    let selected = state.selected.contains(&file.path);
                    let status = format!("{} / {}", file.index, file.worktree);
                    let path = format!(
                        "{}{}",
                        file.path,
                        file.original
                            .as_ref()
                            .map(|old| format!(" ← {old}"))
                            .unwrap_or_default()
                    );
                    let color = if file.conflict() {
                        Color::srgb(1., 0.55, 0.35)
                    } else if file.index == 'D' || file.worktree == 'D' {
                        Color::srgb(1., 0.55, 0.55)
                    } else if file.index == '?' {
                        MUTED
                    } else {
                        Color::srgb(0.5, 0.85, 0.55)
                    };
                    let e = table_row(
                        &mut c,
                        list,
                        &[
                            (if selected { "☑" } else { "☐" }.into(), 50.),
                            (status, 110.),
                            (path, 0.),
                        ],
                        Click::Action(Action::SelectFile(file.path.clone())),
                        color,
                    );
                    c.entity(e).insert((
                        BackgroundColor(if selected { ACCENT } else { PANEL }),
                        scenemax_ide_ui::ButtonSurface(if selected { ACCENT } else { PANEL }),
                    ));
                }
            }
            Page::Branches => {
                for branch in state
                    .snapshot
                    .branches
                    .iter()
                    .filter(|b| state.show_remote || !b.remote)
                {
                    let e = button(
                        &mut c,
                        list,
                        &format!(
                            "{} {}    {}",
                            if branch.current { "●" } else { " " },
                            branch.name,
                            branch.upstream
                        ),
                        Click::Action(Action::SelectBranch(branch.name.clone())),
                    );
                    if state.branch == branch.name {
                        c.entity(e).insert((
                            BackgroundColor(ACCENT),
                            scenemax_ide_ui::ButtonSurface(ACCENT),
                        ));
                    }
                }
            }
            Page::History => {
                for commit in &state.snapshot.commits {
                    button(
                        &mut c,
                        list,
                        &format!(
                            "{}   {}   {}   {}",
                            commit.hash.chars().take(8).collect::<String>(),
                            commit.author,
                            commit.date,
                            commit.subject
                        ),
                        Click::Action(Action::Run(Operation::Show(commit.hash.clone()))),
                    );
                }
            }
            _ => {}
        }
    }
    for e in &confirm {
        c.entity(e).despawn();
    }
    if let Some(op) = &state.confirm {
        let host = c
            .spawn((
                Confirm,
                Node {
                    position_type: PositionType::Absolute,
                    width: percent(100.),
                    height: percent(100.),
                    align_items: AlignItems::Center,
                    justify_content: JustifyContent::Center,
                    ..default()
                },
                GlobalZIndex(410),
                BackgroundColor(Color::srgba(0., 0., 0., 0.7)),
                TabGroup::modal(),
            ))
            .id();
        let card = c
            .spawn((
                Node {
                    width: px(660.),
                    max_width: percent(90.),
                    padding: px(24.).all(),
                    row_gap: px(14.),
                    flex_direction: FlexDirection::Column,
                    ..default()
                },
                BackgroundColor(PANEL),
                ChildOf(host),
            ))
            .id();
        text(&mut c, card, "Confirm Git operation", 20.);
        let description=match op {Operation::Discard(paths)=>format!("Discard changes in these files? Staged contents are kept. Untracked files are moved to a recovery folder inside .git.\n{}",paths.join("\n")),Operation::Delete(name,force)=>format!("{} local branch '{name}'?",if *force{"Force delete (including unmerged commits)"}else{"Delete"}),Operation::Configure(_)=>"Apply global Git identity/default branch/credential helper and this project's origin?".into(),Operation::Merge(name)=>format!("Merge '{name}' into the current branch?"),Operation::Push|Operation::Commit(_,true)=>"Push the current branch to its configured upstream (or origin for a new branch)?".into(),Operation::Abort(_)=>"Abort the current merge/rebase? This can remove conflict-resolution edits.".into(),Operation::Stash=>"Stash tracked changes? Untracked files will remain.".into(),Operation::Pop=>"Apply the latest stash to the working tree? Conflicts will be shown in Stage & Commit.".into(),_=>"Continue?".into()};
        text(&mut c, card, description, 14.);
        let buttons = row(&mut c, card);
        action(&mut c, buttons, "Confirm", Click::Action(Action::Confirm));
        action(&mut c, buttons, "Cancel", Click::Action(Action::Dismiss));
    }
}
pub(crate) fn smoke(
    mut state: ResMut<State>,
    services: Res<crate::application::EditorServices>,
    mut frames: Local<u32>,
    mut once: Local<bool>,
) {
    *frames += 1;
    if *frames < 90 || services.storage.is_pending() || services.catalog_storage.is_pending() {
        return;
    }
    if !*once {
        let page = match std::env::var("SCENEMAX_SMOKE_GIT").as_deref() {
            Ok("branches") => Page::Branches,
            Ok("history") => Page::History,
            Ok("config") => Page::Config,
            Ok("clone") => Page::Clone,
            _ => Page::Commit,
        };
        state.actions.push_back(Action::Open(page));
        *once = true;
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn git_inspection_restarts_after_workspace_root_changes() {
        let (mut app, _dir, _) = crate::tests::app();
        app.world_mut()
            .resource_mut::<State>()
            .actions
            .push_back(Action::Open(Page::Commit));
        app.update();
        let other = tempfile::tempdir().unwrap();
        let project = scenemax_ide_services::Filesystem::open_project(other.path()).unwrap();
        let root = project.root().to_owned();
        app.world_mut()
            .resource_mut::<crate::application::Session>()
            .workspace
            .switch_project(project)
            .unwrap();
        let deadline = std::time::Instant::now() + std::time::Duration::from_secs(20);
        loop {
            app.update();
            let state = app.world().resource::<State>();
            if !state.busy && state.project == root {
                break;
            }
            assert!(
                std::time::Instant::now() < deadline,
                "Git inspection remained stale"
            );
            std::thread::sleep(std::time::Duration::from_millis(10));
        }
        assert!(!app.world().resource::<State>().snapshot.version.is_empty());
    }
    #[test]
    fn git_modal_retains_message_blocks_editor_shortcuts_and_guards_dirty_buffers() {
        for entry in super::super::menu_catalog::MENUS
            .iter()
            .find(|e| e.name == "Git")
            .unwrap()
            .children
            .iter()
            .filter(|e| !e.command.is_empty())
        {
            assert!(Page::menu(entry.command).is_some(), "{}", entry.command);
        }
        let (mut app, _dir, _) = crate::tests::app();
        {
            let project = app
                .world()
                .resource::<crate::application::Session>()
                .workspace
                .project()
                .root()
                .to_owned();
            let mut state = app.world_mut().resource_mut::<State>();
            state.open = true;
            state.project = project;
            state.form_revision = 1;
            state
                .snapshot
                .files
                .push(scenemax_ide_core::git::FileStatus {
                    index: ' ',
                    worktree: 'M',
                    path: "scripts/main".into(),
                    original: None,
                });
        }
        app.update();
        app.update();
        let w = app.world_mut();
        let field = w
            .query::<(Entity, &Field)>()
            .iter(w)
            .find(|(_, f)| f.0 == "message")
            .unwrap()
            .0;
        *w.get_mut::<EditableText>(field).unwrap() = EditableText::new("Keep this message");
        w.resource_mut::<State>()
            .actions
            .push_back(Action::SelectFile("scripts/main".into()));
        app.update();
        app.update();
        assert_eq!(
            app.world().resource::<State>().field("message"),
            "Keep this message"
        );
        assert!(app.world().get::<Field>(field).is_some());
        app.world_mut()
            .resource_mut::<ButtonInput<KeyCode>>()
            .press(KeyCode::F8);
        let status = app
            .world()
            .resource::<crate::application::Session>()
            .status
            .clone();
        app.update();
        assert_eq!(
            app.world().resource::<crate::application::Session>().status,
            status
        );
        {
            let mut session = app
                .world_mut()
                .resource_mut::<crate::application::Session>();
            let id = session.workspace.require_active().unwrap();
            session
                .workspace
                .document_mut(id)
                .unwrap()
                .replace_text("// unsaved".into());
        }
        app.world_mut()
            .resource_mut::<State>()
            .actions
            .push_back(Action::Run(Operation::Stage(vec![])));
        app.update();
        assert!(app.world().resource::<State>().status.contains("Save all"));
        assert!(!app.world().resource::<State>().busy);
        app.world_mut()
            .resource_mut::<State>()
            .actions
            .push_back(Action::Run(Operation::Delete("feature".into(), true)));
        app.update();
        assert!(app.world().resource::<State>().confirm.is_some());
        app.world_mut()
            .resource_mut::<State>()
            .actions
            .push_back(Action::Dismiss);
        app.update();
        app.world_mut()
            .resource_mut::<State>()
            .actions
            .push_back(Action::Close);
        app.update();
        assert!(!app.world().resource::<State>().open);
        assert!(app.world().get_entity(field).is_err());
    }
}
