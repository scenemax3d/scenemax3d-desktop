# SceneMax NextGen Flow VM Report

Generated from logger-injected fixture runs against the Bevy projector.

## How to Run

```powershell
C:\Users\adikt\.cargo\bin\cargo.exe build -p scenemax_projector_nextgen
powershell -NoProfile -ExecutionPolicy Bypass -File C:\dev\scenemax_desktop\scenemax_projector_nextgen\tools\run_flow_vm_fixtures.ps1 -SecondsPerFixture 4
```

Per-fixture logs are written to:

```text
C:\dev\scenemax_desktop\scenemax_projector_nextgen\fixtures\logs
```

## Latest Results

| Fixture | Coverage | Result |
| --- | --- | --- |
| `flow_control_logger.code` | `if`, `else`, `do N times`, `do ... while`, local/global variable mutation | Pass |
| `expression_vm_logger.code` | `not`, `!`, `<>`, condition assignment, `rnd()` range | Pass |
| `when_recurring_logger.code` | `when ... do`, recurring `run every` smoke coverage | Pass |
| `vm_if_guard_logger.code` | `else if`, statement guards, guard aliases, guarded function definitions, procedure `return` | Pass |
| `vm_expression_matrix_logger.code` | arithmetic precedence, parenthesized arithmetic, modulo, `round()`, booleans, `distance()`, coordinate symbols, `rnd()` | Pass |
| `vm_when_after_logger.code` | `when condition after previous_condition`, phase transitions from recurring controllers | Pass |
| `vm_wait_async_logger.code` | `wait`, `wait for`, `do async`, parent continuation after async child | Pass |
| `vm_recurring_guard_logger.code` | recurring function guard re-evaluation and stop when guard becomes false | Pass |

Last run: all fixtures produced zero `FAIL:` markers and zero unexpected `[ERROR]` lines.

## Issues Found and Fixed

- Startup `do ... while` blocks were parsed but not executed. Fixed in the startup action executor.
- `return` inside startup/function execution did not stop the containing block, so actions after `return` still executed. Fixed by propagating `ActionSequenceResult` through startup action execution.
- Comparisons containing parenthesized arithmetic, for example `if ((a + b) * c == 18)`, could be skipped because comparison splitting ignored nesting. Fixed by splitting comparison operators only at top-level parenthesis depth and by stripping wrapping parentheses in assignment values.

## Covered VM Areas

- Basic branching: `if`, `else if`, `else`.
- Boolean composition: `&&`, `||`, `!`, `not`, `true`, `false`.
- Equality and inequality: `==`, `!=`, `<>`.
- Numeric comparisons: `<`, `<=`, `>`, `>=`.
- Arithmetic values: `+`, `-`, `*`, `/`, `%`, precedence, nested parentheses.
- VM helper values: `rnd()`, `round()`, `distance()`.
- Variable resolution: local variables, global variables, guard aliases, coordinate symbols such as `p2.x`.
- Guarded statements and guarded function definitions.
- Recurring controllers and guard re-evaluation.
- `when` events, including `after` conditions.
- Delayed flow: `wait`, `wait for`, `do async`.
- Logger runtime command for deterministic fixture traces.

## Remaining Gaps to Prioritize

- Full Java `ActionLogicalExpressionVm` parity for string expressions, especially concatenation and mixed string/numeric logger or UI messages.
- Function return values in general expressions. Returned factory symbols are used for object-pool prototypes, but normal `run x()` return values are not yet expression values.
- Array/index/property access beyond the currently normalized collider cases, for example richer `foo["bar"]` and nested VM object structures.
- Animation event callbacks such as `anim.event(...) = { ... }`; these likely affect hit flags and punch/kick reactions.
- More exact Java scoping checks around nested function calls and returned values. The current fixtures cover local/global mutation and aliases, but not every Java VM stack-frame edge case.
- Collision/controller integration fixtures that attach box/sphere colliders to bones and assert `when collider collides with collider do` logic through logger statements.
