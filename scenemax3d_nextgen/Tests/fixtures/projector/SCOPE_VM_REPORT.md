# SceneMax NextGen Scope VM Report

Generated from logger-injected scoping stress fixtures against the Bevy projector.

## How to Run

```powershell
C:\Users\adikt\.cargo\bin\cargo.exe build -p scenemax_projector_nextgen
powershell -NoProfile -ExecutionPolicy Bypass -File C:\dev\scenemax_desktop\scenemax_projector_nextgen\tools\run_scope_vm_fixtures.ps1 -SecondsPerFixture 4
```

Per-fixture logs are written to:

```text
C:\dev\scenemax_desktop\scenemax_projector_nextgen\fixtures\logs
```

## Latest Results

| Fixture | Coverage | Result |
| --- | --- | --- |
| `scope_blocks_logger.code` | Procedure locals across `if` / `else`, `do N times`, `do ... while`, and global mutation after a call | Pass |
| `scope_call_chain_logger.code` | Function calling another function, callee sees caller scope copy, callee locals do not leak, globals mutate through call chain | Pass |
| `scope_async_logger.code` | `do async`, delayed async continuation scope capture, parent scope continuing independently, parent scope surviving `wait` | Pass |
| `scope_return_logger.code` | Child `return` stops child tail only, caller continues after child return, parent `return` stops parent tail, event controller continues after procedure call | Pass |
| `scope_nested_logger.code` | Three-level nested procedures, deep local isolation, deep global update propagation | Pass |
| `scope_shadow_global_logger.code` | `var` local shadowing of an existing global symbol | Pass |

Last run: all executable scope fixtures produced zero `FAIL:` markers and zero unexpected `[ERROR]` lines.

## Issues Found and Fixed

- `var` declaration intent was lost in the AST, so `var shadow_value = 5` inside a procedure overwrote an existing global `shadow_value`. The parser now emits `LocalAssignment` for `var` assignments inside executable blocks, and the runtime forces those assignments into the active scope.
- A `return` from a called procedure was propagating to the caller and could abort the caller's remaining statements. Procedure calls now consume child `Returned` results as a completed call boundary; only suspended/delayed execution propagates.

## Covered Scope Areas

- Function/procedure-local variables.
- Local mutation across nested `if` and loop blocks inside the same procedure frame.
- Global variable mutation from inside procedures.
- Local variable shadowing of an existing global using `var`.
- Caller/callee scope copy behavior.
- Nested procedure isolation across three call levels.
- `return` boundaries in child and parent procedures.
- `wait` and delayed parent continuation preserving scope.
- `do async` continuation preserving the scope snapshot at suspension time.

## Current Scope Semantics Confirmed

- `var name = value` inside a runtime procedure creates or updates a local scoped symbol, even if a global with the same name exists.
- Plain `name = value` updates an existing local if one exists; otherwise it updates a global if one exists; otherwise it creates a local in the active procedure scope.
- Called procedures receive a copy of the caller scope. They can read caller locals, but their local writes do not mutate the caller's frame.
- Global writes remain visible after returning from nested procedure calls.
- `return` exits the current procedure only; it does not abort the caller or the event controller that called the procedure.

## Remaining Gaps

- `for each` / `foreach` syntax is not implemented in the Rust parser/runtime yet. A placeholder fixture is tracked at `fixtures/scripts/scope_foreach_unsupported.code` with the expected future logger assertions.
- Startup-time function execution still does not maintain a full procedure-local scope stack. The stress suite exercises runtime controller scopes, which are the important path for fighting-game logic, AI controllers, waits, and events.
- Return values are still not general expression values for normal procedure calls. This is separate from `return` control-flow boundaries and remains part of the wider expression VM parity work.
- Object/array/index scopes such as richer `foo["bar"]` structures still need Java VM parity work.
