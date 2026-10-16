use super::{Context, process};
use scenemax_ide_core::git::{Branch, Commit, Config, Operation, Snapshot, parse_status};
use std::{
    io::{Read, Write},
    path::{Component, Path},
};

fn call(root: &Path, args: &[&str], ctx: &Context) -> Result<String, String> {
    let (ok, bytes, error) = process::run(root, args, ctx)?;
    let out = String::from_utf8_lossy(&bytes).into_owned();
    if ok {
        Ok(out)
    } else {
        Err(format!("{}\n{}", out.trim(), error.trim()).trim().into())
    }
}
fn optional(root: &Path, args: &[&str], ctx: &Context) -> Result<String, String> {
    let (ok, bytes, _) = process::run(root, args, ctx)?;
    Ok(if ok {
        String::from_utf8_lossy(&bytes).trim().into()
    } else {
        String::new()
    })
}
pub(super) fn inspect(project: &Path, limit: usize, ctx: &Context) -> Result<Snapshot, String> {
    let version = call(project, &["--version"], ctx)?.trim().into();
    let root = optional(project, &["rev-parse", "--show-toplevel"], ctx)?;
    let config = Config {
        name: optional(project, &["config", "--global", "--get", "user.name"], ctx)?,
        email: optional(project, &["config", "--global", "--get", "user.email"], ctx)?,
        branch: optional(
            project,
            &["config", "--global", "--get", "init.defaultBranch"],
            ctx,
        )?,
        helper: optional(
            project,
            &["config", "--global", "--get", "credential.helper"],
            ctx,
        )?,
        origin: if root.is_empty() {
            String::new()
        } else {
            optional(project, &["remote", "get-url", "origin"], ctx)?
        },
    };
    let mut s = Snapshot {
        version,
        config,
        ..Default::default()
    };
    if root.is_empty() {
        return Ok(s);
    }
    let root = Path::new(&root);
    s.root = Some(root.into());
    s.branch = optional(root, &["symbolic-ref", "--short", "-q", "HEAD"], ctx)?;
    if s.branch.is_empty() {
        s.branch = format!(
            "detached {}",
            optional(root, &["rev-parse", "--short", "HEAD"], ctx)?
        );
    }
    s.upstream = optional(root, &["rev-parse", "--abbrev-ref", "@{upstream}"], ctx)?;
    let (ok, bytes, err) = process::run(
        root,
        &["status", "--porcelain=v1", "-z", "--untracked-files=all"],
        ctx,
    )?;
    if !ok {
        return Err(err);
    }
    s.files = parse_status(&bytes)?;
    let refs = call(
        root,
        &[
            "for-each-ref",
            "--format=%(HEAD)%09%(refname)%09%(upstream:short)",
            "refs/heads",
            "refs/remotes",
        ],
        ctx,
    )?;
    for row in refs.lines().take(5000) {
        let fields: Vec<_> = row.split('\t').collect();
        if fields.len() != 3 || fields[1].ends_with("/HEAD") {
            continue;
        }
        let remote = fields[1].starts_with("refs/remotes/");
        s.branches.push(Branch {
            current: fields[0] == "*",
            remote,
            name: fields[1]
                .trim_start_matches(if remote {
                    "refs/remotes/"
                } else {
                    "refs/heads/"
                })
                .into(),
            upstream: fields[2].into(),
        });
    }
    if !optional(root, &["rev-parse", "--verify", "HEAD"], ctx)?.is_empty() {
        let log = call(
            root,
            &[
                "log",
                &format!("-{}", limit.clamp(1, 500)),
                "--format=%H%x00%an%x00%ai%x00%s%x00",
            ],
            ctx,
        )?;
        let parts: Vec<_> = log.split('\0').collect();
        for row in parts.chunks(4).filter(|r| r.len() == 4) {
            s.commits.push(Commit {
                hash: row[0].trim().into(),
                author: row[1].into(),
                date: row[2].into(),
                subject: row[3].into(),
            });
        }
    }
    s.stashes = call(root, &["stash", "list", "-10"], ctx)?;
    Ok(s)
}
fn reference(root: &Path, name: &str, ctx: &Context) -> Result<(), String> {
    if name.starts_with('-') || name.contains(['\n', '\r', '\0']) || name.is_empty() {
        return Err("Enter a valid branch name".into());
    }
    call(root, &["check-ref-format", "--branch", name], ctx).map(|_| ())
}
fn url(value: &str) -> Result<(), String> {
    if value.is_empty()
        || value.starts_with('-')
        || value.contains(['\n', '\r', '\0'])
        || value.starts_with("ext::")
    {
        return Err("Enter a repository URL or local repository path".into());
    }
    if let Some((scheme, rest)) = value.split_once("://")
        && rest
            .split('/')
            .next()
            .and_then(|h| h.split_once('@'))
            .is_some_and(|(user, _)| scheme != "ssh" || user.contains(':'))
    {
        return Err("Use Git's credential helper instead of credentials embedded in a URL".into());
    }
    Ok(())
}
fn paths(root: &Path, requested: &[String], ctx: &Context) -> Result<Vec<String>, String> {
    let (_, bytes, _) = process::run(
        root,
        &["status", "--porcelain=v1", "-z", "--untracked-files=all"],
        ctx,
    )?;
    let rows = parse_status(&bytes)?;
    let mut paths = Vec::new();
    for p in requested {
        if Path::new(p)
            .components()
            .any(|c| !matches!(c, Component::Normal(_)))
        {
            return Err("Invalid repository-relative path".into());
        }
        let row = rows
            .iter()
            .find(|r| &r.path == p)
            .ok_or("File status changed; refresh and select the file again")?;
        paths.push(p.clone());
        if let Some(old) = &row.original {
            paths.push(old.clone());
        }
    }
    Ok(paths)
}
fn push(root: &Path, ctx: &Context) -> Result<String, String> {
    if optional(root, &["rev-parse", "--abbrev-ref", "@{upstream}"], ctx)?.is_empty() {
        let branch = call(root, &["symbolic-ref", "--short", "HEAD"], ctx)?;
        call(
            root,
            &["push", "--progress", "-u", "origin", branch.trim()],
            ctx,
        )
    } else {
        call(root, &["push", "--progress"], ctx)
    }
}
pub(super) fn execute(project: &Path, op: &Operation, ctx: &Context) -> Result<String, String> {
    match op {
        Operation::Inspect(_) => return Ok("Repository refreshed".into()),
        Operation::Browse => return process::browse(ctx),
        Operation::Init => {
            let existing = optional(project, &["rev-parse", "--show-toplevel"], ctx)?;
            let result = call(project, &["init"], ctx)?;
            if existing.is_empty() {
                let branch = optional(
                    project,
                    &["config", "--global", "--get", "init.defaultBranch"],
                    ctx,
                )?;
                let branch = if branch.is_empty() {
                    "main"
                } else {
                    branch.as_str()
                };
                reference(project, branch, ctx)?;
                call(
                    project,
                    &["symbolic-ref", "HEAD", &format!("refs/heads/{branch}")],
                    ctx,
                )?;
            }
            return Ok(result);
        }
        Operation::Clone(remote, target) => {
            url(remote)?;
            if target.exists() {
                return Err(
                    "Choose a new clone directory; existing folders are never overwritten".into(),
                );
            }
            return call(
                project,
                &[
                    "clone",
                    "--progress",
                    "--",
                    remote,
                    &target.to_string_lossy(),
                ],
                ctx,
            );
        }
        Operation::Configure(config) => {
            if config.name.trim().is_empty() || config.email.trim().is_empty() {
                return Err("User name and email are required".into());
            }
            reference(project, &config.branch, ctx)?;
            if ![
                "",
                "manager",
                "manager-core",
                "wincred",
                "osxkeychain",
                "cache",
                "store",
                "libsecret",
            ]
            .contains(&config.helper.as_str())
            {
                return Err("Select a named credential helper (manager, wincred, osxkeychain, cache, store or libsecret)".into());
            }
            if !config.origin.is_empty() {
                url(&config.origin)?;
            }
            for (key, val) in [
                ("user.name", &config.name),
                ("user.email", &config.email),
                ("init.defaultBranch", &config.branch),
                ("credential.helper", &config.helper),
            ] {
                call(
                    project,
                    &["config", "--global", "--replace-all", key, val],
                    ctx,
                )?;
            }
            if !optional(project, &["rev-parse", "--show-toplevel"], ctx)?.is_empty() {
                let current = optional(project, &["remote", "get-url", "origin"], ctx)?;
                if config.origin.is_empty() {
                    if !current.is_empty() {
                        call(project, &["remote", "remove", "origin"], ctx)?;
                    }
                } else if current.is_empty() {
                    call(project, &["remote", "add", "origin", &config.origin], ctx)?;
                } else {
                    call(
                        project,
                        &["remote", "set-url", "origin", &config.origin],
                        ctx,
                    )?;
                }
            }
            return Ok("Git configuration applied".into());
        }
        Operation::TestRemote(remote) => {
            url(remote)?;
            return call(project, &["ls-remote", "--heads", "--", remote], ctx);
        }
        Operation::Ignore => {
            let path = project.join(".gitignore");
            let mut file = std::fs::OpenOptions::new()
                .write(true)
                .create_new(true)
                .open(path)
                .map_err(|e| format!("Create .gitignore: {e}. Existing files are preserved."))?;
            file.write_all(b"# SceneMax project ignores\n.scenemax-studio/\nbuild/\nbuild_games/\ntarget/\nout/\ndata/\nconfig.properties\n*.log\n*.bak\n*.tmp\nThumbs.db\n.DS_Store\n").map_err(|e| e.to_string())?;
            return Ok("Created .gitignore".into());
        }
        _ => {}
    }
    let root_text = call(project, &["rev-parse", "--show-toplevel"], ctx)?;
    let root = Path::new(root_text.trim());
    match op {
        Operation::Stage(selected) | Operation::Unstage(selected) => {
            let expanded = paths(root, selected, ctx)?;
            let selected = if matches!(op, Operation::Unstage(_)) {
                expanded
            } else {
                selected.clone()
            };
            if matches!(op, Operation::Stage(_)) {
                let mut args = vec!["add", "-A", "--"];
                if selected.is_empty() {
                    args.push(".");
                } else {
                    args.extend(selected.iter().map(String::as_str));
                }
                call(root, &args, ctx)
            } else {
                let unborn = optional(root, &["rev-parse", "--verify", "HEAD"], ctx)?.is_empty();
                let mut args = if unborn {
                    vec!["rm", "--cached", "-f", "-r", "--ignore-unmatch", "--"]
                } else {
                    vec!["reset", "-q", "HEAD", "--"]
                };
                if selected.is_empty() {
                    args.push(".");
                } else {
                    args.extend(selected.iter().map(String::as_str));
                }
                call(root, &args, ctx)
            }
        }
        Operation::Discard(selected) => {
            if selected.is_empty() {
                return Err("Select files to discard".into());
            }
            paths(root, selected, ctx)?;
            let validated = selected.clone();
            let (_, bytes, _) = process::run(
                root,
                &["status", "--porcelain=v1", "-z", "--untracked-files=all"],
                ctx,
            )?;
            let rows = parse_status(&bytes)?;
            let untracked: Vec<_> = rows
                .iter()
                .filter(|r| r.index == '?' && selected.contains(&r.path))
                .collect();
            let tracked: Vec<_> = validated
                .iter()
                .filter(|p| !untracked.iter().any(|r| &r.path == *p))
                .map(String::as_str)
                .collect();
            if !tracked.is_empty() {
                let mut args = vec!["restore", "--worktree", "--"];
                args.extend(tracked);
                call(root, &args, ctx)?;
            }
            if !untracked.is_empty() {
                let git_dir = call(root, &["rev-parse", "--absolute-git-dir"], ctx)?;
                let trash = tempfile::Builder::new()
                    .prefix("scenemax-discard-")
                    .tempdir_in(git_dir.trim())
                    .map_err(|e| e.to_string())?
                    .keep();
                for row in untracked {
                    let source = root.join(&row.path);
                    let canonical = source.canonicalize().map_err(|e| e.to_string())?;
                    let canonical_root = root.canonicalize().map_err(|e| e.to_string())?;
                    if !canonical.starts_with(canonical_root) || !source.is_file() {
                        return Err("Discard supports only regular repository files".into());
                    }
                    let target = trash.join(&row.path);
                    if let Some(parent) = target.parent() {
                        std::fs::create_dir_all(parent).map_err(|e| e.to_string())?;
                    }
                    std::fs::rename(source, target).map_err(|e| e.to_string())?;
                }
                Ok(format!(
                    "Tracked edits discarded. Untracked files moved to {}",
                    trash.display()
                ))
            } else {
                Ok("Working-tree changes discarded; staged content preserved".into())
            }
        }
        Operation::Commit(message, also_push) => {
            if message.trim().is_empty() {
                return Err("Enter a commit message".into());
            }
            let mut file = tempfile::NamedTempFile::new().map_err(|e| e.to_string())?;
            file.write_all(message.as_bytes())
                .map_err(|e| e.to_string())?;
            let output = call(
                root,
                &["commit", "--file", &file.path().to_string_lossy()],
                ctx,
            )?;
            if *also_push {
                match push(root, ctx) {
                    Ok(p) => Ok(format!("{output}\n{p}")),
                    Err(e) => Err(format!(
                        "Commit succeeded; push failed. Do not repeat the commit.\n{e}"
                    )),
                }
            } else {
                Ok(output)
            }
        }
        Operation::Push => push(root, ctx),
        Operation::Pull(rebase) => call(
            root,
            &[
                "pull",
                "--progress",
                if *rebase { "--rebase" } else { "--no-rebase" },
                "--no-edit",
            ],
            ctx,
        ),
        Operation::Fetch => call(root, &["fetch", "--all", "--progress"], ctx),
        Operation::CreateBranch(name, switch) => {
            reference(root, name, ctx)?;
            if *switch {
                call(root, &["switch", "-c", name], ctx)
            } else {
                call(root, &["branch", name], ctx)
            }
        }
        Operation::Switch(name, remote) => {
            reference(root, name, ctx)?;
            if *remote {
                call(root, &["switch", "--track", name], ctx)
            } else {
                call(root, &["switch", name], ctx)
            }
        }
        Operation::Merge(name) => {
            reference(root, name, ctx)?;
            call(root, &["merge", "--no-edit", name], ctx)
        }
        Operation::Rename(old, new) => {
            reference(root, old, ctx)?;
            reference(root, new, ctx)?;
            call(root, &["branch", "-m", old, new], ctx)
        }
        Operation::Delete(name, force) => {
            reference(root, name, ctx)?;
            call(
                root,
                &["branch", if *force { "-D" } else { "-d" }, name],
                ctx,
            )
        }
        Operation::Stash => call(root, &["stash", "push"], ctx),
        Operation::Pop => call(root, &["stash", "pop"], ctx),
        Operation::Show(hash) => {
            if hash.len() < 7 || !hash.bytes().all(|b| b.is_ascii_hexdigit()) {
                return Err("Invalid commit ID".into());
            }
            call(
                root,
                &[
                    "show",
                    "--no-ext-diff",
                    "--no-textconv",
                    "--stat",
                    "--patch",
                    hash,
                    "--",
                ],
                ctx,
            )
        }
        Operation::Diff(path, staged) => {
            let selected = paths(root, std::slice::from_ref(path), ctx)?;
            if !*staged {
                let (_, bytes, _) = process::run(
                    root,
                    &["status", "--porcelain=v1", "-z", "--untracked-files=all"],
                    ctx,
                )?;
                if parse_status(&bytes)?
                    .iter()
                    .any(|r| r.path == *path && r.index == '?')
                {
                    let file = root.join(path).canonicalize().map_err(|e| e.to_string())?;
                    if !file.starts_with(root.canonicalize().map_err(|e| e.to_string())?) {
                        return Err("File resolves outside repository".into());
                    }
                    let mut bytes = Vec::new();
                    std::fs::File::open(file)
                        .map_err(|e| e.to_string())?
                        .take(256 * 1024)
                        .read_to_end(&mut bytes)
                        .map_err(|e| e.to_string())?;
                    return if bytes.contains(&0) {
                        Ok("Untracked binary file".into())
                    } else {
                        Ok(format!(
                            "Untracked: {path} (preview up to 256 KiB)\n{}",
                            String::from_utf8_lossy(&bytes)
                        ))
                    };
                }
            }
            let mut args = vec!["diff", "--no-ext-diff", "--no-textconv"];
            if *staged {
                args.push("--cached");
            }
            args.push("--");
            args.extend(selected.iter().map(String::as_str));
            call(root, &args, ctx)
        }
        Operation::Continue(rebase) => call(
            root,
            &[if *rebase { "rebase" } else { "merge" }, "--continue"],
            ctx,
        ),
        Operation::Abort(rebase) => call(
            root,
            &[if *rebase { "rebase" } else { "merge" }, "--abort"],
            ctx,
        ),
        _ => Err("Unsupported Git operation".into()),
    }
}
