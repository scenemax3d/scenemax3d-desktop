# Project Rules

The SceneMax projector/runtime code is a generic engine.

Never add game-specific code, asset names, entity names, animation names, fallback assets, hardcoded variables, or behavior for a specific game inside projector, engine, or runtime modules.

Examples of forbidden engine code:

- Hardcoding `player1`, `player2`, `boss`, `fighter`, `rock`, `bone`, or other game-specific entity/resource names.
- Hardcoding animation clips like `idle2`, `run_sword`, or `HighKick`.
- Adding fallback assets from a specific game.
- Adding collision, camera, input, animation, object-pool, or state behavior that assumes the fighting game or any other specific project.

Game-specific behavior must live in scripts, project assets, project config, fixtures, or tests only. Engine code may support generic mechanisms, but must not encode one game's rules.
