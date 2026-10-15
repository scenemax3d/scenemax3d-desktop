//! Main-thread MCP orchestration. Uses normal document history and command/save paths.
use super::{Command, EditorServices, Session, ViewChange};
use bevy::prelude::*;
use scenemax_ide_core::{DocumentId, automation};
use scenemax_ide_services::mcp::{
    Call, Server,
    disk::{Output, Request, Worker},
};
use serde_json::{Value, json};
use std::{path::PathBuf, time::Instant};
#[derive(Resource, Default)]
pub(crate) struct State {
    pub(crate) server: Option<Server>,
    worker: Option<Worker>,
    pending: Option<Pending>,
    pub(crate) status: String,
    pub(crate) monitor: bool,
    pub(crate) overlay_image: Option<scenemax_ide_services::mcp::disk::ImageData>,
    pub(crate) view_request: Option<(String, Value)>,
    pub(crate) view_result: Option<Result<Value, String>>,
}
struct Pending {
    call: Call,
    root: PathBuf,
    phase: Phase,
}
enum Phase {
    Load,
    Disk,
    Ready,
    Save(DocumentId),
    Command,
    View,
}
impl State {
    pub(crate) fn unavailable(status: String) -> Self {
        Self {
            status,
            ..default()
        }
    }
    pub(crate) fn start(port: u16, token: Option<String>) -> Self {
        match Server::start(port, token).and_then(|s| Worker::new().map(|w| (s, w))) {
            Ok((s, w)) => Self {
                status: format!("MCP: {}", s.endpoint),
                server: Some(s),
                worker: Some(w),
                ..default()
            },
            Err(e) => Self {
                status: format!("MCP unavailable: {e}"),
                ..default()
            },
        }
    }
    pub(crate) fn logs(&self) -> Vec<String> {
        self.server
            .as_ref()
            .and_then(|s| s.log.lock().ok())
            .map(|v| v.iter().cloned().collect())
            .unwrap_or_default()
    }
}
fn revision(doc: &scenemax_ide_core::Document) -> String {
    use std::hash::{Hash, Hasher};
    let mut hash = std::collections::hash_map::DefaultHasher::new();
    doc.path().hash(&mut hash);
    doc.text().hash(&mut hash);
    format!("{:?}-{:016x}", doc.revision(), hash.finish())
}
fn document(session: &Session) -> Result<Value, String> {
    let id = session
        .workspace
        .require_active()
        .map_err(|e| e.to_string())?;
    let d = session.workspace.document(id).map_err(|e| e.to_string())?;
    Ok(json!({"path":d.path(),"content":d.text(),"revision":revision(d),"dirty":d.is_dirty()}))
}
fn docs(session: &Session) -> Value {
    json!(
        session
            .workspace
            .documents()
            .map(|(_, d)| json!({"path":d.path(),"revision":revision(d),"dirty":d.is_dirty()}))
            .collect::<Vec<_>>()
    )
}
fn readonly(name: &str) -> bool {
    automation::tools()
        .iter()
        .find(|t| t["name"] == name)
        .is_some_and(|t| t["annotations"]["readOnlyHint"] == true)
}
fn snapshots(session: &Session) -> Vec<scenemax_ide_core::Document> {
    session
        .workspace
        .documents()
        .map(|(_, d)| d.snapshot())
        .collect()
}
fn submit(state: &State, session: &Session, name: &str, args: Value) -> Result<(), String> {
    state
        .worker
        .as_ref()
        .ok_or("MCP disk worker unavailable")?
        .submit(Request {
            root: session.workspace.project().root().to_owned(),
            name: name.into(),
            args,
            buffers: snapshots(session),
        })
}
fn guard(session: &Session, args: &Value) -> Result<DocumentId, String> {
    let id = session
        .workspace
        .require_active()
        .map_err(|e| e.to_string())?;
    let d = session.workspace.document(id).map_err(|e| e.to_string())?;
    let expected = args["expected_revision"].as_str();
    let revision = revision(d);
    if expected.is_some_and(|v| v != revision)
        || (d.is_dirty() && expected != Some(revision.as_str()))
    {
        return Err(
            "Revision conflict: read the buffer and provide expected_revision to edit unsaved work"
                .into(),
        );
    }
    Ok(id)
}
fn finish(p: Pending, result: Result<Value, String>) {
    let _ = p.call.reply.send(result);
}
pub(crate) fn update(
    mut state: ResMut<State>,
    mut session: ResMut<Session>,
    mut services: ResMut<EditorServices>,
    mut changes: MessageWriter<ViewChange>,
    mut exit: MessageWriter<AppExit>,
) {
    let Some(mut pending) = state.pending.take().or_else(|| {
        state
            .server
            .as_ref()
            .and_then(Server::poll)
            .map(|call| Pending {
                call,
                root: session.workspace.project().root().to_owned(),
                phase: Phase::Ready,
            })
    }) else {
        return;
    };
    if pending.call.deadline <= Instant::now() || pending.root != session.workspace.project().root()
    {
        if matches!(pending.phase, Phase::Load | Phase::Disk)
            && state.worker.as_ref().and_then(Worker::poll).is_none()
        {
            state.pending = Some(pending);
            return;
        }
        if matches!(pending.phase, Phase::View)
            && state.view_result.is_none()
            && state.view_request.is_none()
        {
            state.pending = Some(pending);
            return;
        }
        state.view_request = None;
        state.view_result = None;
        finish(
            pending,
            Err("Request expired or project changed; inspect state before retrying".into()),
        );
        return;
    }
    let outcome = (|| -> Result<Option<Value>, String> {
        match pending.phase {
            Phase::Load | Phase::Disk => {
                let Some(result) = state.worker.as_ref().and_then(Worker::poll) else {
                    return Ok(None);
                };
                match result? {
                    Output::Json(v) => return Ok(Some(v)),
                    Output::Image(image) => {
                        state.overlay_image = Some(image);
                        state.view_result = None;
                        state.view_request =
                            Some((pending.call.name.clone(), pending.call.arguments.clone()));
                        pending.phase = Phase::View;
                        return Ok(None);
                    }
                    Output::Document(doc) => {
                        let path = doc.path().to_owned();
                        pending.call.arguments["path"] = json!(path);
                        let id = if let Some(id) = session.workspace.find_document(&path) {
                            session.workspace.select(id).map_err(|e| e.to_string())?;
                            id
                        } else {
                            let id = session
                                .workspace
                                .open_document(doc)
                                .map_err(|e| e.to_string())?;
                            changes.write(ViewChange::DocumentOpened(id));
                            id
                        };
                        changes.write(ViewChange::ActiveChanged);
                        if matches!(pending.phase, Phase::Disk) {
                            changes.write(ViewChange::ProjectTreeChanged);
                            services.storage_outcome = None;
                            services
                                .storage
                                .request(scenemax_ide_services::StorageRequest::Refresh(
                                    pending.root.clone(),
                                ))
                                .map_err(|e| e.to_string())?;
                            pending.phase = Phase::Command;
                            return Ok(None);
                        }
                        let _ = id;
                        pending.phase = Phase::Ready;
                    }
                }
            }
            Phase::Save(id) => {
                if services.storage_outcome.is_none() && services.storage.is_pending() {
                    return Ok(None);
                }
                if let Some(Err(e)) = services.storage_outcome.take() {
                    return Err(e);
                }
                let doc = session.workspace.document(id).map_err(|e| e.to_string())?;
                if doc.is_dirty() {
                    return Err(
                        "Save did not include newer edits; inspect the document before retrying"
                            .into(),
                    );
                }
                return Ok(Some(
                    json!({"path":doc.path(),"revision":revision(doc),"saved":true,"status":session.status}),
                ));
            }
            Phase::Command => {
                if services.storage_outcome.is_none() && services.storage.is_pending() {
                    return Ok(None);
                }
                if let Some(Err(e)) = services.storage_outcome.take() {
                    return Err(e);
                }
                if pending.call.name == "run.preview_scene" && !services.projector.is_running() {
                    return Err(format!("Preview did not start: {}", session.status));
                }
                return Ok(Some(
                    json!({"status":session.status,"running":services.projector.is_running(),"documents":docs(&session)}),
                ));
            }
            Phase::View => return state.view_result.take().transpose(),
            Phase::Ready => {}
        }
        if !matches!(
            pending.call.name.as_str(),
            "run.stop"
                | "project.get_context"
                | "ide.get_status"
                | "ide.get_recent_errors"
                | "runtime.get_issue_log"
        ) && (services.storage.is_pending()
            || services.catalog_storage.is_pending()
            || services.is_saving()
            || session.asset_operation_pending
            || session.composing)
        {
            return Err("IDE is busy; retry after current edit or file operation completes".into());
        }
        let name = pending.call.name.as_str();
        let args = &pending.call.arguments;
        if args["project_root"]
            .as_str()
            .is_some_and(|root| std::path::Path::new(root) != pending.root)
        {
            return Err("Project context changed; call project.get_context again".into());
        }

        if let Some(path) = args["path"].as_str().filter(|_| name != "file.create") {
            let p = scenemax_ide_services::mcp::disk::path(&pending.root, args, "path")?;
            if session
                .workspace
                .active_id()
                .and_then(|id| session.workspace.document(id).ok())
                .is_none_or(|d| d.path() != p)
            {
                // Disk resolution validates even aliases/junctions before adopting a buffer.
                let _ = path;
                submit(&state, &session, "load", args.clone())?;
                pending.phase = Phase::Load;
                return Ok(None);
            }
        }
        let result = match name {
            "designer.overlay_reference_image" => {
                if args["visible"] == false {
                    state.view_result = None;
                    state.view_request = Some((name.into(), args.clone()));
                    pending.phase = Phase::View;
                } else {
                    submit(&state, &session, name, args.clone())?;
                    pending.phase = Phase::Disk;
                }
                None
            }
            "project.get_context" => Some(
                json!({"root":pending.root,"documents":docs(&session),"active":document(&session).ok().map(|mut d|{d.as_object_mut().map(|o|o.remove("content"));d}),"running":services.projector.is_running(),"mcp":state.status,"language":"SceneMax choreographic programming language"}),
            ),
            "editor.get_active_document" | "file.read" | "editor.open_file" => {
                Some(document(&session)?)
            }
            "editor.list_documents" => Some(json!({"documents":docs(&session)})),
            "project.list_tree" | "project.search_files" => {
                let query = args["query"].as_str().unwrap_or("").to_lowercase();
                let filter = args["root"].as_str().unwrap_or("").replace('\\', "/");
                let offset = args["offset"].as_u64().unwrap_or(0) as usize;
                let limit = args["maxResults"]
                    .as_u64()
                    .or(args["maxEntries"].as_u64())
                    .unwrap_or(200)
                    .clamp(1, 1000) as usize;
                let entries: Vec<_> = session
                    .workspace
                    .project()
                    .entries()
                    .iter()
                    .filter_map(|e| {
                        let p = e
                            .path
                            .strip_prefix(&pending.root)
                            .ok()?
                            .to_string_lossy()
                            .replace('\\', "/");
                        (p.to_lowercase().contains(&query) && p.starts_with(&filter))
                            .then(|| json!({"path":p,"directory":e.is_directory}))
                    })
                    .collect();
                let next = offset.saturating_add(limit);
                Some(
                    json!({"entries":entries.iter().skip(offset).take(limit).collect::<Vec<_>>(),"next_offset":if next<entries.len(){Some(next)}else{None},"truncated":session.workspace.project().tree_truncated()}),
                )
            }
            "ide.get_status" | "ide.get_recent_errors" | "runtime.get_issue_log" => {
                let n = args["limit"].as_u64().unwrap_or(100).min(2000) as usize;
                Some(
                    json!({"status":session.status,"running":services.projector.is_running(),"output":session.output.iter().rev().take(n).collect::<Vec<_>>(),"mcp_log":state.logs().into_iter().rev().take(n).collect::<Vec<_>>()}),
                )
            }
            "ui.get_schema" => Some(automation::inspect(
                name,
                &automation::ui_template().to_string(),
                args,
            )?),
            "editor.check"
            | "designer.compare_to_reference"
            | "file.create"
            | "designer.create_document"
            | "scene.create"
            | "project.search_text"
            | "designer.list_available_materials"
            | "asset.list_models"
            | "ui.list_fonts"
            | "ui.list_sprites"
            | "designer.validate_resources" => {
                let mut buffers = snapshots(&session);
                if name == "designer.validate_resources" || name == "editor.check" {
                    let id = session
                        .workspace
                        .require_active()
                        .map_err(|e| e.to_string())?;
                    buffers = vec![
                        session
                            .workspace
                            .document(id)
                            .map_err(|e| e.to_string())?
                            .snapshot(),
                    ];
                }
                state
                    .worker
                    .as_ref()
                    .ok_or("Worker unavailable")?
                    .submit(Request {
                        root: pending.root.clone(),
                        name: name.into(),
                        args: {
                            let mut a = args.clone();
                            if name == "editor.check" {
                                a["checked_revision"] = document(&session)?["revision"].clone();
                            }
                            a
                        },
                        buffers,
                    })?;
                pending.phase = Phase::Disk;
                None
            }
            "designer.get_view_state"
            | "designer.adjust_view"
            | "designer.use_view_gizmo"
            | "designer.select_entity"
            | "designer.capture_canvas"
            | "designer.capture_canvas_reliable"
            | "designer.capture_thumbnail" => {
                state.view_result = None;
                state.view_request = Some((name.into(), args.clone()));
                pending.phase = Phase::View;
                None
            }
            "editor.undo" | "editor.redo" => {
                let id = guard(&session, args)?;
                let doc = session
                    .workspace
                    .document_mut(id)
                    .map_err(|e| e.to_string())?;
                let changed = if name.ends_with("undo") {
                    doc.undo()
                } else {
                    doc.redo()
                };
                changes.write(ViewChange::BufferChanged(id));
                Some(json!({"changed":changed,"document":document(&session)?}))
            }
            "project.refresh_tree"
            | "designer.refresh_project_tree"
            | "editor.save_active"
            | "editor.save_all"
            | "editor.reload_from_disk"
            | "designer.generate_code"
            | "material.save_export"
            | "run.preview_scene"
            | "run.stop"
            | "app.restart" => {
                if name == "designer.generate_code" || name == "material.save_export" {
                    let id = session
                        .workspace
                        .require_active()
                        .map_err(|e| e.to_string())?;
                    let d = session.workspace.document(id).map_err(|e| e.to_string())?;
                    let expected = if name == "designer.generate_code" {
                        "smdesign"
                    } else {
                        "smmat"
                    };
                    if d.path().extension().is_none_or(|e| e != expected) {
                        return Err(format!("Expected a .{expected} document"));
                    }
                }
                let command = match name {
                    "project.refresh_tree" | "designer.refresh_project_tree" => Command::Refresh,
                    "editor.save_all" => Command::SaveAll,
                    "editor.reload_from_disk" => {
                        let id = session
                            .workspace
                            .require_active()
                            .map_err(|e| e.to_string())?;
                        let doc = session.workspace.document(id).map_err(|e| e.to_string())?;
                        if doc.is_dirty() {
                            return Err("Unsaved edits: reload refused".into());
                        }
                        Command::ReloadPath(doc.path().to_owned())
                    }
                    "run.preview_scene" => Command::Run,
                    "run.stop" => Command::Stop,
                    "app.restart" => {
                        if session.workspace.has_dirty_documents() {
                            return Err("Save documents before restart".into());
                        }
                        Command::Restart
                    }
                    _ => Command::Save,
                };
                services.storage_outcome = None;
                super::commands::execute(
                    command,
                    &mut session,
                    &mut services,
                    &mut changes,
                    &mut exit,
                )
                .map_err(|e| format!("{e:#}"))?;
                if name == "app.restart" {
                    return Ok(Some(
                        json!({"accepted":true,"message":"IDE is restarting; reconnect to the same endpoint."}),
                    ));
                }
                pending.phase = Phase::Command;
                None
            }
            _ if readonly(name) => {
                let id = session
                    .workspace
                    .require_active()
                    .map_err(|e| e.to_string())?;
                Some(automation::inspect(
                    name,
                    session
                        .workspace
                        .document(id)
                        .map_err(|e| e.to_string())?
                        .text(),
                    args,
                )?)
            }
            _ => {
                let id = guard(&session, args)?;
                let source = session
                    .workspace
                    .document(id)
                    .map_err(|e| e.to_string())?
                    .text();
                let changed = automation::edit(name, source, args)?;
                session
                    .workspace
                    .document_mut(id)
                    .map_err(|e| e.to_string())?
                    .replace_text(changed);
                changes.write(ViewChange::BufferChanged(id));
                if args["save"].as_bool().unwrap_or(true) {
                    services.storage_outcome = None;
                    services
                        .save(&mut session, vec![id], super::jobs::SavePurpose::Save)
                        .map_err(|e| e.to_string())?;
                    pending.phase = Phase::Save(id);
                    None
                } else {
                    Some(document(&session)?)
                }
            }
        };
        Ok(result)
    })();
    match outcome {
        Ok(Some(v)) => finish(pending, Ok(v)),
        Err(e) => finish(pending, Err(e)),
        Ok(None) => state.pending = Some(pending),
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    use scenemax_ide_services::Filesystem;
    use std::{sync::mpsc, time::Duration};
    fn app(root: &std::path::Path) -> App {
        let mut app = App::new();
        app.insert_resource(Session::new(Filesystem::open_project(root).unwrap()))
            .insert_resource(EditorServices::new(root.join("missing-projector")).unwrap())
            .insert_resource(State {
                worker: Some(Worker::new().unwrap()),
                ..default()
            })
            .add_message::<ViewChange>()
            .add_message::<AppExit>()
            .add_systems(Update, (update, super::super::poll_jobs).chain());
        app
    }
    fn call(app: &mut App, name: &str, args: Value) -> Result<Value, String> {
        let (tx, rx) = mpsc::sync_channel(1);
        let root = app
            .world()
            .resource::<Session>()
            .workspace
            .project()
            .root()
            .to_owned();
        app.world_mut().resource_mut::<State>().pending = Some(Pending {
            call: Call {
                name: name.into(),
                arguments: args,
                deadline: Instant::now() + Duration::from_secs(5),
                reply: tx,
            },
            root,
            phase: Phase::Ready,
        });
        for _ in 0..1000 {
            app.update();
            if let Ok(result) = rx.try_recv() {
                return result;
            }
            std::thread::sleep(Duration::from_millis(2));
        }
        panic!("MCP request did not complete")
    }
    #[test]
    fn real_editor_dirty_revisions_save_conflicts_and_undo() {
        let dir = tempfile::tempdir().unwrap();
        std::fs::create_dir(dir.path().join("scripts")).unwrap();
        std::fs::write(dir.path().join("scripts/main"), "original").unwrap();
        let mut app = app(dir.path());
        let read = call(&mut app, "file.read", json!({"path":"scripts/main"})).unwrap();
        assert_eq!(read["content"], "original");
        let edited = call(
            &mut app,
            "file.modify",
            json!({"newContent":"changed","save":false}),
        )
        .unwrap();
        assert!(
            call(&mut app, "file.modify", json!({"newContent":"lost"}))
                .unwrap_err()
                .contains("Revision conflict")
        );
        assert!(
            call(
                &mut app,
                "file.modify",
                json!({"newContent":"lost","expected_revision":read["revision"]})
            )
            .is_err()
        );
        call(
            &mut app,
            "file.modify",
            json!({"newContent":"saved","expected_revision":edited["revision"]}),
        )
        .unwrap();
        assert_eq!(
            std::fs::read_to_string(dir.path().join("scripts/main")).unwrap(),
            "saved"
        );
        let undo = call(&mut app, "editor.undo", json!({})).unwrap();
        assert_eq!(undo["document"]["content"], "changed");
        std::fs::write(dir.path().join("scripts/main"), "external change").unwrap();
        assert!(call(&mut app, "editor.save_active", json!({})).is_err());
        assert_eq!(
            std::fs::read_to_string(dir.path().join("scripts/main")).unwrap(),
            "external change"
        );
    }
    #[test]
    fn creates_scene_edits_and_generates_companion_through_save_pipeline() {
        let dir = tempfile::tempdir().unwrap();
        std::fs::create_dir(dir.path().join("scripts")).unwrap();
        let mut app = app(dir.path());
        call(
            &mut app,
            "scene.create",
            json!({"parentPath":"scripts","sceneName":"automated_scene"}),
        )
        .unwrap();
        call(
            &mut app,
            "designer.add_primitive",
            json!({"primitive":"BOX","name":"example_cube"}),
        )
        .unwrap();
        let generated = std::fs::read_to_string(
            dir.path()
                .join("scripts/automated_scene/automated_scene.code"),
        )
        .unwrap();
        assert!(generated.contains("example_cube"));
        let data = call(&mut app, "designer.get_document_json", json!({})).unwrap();
        assert_eq!(data["document"]["entities"][0]["name"], "example_cube");
    }
    #[test]
    fn expired_and_switched_requests_do_not_mutate() {
        let dir = tempfile::tempdir().unwrap();
        std::fs::create_dir(dir.path().join("scripts")).unwrap();
        std::fs::write(dir.path().join("scripts/main"), "original").unwrap();
        let mut app = app(dir.path());
        call(&mut app, "file.read", json!({"path":"scripts/main"})).unwrap();
        let (tx, rx) = mpsc::sync_channel(1);
        let root = app
            .world()
            .resource::<Session>()
            .workspace
            .project()
            .root()
            .to_owned();
        app.world_mut().resource_mut::<State>().pending = Some(Pending {
            call: Call {
                name: "file.modify".into(),
                arguments: json!({"newContent":"bad"}),
                deadline: Instant::now() - Duration::from_secs(1),
                reply: tx,
            },
            root,
            phase: Phase::Ready,
        });
        app.update();
        assert!(rx.try_recv().unwrap().is_err());
        assert_eq!(
            document(app.world().resource::<Session>()).unwrap()["content"],
            "original"
        );
    }
    #[test]
    fn restart_refuses_dirty_work_then_completes_normal_workspace_handoff() {
        let dir = tempfile::tempdir().unwrap();
        std::fs::create_dir(dir.path().join("scripts")).unwrap();
        std::fs::write(dir.path().join("scripts/main"), "// original").unwrap();
        let mut app = app(dir.path());
        call(&mut app, "file.read", json!({"path":"scripts/main"})).unwrap();
        call(
            &mut app,
            "file.modify",
            json!({"newContent":"// unsaved","save":false}),
        )
        .unwrap();
        assert!(
            call(&mut app, "app.restart", json!({}))
                .unwrap_err()
                .contains("Save documents")
        );
        call(&mut app, "editor.save_active", json!({})).unwrap();
        let response = call(&mut app, "app.restart", json!({})).unwrap();
        assert_eq!(response["accepted"], true);
        for _ in 0..500 {
            app.update();
            let session = app.world().resource::<Session>();
            if let Some(root) = session.restart_project.lock().unwrap().as_ref() {
                assert_eq!(*root, dir.path().canonicalize().unwrap());
                return;
            }
            std::thread::sleep(Duration::from_millis(2));
        }
        panic!("Restart workspace handoff was not completed");
    }
    #[test]
    fn completion_is_not_delayed_or_replaced_by_a_new_user_operation() {
        let dir = tempfile::tempdir().unwrap();
        let mut app = app(dir.path());
        let (tx, rx) = mpsc::sync_channel(1);
        let root = app
            .world()
            .resource::<Session>()
            .workspace
            .project()
            .root()
            .to_owned();
        app.world_mut().resource_mut::<State>().pending = Some(Pending {
            call: Call {
                name: "editor.save_all".into(),
                arguments: json!({}),
                deadline: Instant::now() + Duration::from_secs(5),
                reply: tx,
            },
            root: root.clone(),
            phase: Phase::Command,
        });
        {
            let mut services = app.world_mut().resource_mut::<EditorServices>();
            services.storage_outcome = Some(Ok(()));
            services
                .storage
                .request(scenemax_ide_services::StorageRequest::Refresh(
                    root.join("missing"),
                ))
                .unwrap();
        }
        app.update();
        assert!(rx.try_recv().unwrap().is_ok());
        app.world_mut()
            .resource_mut::<Session>()
            .asset_operation_pending = true;
        assert!(call(&mut app, "ide.get_status", json!({})).is_ok());
    }
}
