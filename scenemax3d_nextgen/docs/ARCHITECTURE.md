# Architecture and engineering standards

## Ownership

`scenemax3d_nextgen` owns all first-party NextGen Rust components. IDE and Projector are sibling products. Cargo is used as a shared build workspace, not as an application dependency. Each crate has a specific responsibility and independent tests. Create new crates for meaningful dependency or ownership boundaries; use modules for ordinary features.

The Swing IDE is the behavioral and architectural reference for each migration slice. Preserve its separation of editing, project management, design tools and supporting services while expressing those boundaries idiomatically in Rust. Do not translate Java classes mechanically or put feature implementations into the executable bootstrap.

## Allowed dependency direction

`Projector/launcher` is an independent distribution bootstrap with safe archive extraction and owned projector launch/wait logic. It has no IDE or Bevy dependency. IDE/services builds it through Cargo and appends a snapshot; it never links either executable. Domain settings, service jobs, application orchestration and the retained deployment form follow the normal IDE boundaries. Explicit Android/iOS/Web SDK adapters do not imply completed runtime ports; see [deployment status](../IDE/DEPLOYMENT.md).

```text
IDE/app ──> IDE/core
        ├─> IDE/services ──> IDE/core, Language/parser
        └─> IDE/ui ────────> Bevy UI

Projector/app ──> Engine/runtime ──> Engine/*, Common/assets, Language/*
```

* `IDE/core` contains domain state only: no Bevy, filesystem operations, threads or processes. Document text and selection are mutated through methods that preserve invariants.
* `IDE/services` performs infrastructure operations through typed APIs. It must remain usable without a renderer, window, IDE executable, or projector library. Launching a projector process does not imply linking to its crate.
* `IDE/ui` contains reusable presentation primitives with caller-owned components. It does not know about documents, scripts, project catalogs, command services or engine entities.
* `IDE/app/application` owns commands, orchestration and session state. It has no widget spawning or view-entity queries. Buttons, keyboard shortcuts and later automation use the same command path.
* `IDE/app/presentation` maps widget input into commands and projects application state into retained views. It does not read files, parse scripts or own child processes.
* Engine, Language and Common must never depend on IDE implementation. Editor entities and selection state must never enter exported runtime state.
* `Common` contains actual shared capabilities. Add a `project_format` crate when shared typed project/scene serialization is extracted; do not create empty placeholder crates or a catch-all utilities library.

`Tools/check_architecture.py` validates workspace locations and dependency direction from Cargo metadata, checks new IDE lint inheritance, and rejects infrastructure operations in the domain layer. Update its policy and this document together when adding a justified component.

## State, commands and jobs

`EditorWorkspace` owns stable document IDs, buffers and active selection. IDs survive tab ordering and are not reused when switching projects. Documents expose revision-aware text updates; UI caret changes do not create source revisions. Internal newlines are LF, including pasted CRLF text.

Native Bevy text edits run in PostUpdate. IDE systems run in a defined Last-stage order: synchronize buffers, collect commands, execute commands, receive job results, reconcile structural view changes, refresh labels. This guarantees that Save and close guards see the latest widget contents. Views are not reconstructed per keystroke.

The diagnostics service owns one worker with one outstanding request. Results carry document identity and revision and are rejected after edits or project changes. The process service owns one projector child, launches it through structured arguments, reads bounded output chunks, and never terminates unrelated processes.

Filesystem operations are centralized in a storage service. Reads are bounded; project scans avoid links/junctions; saves compare the original bytes and replace through a same-directory temporary file. A failed save leaves the buffer dirty. These are optimistic conflict checks, not a filesystem transaction against arbitrary concurrent writers.

Project inventory, document reads and saves run on a dedicated storage worker, including startup. The application submits owned snapshots without borrowing live state. Exactly one request or unconsumed result occupies the worker slot; repeated file actions report busy rather than accumulating jobs. Typing and selecting existing tabs remain available.

Save completion updates the saved baseline without replacing current text or changing its revision. Writes are serialized and acknowledgements applied in order. Save All applies successful acknowledgements even if other files fail. Run and Save All on close wait for completion and recheck dirty state; newer edits prevent running or exiting. Stop cancels a run deferred behind a save. Cancel on the close dialog cancels the exit intent while allowing the write to finish. An orderly exit cannot interrupt an outstanding save.

Project replacement rechecks dirty buffers when loading completes, since users can edit during a scan. Read cancellation and prioritization/progress for long operations remain upcoming. Undo/redo, selection-aware editing commands and recovery checkpoints are implemented. Saves cannot currently be cancelled once submitted. Snapshot copies and native text layout still run on the UI thread, so background I/O alone does not establish large-file responsiveness.

## Rust standards

* Pin the compiler and centralize dependency versions. Enable Bevy features per consuming product; do not force 3D/audio features into a UI-only build.
* Keep executable entry points to CLI parsing and composition. Use narrow modules and crate-private visibility by default.
* New IDE crates inherit workspace lints and forbid unsafe code. Public library APIs have documentation. Services use typed errors; application boundaries attach user-facing context.
* Do not use panic/unwrap/expect for recoverable application or service failures. Assertions belong in invariant tests; test fixtures may unwrap expected setup results.
* Do not add blanket lint suppressions to pass CI. Use meaningful type aliases and explicit boundaries where signatures become hard to read.
* No game-specific resource/entity/animation names or behavior in engine/runtime code. Such data belongs in scripts, project configuration or tests, as required by the repository's AGENTS.md.
* Avoid global mutable registries and unmanaged child processes. Background work has an owner, bounded communication and a defined shutdown path.

## Verification

`Tools/verify.ps1` is the local/CI entry point. It runs architecture checks and their negative cases, formatting and warning-free Clippy for the new IDE crates, a whole-workspace compile check, and library tests. IDE and runtime test/build invocations are separated to avoid unnecessary Bevy feature unification. The existing engine crates retain their compiler/test baseline; tightening their historical lint baseline is separate work, not a reason to suppress new IDE warnings.

Tests cover domain transitions without Bevy; actual filesystem safety in temporary directories; stale diagnostics; safe process argument construction; and headless application behavior using the real command dispatcher. GPU smoke captures verify startup and layout separately. Full interactive keyboard, clipboard, IME, accessibility and designer parity require explicit acceptance testing.

Measure launch time, typing latency, idle CPU, large-document behavior and memory against Swing on the same hardware. Performance targets in the migration roadmap remain targets until measured; a successful screenshot is not a performance result.

The Rust CI workflow verifies the default Windows build without invoking Java. Native Effekseer builds need their external C++ sources/SDKs and remain a distinct configuration. The final migration gate must additionally prove clean-machine distribution with every Java tool and payload removed.

## Working editor milestone

The domain owns bounded delta history, Unicode-safe selections, literal find/replace, line navigation, indentation/comments and explicit clean/discard tab transitions. History remains local to each live document. Persistence snapshots omit history, so background saves and project search do not clone undo stacks.

Presentation projects command results back into existing native text widgets and restores selections. Focus changes into toolbar fields must not overwrite the document's last editor selection. Editor-specific undo shortcuts do not act while a find/project field owns focus. Tab indentation intercepts the bubbled input on the editor host before window tab navigation. Gutter labels include only visible line numbers and avoid rescanning unchanged state.

Recovery is a storage-worker adapter using immutable, versioned JSON checkpoints under the project's Git-ignored `.scenemax-studio/recovery`. A checkpoint captures original disk bytes plus dirty text; no original file is changed. Session snapshots are atomically published and flushed before old accepted checkpoints are retired. Read paths are bounded and canonicalized within the project. The newest checkpoint for a file shadows older copies, including when its text is already saved. Explicit restoration opens new document identities and preserves optimistic save-conflict detection. Orderly exit retires this session's checkpoints; a crash before the next successful checkpoint can still lose the latest edits.

Project search uses open-buffer snapshots in preference to disk text, caps byte/result counts, reports skipped files and rejects results after open-buffer revisions change. Result locations are domain data; presentation converts a result click into an application navigation command. The console keeps bounded output separately from status, refreshing only when its output revision changes.


### Desktop shell and navigator

The presentation layer owns menu visibility, transient command panels and folder expansion/focus. The domain `ProjectEntry` contains only validated file paths and entry kinds; services build a bounded tree inventory on the disk worker. Presentation never reads the filesystem. Menu, toolbar, tab and keyboard input reuse application commands, including the same unsaved-document guards when closing a specific tab. Shared theme and button surfaces live in `IDE/ui`; editor features stay in `IDE/app`. Tree filters preserve matching ancestors and use a set of matching paths rather than rescanning all descendants for every folder.


Designer embedding must use the retained Bevy UI [hosting contract](DESIGNER_HOSTS.md). `IDE/ui::panels` owns reusable panel surfaces, native splitters and measured host extents; designer state and camera ownership remain in feature plugins.


## Syntax presentation boundary

`IDE/core/syntax.rs` owns SceneMax lexical categories and UTF-8 ranges, without Bevy or filesystem dependencies. The application maps categories to the Java editor palette. `IDE/ui/syntax.rs` accepts generic source/color spans and colors native Bevy UI glyphs during render extraction. It uses Parley's visual cluster order, caches mappings by component change ticks, rejects stale source/layout, preserves selection foreground overrides and colored emoji, and leaves IME preedit rendering to Bevy. No second text layer, engine dependency or Bevy/Parley fork is introduced. The adapter targets pinned Bevy 0.19; upgrades must revalidate cluster-to-glyph ordering with Unicode/ligatures and GPU captures. Full-buffer lexing/layout remains a known scaling limitation.


## Editing assistance

`IDE/core/assistance.rs` computes indentation and indexes bracket pairs from lexical token ranges. Presentation adapts pending native newline operations before `EditableTextSystems`, applying each event through Bevy's existing text-edit path so same-frame cursor changes and edits stay ordered. The document synchronization still runs afterward, producing one history transaction. Paste, IME and custom-filter inputs bypass the adapter. Bracket matching uses the source-index snapshot installed with syntax colors. `TextEmphasis` is a generic UI byte-range decoration; native render extraction applies the existing text transform, DPI scale and clip to its outline primitives. No editing rule enters the engine or projector.


## Completion boundary

`IDE/core/completion.rs` provides a bounded, ranked static catalog copied from the Java completion source and lexical declarations from unsaved source, with UTF-8 prefix ranges. The application owns each popup's exact source/caret snapshot, input arbitration before native edits, dismissal and acceptance. Suggestions use `IDE/ui/completion.rs` retained nodes; the reusable UI has no language dependency. Accepted candidates enter the existing native text queue and document undo synchronization. No filesystem or parser process is called by popup rendering. Full project semantic indexing and incremental large-buffer updates remain separate future work. `Tools/check_java_completion_parity.py` prevents static-catalog drift during the side-by-side migration.


### Background project declarations

IDE/services owns a bounded symbol-index worker and source cache. It parses Rust AST top-level declarations without walking nested function/event scopes. IDE/app snapshots the validated script inventory and open buffers after a 350 ms debounce; project identity, inventory, refresh generation and document revisions guard publication. Unsaved buffers override disk, even after a disk deletion. Tree filtering does not invalidate the index. The core merges project candidates with local lexical declarations and static vocabulary, with local names taking precedence. The retained popup refreshes matching source/caret snapshots and reports indexing coverage. Include visibility, resource catalogs, parameter scopes and expression types remain outstanding Java parity work.


### Include-aware completion visibility

The symbol worker now caches direct top-level `Add Code` relationships along with declarations. Resolution mirrors the projector's source-relative paths, leading-slash convention and `.code` fallback, but targets must belong to the validated inventory. The immutable report exposes a cycle-safe traversal rooted at project `main` and the active document. Unrelated scripts no longer contribute suggestions. Unsaved include edits use the existing revision/debounce publication guards; unresolved targets appear in popup coverage. Tests exercise transitive includes, cycles, relative traversal, exact-file precedence, unsaved edge replacement and native completion refresh. Resource catalogs, function parameter scopes and expression types remain outstanding.


### Embedded UI designer documents

`.smui` tabs share existing document identities, revisions, saving, recovery and history; they own retained designer entities instead of a script input. IDE/core owns bounded lossless JSON mutations, preserving unknown properties. IDE/services adapts the schema and constraint solver from Engine/runtime_ui, a pure Rust/serde crate with no Bevy, filesystem, process or window dependencies. This specific dependency is allowed by the architecture checker; the full runtime/projector remain forbidden IDE dependencies. IDE/ui owns generic retained canvas scaling and font scaling, reusable for future designer documents. IDE/app owns selection, draft property input, command routing and view construction. Edits check the displayed revision before applying; a parse/layout failure cannot overwrite source. Preview solving is bounded and runs only on revision or selection changes. Rendering remains in the same Bevy app.


### Separate 3D document import

The app routes `.smdesign` to a read-only 3D document host instead of the text or UI-layout editor. Services resolve immutable scene snapshots on a dedicated Storage worker, using Common/assets for project model lookup. Application state ties publication to document identity/revision, owns one active world and offscreen camera, and discards results after document changes. Native Bevy ViewportNode owns viewport sizing/input integration. Model assets use WorldAssetRoot and retain glTF resource transforms. World/render construction, retained view construction and lifecycle coordination are separate modules under IDE/app/presentation/scene3d. The IDE links Bevy's 3D features, never the projector or full runtime. No embedded game code is executed. Import remains read-only until serialized edits and companion SceneMax code generation can be kept consistent.

The application registers a scoped `project` asset reader before Bevy plugins initialize. Each canonical resource root receives a stable source prefix, so assets from different projects cannot share a cache key. Both models and glTF dependencies resolve through that reader; canonical containment checks reject paths outside registered resource roots. The reader runs on Bevy asset workers and does not write project files.
