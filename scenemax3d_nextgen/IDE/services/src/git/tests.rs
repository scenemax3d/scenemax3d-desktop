use super::*;
use scenemax_ide_core::git::Operation as Op;
use std::{path::Path, process::Command};
fn context() -> Context {
    Context {
        global: None,
        cancel: Arc::new(AtomicBool::new(false)),
        output: Arc::new(Mutex::new(String::new())),
        progress: false,
    }
}
fn git(root: &Path, args: &[&str]) -> String {
    let output = Command::new("git")
        .current_dir(root)
        .args(args)
        .output()
        .unwrap();
    assert!(
        output.status.success(),
        "{}",
        String::from_utf8_lossy(&output.stderr)
    );
    String::from_utf8_lossy(&output.stdout).into_owned()
}
fn repo() -> tempfile::TempDir {
    let d = tempfile::tempdir().unwrap();
    git(d.path(), &["init"]);
    git(d.path(), &["symbolic-ref", "HEAD", "refs/heads/main"]);
    git(d.path(), &["config", "core.autocrlf", "false"]);
    git(d.path(), &["config", "user.name", "Fixture"]);
    git(
        d.path(),
        &["config", "user.email", "fixture@example.invalid"],
    );
    d
}
fn run(root: &Path, op: Op) -> String {
    repository::execute(root, &op, &context()).unwrap()
}
fn inspect(root: &Path) -> Snapshot {
    repository::inspect(root, 100, &context()).unwrap()
}
#[test]
fn git_staging_unborn_rename_unicode_commit_diff_stash_and_discard() {
    let d = repo();
    let r = d.path();
    let name = "space שלום.txt";
    std::fs::write(r.join(name), "first\n").unwrap();
    run(r, Op::Stage(vec![name.into()]));
    assert!(inspect(r).files[0].staged());
    run(r, Op::Unstage(vec![name.into()]));
    assert!(!inspect(r).files[0].staged());
    run(r, Op::Stage(vec![]));
    run(
        r,
        Op::Commit("initial\n\nBody with `literal` $text".into(), false),
    );
    std::fs::write(r.join(name), "second\n").unwrap();
    run(r, Op::Stage(vec![name.into()]));
    std::fs::write(r.join(name), "third\n").unwrap();
    let s = inspect(r);
    assert_eq!((s.files[0].index, s.files[0].worktree), ('M', 'M'));
    assert!(run(r, Op::Diff(name.into(), true)).contains("+second"));
    run(r, Op::Discard(vec![name.into()]));
    assert_eq!(std::fs::read_to_string(r.join(name)).unwrap(), "second\n");
    run(r, Op::Unstage(vec![]));
    run(r, Op::Stash);
    assert!(inspect(r).files.is_empty());
    run(r, Op::Pop);
    run(r, Op::Stage(vec![]));
    run(r, Op::Commit("second".into(), false));
    std::fs::rename(r.join(name), r.join("renamed.txt")).unwrap();
    run(r, Op::Stage(vec![]));
    assert_eq!(inspect(r).files[0].original.as_deref(), Some(name));
    std::fs::write(r.join("renamed.txt"), "second\nx\n").unwrap();
    run(r, Op::Stage(vec!["renamed.txt".into()]));
    run(r, Op::Discard(vec!["renamed.txt".into()]));
    run(r, Op::Unstage(vec!["renamed.txt".into()]));
    assert!(inspect(r).files.iter().all(|f| !f.staged()));
    let s = inspect(r);
    assert_eq!(s.commits.len(), 2);
    assert!(run(r, Op::Show(s.commits[0].hash.clone())).contains("second"));
}
#[test]
fn git_branch_merge_conflict_abort_and_rename_delete() {
    let d = repo();
    let r = d.path();
    std::fs::write(r.join("file"), "base\n").unwrap();
    run(r, Op::Stage(vec![]));
    run(r, Op::Commit("base".into(), false));
    run(r, Op::CreateBranch("feature".into(), true));
    std::fs::write(r.join("file"), "feature\n").unwrap();
    run(r, Op::Stage(vec![]));
    run(r, Op::Commit("feature".into(), false));
    run(r, Op::Switch("main".into(), false));
    std::fs::write(r.join("file"), "main\n").unwrap();
    run(r, Op::Stage(vec![]));
    run(r, Op::Commit("main".into(), false));
    assert!(repository::execute(r, &Op::Merge("feature".into()), &context()).is_err());
    assert!(inspect(r).files[0].conflict());
    run(r, Op::Abort(false));
    assert!(inspect(r).files.is_empty());
    run(r, Op::Rename("feature".into(), "renamed".into()));
    assert!(repository::execute(r, &Op::Delete("renamed".into(), false), &context()).is_err());
    run(r, Op::Delete("renamed".into(), true));
    assert!(repository::execute(r, &Op::CreateBranch("--bad".into(), false), &context()).is_err());
}
#[test]
fn git_local_remote_push_clone_fetch_pull_rebase_and_ignore_preservation() {
    let d = repo();
    let r = d.path();
    std::fs::write(r.join("file"), "base").unwrap();
    run(r, Op::Stage(vec![]));
    run(r, Op::Commit("base".into(), false));
    let remote = tempfile::tempdir().unwrap();
    git(remote.path(), &["init", "--bare"]);
    git(remote.path(), &["symbolic-ref", "HEAD", "refs/heads/main"]);
    git(
        r,
        &["remote", "add", "origin", remote.path().to_str().unwrap()],
    );
    run(r, Op::Push);
    let parent = tempfile::tempdir().unwrap();
    let clone = parent.path().join("clone with spaces");
    run(
        parent.path(),
        Op::Clone(remote.path().to_string_lossy().into_owned(), clone.clone()),
    );
    std::fs::write(r.join("new"), "new").unwrap();
    run(r, Op::Stage(vec![]));
    run(r, Op::Commit("next".into(), true));
    run(&clone, Op::Fetch);
    run(&clone, Op::Pull(false));
    assert!(clone.join("new").is_file());
    run(&clone, Op::Pull(true));
    assert!(
        repository::execute(
            parent.path(),
            &Op::Clone("unused".into(), clone.clone()),
            &context()
        )
        .is_err()
    );
    run(&clone, Op::Ignore);
    let before = std::fs::read(clone.join(".gitignore")).unwrap();
    assert!(repository::execute(&clone, &Op::Ignore, &context()).is_err());
    assert_eq!(std::fs::read(clone.join(".gitignore")).unwrap(), before);
}
#[test]
fn git_cancelled_operation_does_not_start_a_process() {
    let d = repo();
    let ctx = context();
    ctx.cancel.store(true, Ordering::Relaxed);
    assert!(
        repository::execute(d.path(), &Op::Fetch, &ctx)
            .unwrap_err()
            .contains("cancelled")
    );
}

#[test]
fn git_configuration_uses_isolated_global_file_and_supports_remote_testing() {
    let dir = repo();
    let global = tempfile::tempdir().unwrap();
    let mut ctx = context();
    ctx.global = Some(global.path().join("gitconfig"));
    let config = scenemax_ide_core::git::Config {
        name: "Test identity".into(),
        email: "test@example.invalid".into(),
        branch: "main".into(),
        helper: "".into(),
        origin: dir.path().to_string_lossy().into_owned(),
    };
    repository::execute(dir.path(), &Op::Configure(config), &ctx).unwrap();
    let s = repository::inspect(dir.path(), 100, &ctx).unwrap();
    assert_eq!(s.config.name, "Test identity");
    assert_eq!(s.config.branch, "main");
    repository::execute(
        dir.path(),
        &Op::TestRemote(dir.path().to_string_lossy().into_owned()),
        &ctx,
    )
    .unwrap();
}
#[test]
fn git_cancellation_stops_owned_command_and_untracked_discard_is_recoverable() {
    let dir = repo();
    let ctx = context();
    let cancel = ctx.cancel.clone();
    let trigger = std::thread::spawn(move || {
        std::thread::sleep(std::time::Duration::from_millis(350));
        cancel.store(true, Ordering::Relaxed);
    });
    let start = std::time::Instant::now();
    assert!(
        process::run(dir.path(), &["-c", "alias.wait=!sleep 20", "wait"], &ctx)
            .unwrap_err()
            .contains("cancelled")
    );
    trigger.join().unwrap();
    assert!(start.elapsed().as_secs() < 10);
    std::fs::write(dir.path().join("keep.txt"), "recover me").unwrap();
    let output = run(dir.path(), Op::Discard(vec!["keep.txt".into()]));
    assert!(output.contains("scenemax-discard-"));
    assert!(!dir.path().join("keep.txt").exists());
    let saved = std::fs::read_dir(dir.path().join(".git"))
        .unwrap()
        .filter_map(Result::ok)
        .find(|e| {
            e.file_name()
                .to_string_lossy()
                .starts_with("scenemax-discard-")
        })
        .unwrap();
    assert_eq!(
        std::fs::read_to_string(saved.path().join("keep.txt")).unwrap(),
        "recover me"
    );
}
