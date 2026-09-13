# SceneMax NextGen Flow/Expression VM Tracker

## Logger Fixtures

Run with:

```powershell
C:\dev\scenemax_desktop\scenemax_projector_nextgen\target\debug\scenemax_projector_nextgen.exe run --project-root C:\dev\scenemax_desktop\scenemax_projector_nextgen\fixtures --script C:\dev\scenemax_desktop\scenemax_projector_nextgen\fixtures\scripts\flow_control_logger.code
```

The trace file is:

```text
C:\dev\scenemax_desktop\scenemax_projector_nextgen\fixtures\scenemax-nextgen-runtime.log
```

## Covered

- Basic logger command: `Logger.info`, `Logger.debug`, `Logger.error`.
- Literal and numeric/expression logger messages.
- `if` / `else` execution.
- `do N times` blocks.
- `do ... while` blocks.
- Function-local variables versus global variable mutation.
- Boolean expression assignment to numeric truth values.
- Java VM condition forms: `!`, `not`, `<>`, `true`, `false`.
- `when` and `run every` smoke coverage.
- `return value` parsing for object-pool factories.

## Fixed By Logger Fixtures

- Startup `do ... while` blocks were parsed but ignored by startup execution. The `flow_control_logger.code` trace exposed this as a missing `while:tick` and a final `flow:bad-state`. Startup `DoWhile` now executes and the fixture reaches `flow:done`.

## Current Suspect Areas

- Full string expression VM: `"Score: " + score` is not yet represented as a runtime value.
- Function return values are parsed and used for pool prototypes, but normal `run x()` calls do not yet expose returned values to expressions.
- Logger message expressions currently support numeric/condition expressions, not string concatenation.
- Animation event callbacks such as `anim.event(...) = { ... }` are still no-op; this can affect hit flags and richer fighting reactions.
- VM array/JSON/UI property access opcodes from the Java VM are not implemented yet.
- Collision references with bracket/index syntax such as `weapon.colliders["name"]` are only partially normalized.
- Recurring controllers continue until their guard becomes false or scene changes. Fixtures that log `run every` should include a guard/stop condition when testing exact counts.
