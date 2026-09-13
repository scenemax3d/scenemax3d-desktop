# IDE application component

This crate contains the `scenemax_ide` executable and its application library. The entry point parses CLI options; `application/` owns commands and orchestration, and `presentation/` contains feature views and input adapters.

See the [IDE guide](../README.md) for launch instructions and the [architecture](../../docs/ARCHITECTURE.md) for component contracts. Domain state belongs in `../core`, infrastructure in `../services`, and reusable widgets in `../ui`.
