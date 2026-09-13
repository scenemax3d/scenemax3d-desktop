# Shared capabilities

`assets` owns the existing shared asset catalog and resolution library. Common code must not depend on the IDE or projector executable. Add a separately owned project-format crate when actual shared serialization contracts are extracted; keep editor session state in `IDE/core`.
