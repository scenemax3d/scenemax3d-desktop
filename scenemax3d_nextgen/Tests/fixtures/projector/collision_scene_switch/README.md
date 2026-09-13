# Collision-triggered scene switch regression

From the projector directory, run:

```powershell
./target/debug/scenemax_projector_nextgen.exe run --project-root fixtures/collision_scene_switch --script fixtures/collision_scene_switch/first/main
```

The guarded collision must log `SCENE_SWITCH_TEST CONTACT`, then load the sibling
scene and log `SCENE_SWITCH_TEST PASS: next room loaded`. It must never log
`FAIL: continued old handler`. The empty resources directory is required for
project initialization; the fixture needs no game assets.
