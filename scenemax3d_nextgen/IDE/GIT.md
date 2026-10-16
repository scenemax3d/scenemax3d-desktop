# Git tools

The Git menu implements the desktop IDE workflows with retained Bevy dialogs and a background Git CLI worker. Git must be installed and available on PATH. Existing system credential helpers and SSH agents provide authentication; the IDE does not store passwords or access tokens. If a remote needs a new login, authenticate using your Git credential manager/terminal, then retry.

## Workflows

- **Initialize Repository** creates Git metadata in the current project. **Clone Repository** accepts a URL and new destination, offers a folder picker, progress/cancellation, and **Open Cloned Project** on success.
- **Stage & Commit** lists index/worktree statuses separately, including staged-plus-unstaged edits, conflicts, renames and untracked files. Select multiple rows, Stage/Unstage Selected or All, inspect working/staged diffs, enter a multiline message, then Commit or Commit & Push. Only staged content is committed.
- **Discard Changes** restores tracked working files from the index, preserving staged changes. Selected untracked files are moved into a recoverable `scenemax-discard-*` directory inside the actual Git metadata directory. The result shows its location. Confirmation lists selected files.
- **Push** uses the current upstream or sets origin for a new branch. **Pull**, **Pull (Rebase)** and **Fetch** show results and bounded network progress. Failed Commit & Push explicitly distinguishes a successful commit from a failed push.
- **Branches** lists the current/local branches and optionally remote branches. Create, create-and-switch, switch (including tracking remote branches), merge into current, rename, delete or explicitly force-delete. Destructive actions have an explicit confirmation.
- **Commit History** supports 1–500 recent commits, with author/date/subject and selected commit statistics/patches.
- **Stash Changes** saves tracked edits; **Pop Stash** applies the latest stash. Untracked files are left alone. Conflicts remain visible in Stage & Commit. Resolve files in the editor, save and stage them, then use Continue Merge/Rebase or Abort as appropriate. Abort requires confirmation.
- **Create .gitignore** creates a SceneMax-oriented ignore file without replacing an existing one.
- **Configuration** displays Git availability/version, global identity/default branch/credential helper, and this project's origin. Apply confirms the global/project scope. Test Connection checks the entered remote.

## Ownership and safeguards

The dialog shows the actual repository root, which may be a parent of the selected project. Stage All and commits operate on that repository. Ordinary Git rejection rules apply to checkout, merges, non-fast-forward pushes and deleting unmerged branches. There is no implicit force push or hard reset.

Save editor documents and stop the game before mutations. The application locks conflicting file operations while Git runs. Worktree-changing results reload only buffers whose identity and revision still match the submitted snapshot. Newer edits and missing documents are retained with diagnostics. Refresh rebuilds the project inventory after repository mutations. A project switch cannot apply an old repository result to the new project.

Jobs use argument arrays, literal pathspecs and NUL-delimited status parsing. One job can run at a time. The worker owns its subprocess, supports cancellation and a five-minute timeout, and limits command output to 4 MiB. Dialog output is limited to 24,000 displayed characters. Status is limited to 5,000 paths, history to 500 commits. Large diff output reports its limit instead of freezing layout. Controls and commit-message fields remain retained during row/status refreshes.

## Implementation and verification

- `core/src/git.rs`: workflow types and porcelain parser.
- `services/src/git/`: process ownership, repository operations and isolated Git integration tests.
- `app/src/application/git.rs`: captured project, dirty-buffer guards, job completion and document refresh.
- `app/src/presentation/git.rs`: dialogs, fields, selection, confirmation and bounded output.

Tests use temporary repositories and local bare remotes. Configuration tests redirect global config operations to an isolated file (including older Git versions); they do not change user identity. GPU audit fixtures are under `target/git-audit`. Set `SCENEMAX_SMOKE_GIT` to `commit`, `branches`, `history`, `config` or `clone` only with an explicit bounded IDE smoke launch to open that dialog for capture.
