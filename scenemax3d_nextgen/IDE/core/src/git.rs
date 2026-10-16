//! Git workflow data, independent of processes and widgets.
use std::path::PathBuf;

/// Changed repository path; the two columns retain index/worktree distinctions.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct FileStatus {
    /// Index status.
    pub index: char,
    /// Working tree status.
    pub worktree: char,
    /// Current repository-relative filename.
    pub path: String,
    /// Original filename for a rename/copy.
    pub original: Option<String>,
}
impl FileStatus {
    /// Whether the index contains a change.
    pub fn staged(&self) -> bool {
        self.index != ' ' && self.index != '?'
    }
    /// Whether this is an unresolved conflict.
    pub fn conflict(&self) -> bool {
        self.index == 'U'
            || self.worktree == 'U'
            || matches!((self.index, self.worktree), ('A', 'A') | ('D', 'D'))
    }
}
/// Local or remote branch row.
#[derive(Clone, Debug, Default)]
pub struct Branch {
    /// Short ref name, including remote for remote refs.
    pub name: String,
    /// Currently checked out.
    pub current: bool,
    /// Remote tracking ref.
    pub remote: bool,
    /// Upstream name.
    pub upstream: String,
}
/// Commit history row.
#[derive(Clone, Debug, Default)]
pub struct Commit {
    /// Full object ID.
    pub hash: String,
    /// Author display name.
    pub author: String,
    /// Author date.
    pub date: String,
    /// Subject line.
    pub subject: String,
}
/// Java-compatible Git configuration form.
#[derive(Clone, Debug, Default)]
pub struct Config {
    /// Global author name.
    pub name: String,
    /// Global author email.
    pub email: String,
    /// Default initial branch.
    pub branch: String,
    /// Named credential helper; executable shell helpers are not authored here.
    pub helper: String,
    /// Project origin URL.
    pub origin: String,
}
/// Bounded repository summary produced by the worker.
#[derive(Clone, Debug, Default)]
pub struct Snapshot {
    /// Installed Git version.
    pub version: String,
    /// Actual repository root, which may be above the project.
    pub root: Option<PathBuf>,
    /// Current branch or detached HEAD description.
    pub branch: String,
    /// Configured upstream.
    pub upstream: String,
    /// Status rows.
    pub files: Vec<FileStatus>,
    /// Branch rows.
    pub branches: Vec<Branch>,
    /// Recent commits.
    pub commits: Vec<Commit>,
    /// Stash summary.
    pub stashes: String,
    /// Global identity and repository remote.
    pub config: Config,
}
/// A typed Git operation; no shell command text crosses the UI boundary.
#[derive(Clone, Debug)]
pub enum Operation {
    /// Refresh repository data with a history limit.
    Inspect(usize),
    /// Initialize this project.
    Init,
    /// Clone URL to a new directory.
    Clone(String, PathBuf),
    /// Stage listed paths, or all paths when empty.
    Stage(Vec<String>),
    /// Unstage listed paths, or all paths when empty.
    Unstage(Vec<String>),
    /// Discard tracked worktree changes in selected paths.
    Discard(Vec<String>),
    /// Commit staged content; optionally push afterward.
    Commit(String, bool),
    /// Push, configuring origin upstream when necessary.
    Push,
    /// Pull with merge or rebase.
    Pull(bool),
    /// Fetch all remotes.
    Fetch,
    /// Create a local branch and optionally switch.
    CreateBranch(String, bool),
    /// Switch to a local or remote tracking branch.
    Switch(String, bool),
    /// Merge selected branch into current.
    Merge(String),
    /// Rename a local branch.
    Rename(String, String),
    /// Delete local branch; force requires separate confirmation.
    Delete(String, bool),
    /// Save tracked changes to stash.
    Stash,
    /// Apply and remove the latest stash.
    Pop,
    /// Create default ignores without replacing existing content.
    Ignore,
    /// Apply global identity and project origin.
    Configure(Config),
    /// Test a remote URL.
    TestRemote(String),
    /// Show selected commit patch.
    Show(String),
    /// Show selected staged or working-tree diff.
    Diff(String, bool),
    /// Continue merge/rebase after resolving conflicts.
    Continue(bool),
    /// Abort merge/rebase.
    Abort(bool),
    /// Select an existing clone parent folder.
    Browse,
}
impl Operation {
    /// Operations that require saved editor documents.
    pub fn mutates(&self) -> bool {
        !matches!(
            self,
            Self::Inspect(_) | Self::Show(_) | Self::Diff(..) | Self::TestRemote(_) | Self::Browse
        )
    }
    /// Operations that can replace working-tree files.
    pub fn changes_files(&self) -> bool {
        matches!(
            self,
            Self::Discard(_)
                | Self::Pull(_)
                | Self::Switch(..)
                | Self::Merge(_)
                | Self::Stash
                | Self::Pop
                | Self::Abort(_)
                | Self::Continue(_)
                | Self::Ignore
        )
    }
}
/// Parse NUL-delimited porcelain status without splitting filenames on whitespace.
pub fn parse_status(bytes: &[u8]) -> Result<Vec<FileStatus>, String> {
    let text = std::str::from_utf8(bytes).map_err(|_| "Git filenames must be UTF-8")?;
    let mut parts = text.split('\0').filter(|s| !s.is_empty());
    let mut rows = Vec::new();
    while let Some(row) = parts.next() {
        if row.len() < 4 || row.as_bytes()[2] != b' ' {
            return Err("Invalid Git status record".into());
        }
        let index = row.as_bytes()[0] as char;
        let worktree = row.as_bytes()[1] as char;
        let original = if matches!(index, 'R' | 'C') || matches!(worktree, 'R' | 'C') {
            Some(parts.next().ok_or("Incomplete rename record")?.to_owned())
        } else {
            None
        };
        rows.push(FileStatus {
            index,
            worktree,
            path: row[3..].into(),
            original,
        });
        if rows.len() > 5000 {
            return Err(
                "More than 5,000 changed files; narrow the repository before using the Git dialog"
                    .into(),
            );
        }
    }
    Ok(rows)
}
