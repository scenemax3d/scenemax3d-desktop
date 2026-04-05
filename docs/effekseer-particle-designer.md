# Effekseer Particle System Designer

## Status Update

The original goal of this document was import plus preview first, with runtime scripting deferred until later.

That runtime step is now available.

Current SceneMax support includes:

- importing Effekseer assets into `resources/effects/<assetId>/`
- previewing them in the Effekseer designer
- declaring runtime effect objects with `effects.effekseer.<assetId>`
- playing them with `pos (...)`
- passing runtime attributes through `attr = [ ... ]`
- aiming them with `look at`
- attaching and detaching them like other scene objects
- showing, hiding, deleting, packaging, and scene-switch cleanup

For the scripting/runtime reference and examples, see [Effects](./effects.md).

## Goal

Create a new particle-system designer flow in SceneMax that is based on the open source [Effekseer](https://effekseer.github.io/en/) project, not on JME's particle system.

The first and most important milestone is:

- import an existing Effekseer effect into a SceneMax project
- preview it faithfully inside the designer
- keep the imported asset self-contained inside the project

Editing Effekseer graphs, mapping them into SceneMax language syntax, and runtime gameplay wiring are intentionally deferred until import and preview are solid.

## Why A New Path Is Required

Today SceneMax particle effects are hardcoded language/runtime features:

- the grammar exposes named built-in effects such as `Flash`, `Explosion`, `Debris`, `Spark`, `SmokeTrail`, `ShockWave`, and `Fire` in [scenemax3d_parser/SceneMax.g4](../scenemax3d_parser/SceneMax.g4)
- the projector creates those effects with JME `ParticleEmitter` instances in [scenemax_win_projector/src/com/scenemaxeng/projector/SceneMaxApp.java](../scenemax_win_projector/src/com/scenemaxeng/projector/SceneMaxApp.java)

That is the exact coupling we do not want for the new designer. We should not translate Effekseer assets into JME emitters, because that would immediately lose compatibility, authoring fidelity, and future upgrade headroom.

## Effekseer Facts That Drive The Design

The official Effekseer docs and repository establish a few constraints:

- the editor opens effect files directly from the file browser, including `.efkefc` files
- the tool supports import and export workflows as first-class operations
- the viewer/editor supports environment preview settings such as background, ground, bloom, and tonemap
- the official runtime is delivered as native runtime code with graphics backends such as DirectX and OpenGL rather than as a Java/JME runtime

Design implication:

- SceneMax should treat Effekseer as an external effect format and runtime stack
- preview should be powered by Effekseer itself, or by a thin native host built on Effekseer runtime, not by JME particles

## Product Scope

### In scope for phase 1

- import Effekseer assets into a SceneMax project
- preview imported effects in a dedicated designer panel
- support play, stop, restart, loop, speed, camera orbit, and background/ground controls
- show missing dependency errors for textures, models, sounds, materials, and curves
- persist SceneMax-side metadata in a new designer document
- use the existing `effekseer/` sample corpus in this repository as an acceptance-test corpus

### Explicitly out of scope for phase 1

- editing node graphs or particle parameters
- converting Effekseer assets into JME emitters
- extending SceneMax language syntax
- attaching effects to gameplay entities at runtime
- Android export

## Recommended Architecture

### 1. New document type

Add a dedicated particle designer document instead of overloading `.smdesign`.

Suggested extension:

- `.smeffectdesign`

Suggested JSON shape:

```json
{
  "version": 1,
  "assetId": "fire_burst_01",
  "source": {
    "importedFile": "resources/effects/fire_burst_01/main.efkefc",
    "originalFormat": "efkefc",
    "originalImportPath": "C:/temp/fire_burst_01.efkefc",
    "importedAt": "2026-04-03T22:30:00Z"
  },
  "preview": {
    "loop": true,
    "playbackSpeed": 1.0,
    "backgroundMode": "dark",
    "showGround": true,
    "camera": {
      "distance": 12.0,
      "yawDeg": 35.0,
      "pitchDeg": -15.0
    }
  },
  "placement": {
    "position": [0.0, 0.0, 0.0],
    "rotation": [0.0, 0.0, 0.0, 1.0],
    "scale": [1.0, 1.0, 1.0]
  }
}
```

This document stores SceneMax metadata only. The Effekseer asset stays in its native format beside it.

### 2. Imported asset layout

When the user imports an effect, SceneMax should copy it into the project so preview is stable and portable.

Suggested layout:

```text
resources/
  effects/
    fire_burst_01/
      fire_burst_01.smeffectdesign
      main.efkefc
      Texture/
      Model/
      Sound/
      Material/
      Curve/
```

Rules:

- keep relative paths intact under the imported root
- never rewrite the effect into JME-specific data
- keep the native source file as the canonical asset
- allow reimport from the original path later

### 3. Supported import formats

Phase 1 should accept:

- `.efkefc` as the preferred runtime-ready format
- `.efkproj` for projects already authored in Effekseer
- `.efk` if encountered in older content

Recommended handling:

- if the file is already `.efkefc`, import it directly
- if the file is `.efkproj` or `.efk`, keep the original and also produce a normalized preview target if needed
- if the effect references external resources, import the entire sibling asset tree, not only the root file

If we later decide package import matters, `.efkpkg` can be added as a wrapper format on top of the same internal layout.

### 4. Preview runtime

Do not preview with JME `ParticleEmitter`.

Instead, add a small native preview host based on the official Effekseer runtime:

- Windows executable or DLL, since Windows is the primary platform today
- uses Effekseer runtime plus a supported rendering backend
- owns effect loading, playback, camera, and preview environment controls
- can run either:
  - embedded inside a Swing panel via a child native window handle, or
  - as a separate preview window for the first delivery if embedding is too expensive

For the first milestone, a separate preview window is acceptable if it gets us to faithful import-and-preview faster. The document should optimize for correctness first, embedding second.

### 5. Java-to-native bridge

Keep the contract narrow and asynchronous.

Recommended control API:

```text
loadEffect(projectRoot, effectFile)
play(loop)
stop()
restart()
setPlaybackSpeed(speed)
setCameraOrbit(yawDeg, pitchDeg, distance)
setBackground(mode)
setGroundVisible(flag)
resize(width, height)
queryDiagnostics()
captureThumbnail(outputPath)
shutdown()
```

Transport options:

- preferred for phase 1: out-of-process JSON-RPC over stdin/stdout or localhost
- later optimization: in-process JNI/JNA bridge

Why start out-of-process:

- native crashes do not take down the IDE
- easier to iterate on the host separately
- simpler debugging during asset compatibility work

### 6. Designer UI

Add a dedicated "Particle Effect" designer tab with:

- asset path and import status
- preview viewport
- transport controls: play, pause, stop, restart
- loop toggle
- playback speed dropdown
- background preset selector
- ground toggle
- camera reset and orbit controls
- diagnostics panel for missing assets and load errors

This should feel like an asset designer, not like the scene designer with a particle entity dropped into it.

## Import Pipeline

### Import flow

1. User chooses `Import Effekseer Effect...`
2. SceneMax accepts `.efkefc`, `.efkproj`, or `.efk`
3. Importer resolves all referenced sibling assets under the effect root
4. Importer copies the effect bundle into `resources/effects/<assetId>/`
5. SceneMax creates `<assetId>.smeffectdesign`
6. Preview host loads the copied asset from the project path
7. Designer shows diagnostics if any dependency is missing

### Dependency resolution

Phase 1 should be conservative:

- preserve directory structure exactly
- validate by attempting to load with Effekseer runtime
- surface load diagnostics rather than trying to "repair" paths automatically

This avoids inventing a partial parser before preview works.

## Runtime Boundary For Later

Phase 1 stops at import plus preview, but the document should leave a clean seam for later runtime integration.

Recommended later contract:

- SceneMax runtime gets a new `EffekseerEffectInstance` abstraction
- scene/language code references imported effect assets by project-relative id
- projector delegates playback to the same Effekseer-native integration layer used by the designer

Important rule:

- the preview path and gameplay path should share the same native Effekseer loading code

That keeps the designer truthful and prevents "looks right in the editor, different in game" drift.

## Repo Fit

### Existing assets we can use immediately

This repository already contains a large local Effekseer sample corpus under [effekseer/](../effekseer/), including `.efkproj` files with textures and models. That makes it a strong built-in compatibility suite for phase 1.

### Existing system we should not extend for this milestone

- current script-facing particle features in [docs/effects.md](./effects.md)
- current built-in effect grammar in [scenemax3d_parser/SceneMax.g4](../scenemax3d_parser/SceneMax.g4)
- current JME emitter implementation in [scenemax_win_projector/src/com/scenemaxeng/projector/SceneMaxApp.java](../scenemax_win_projector/src/com/scenemaxeng/projector/SceneMaxApp.java)

We should leave those paths unchanged until the Effekseer import-and-preview path is proven.

## Delivery Plan

### Phase 1A: asset import plus standalone preview

- create `.smeffectdesign`
- build importer for `.efkefc`, `.efkproj`, `.efk`
- launch native Effekseer preview host in a separate window
- add diagnostics and thumbnail capture

Success criteria:

- import at least 20 effects from the local `effekseer/` corpus
- preview matches Effekseer viewer closely enough for artist verification
- missing assets are clearly reported

### Phase 1B: embedded preview panel

- host preview inside the designer tab
- persist preview camera/environment settings in `.smeffectdesign`

### Phase 2: SceneMax runtime playback

- add projector/runtime integration using the same native loader
- define a stable asset id for script/runtime use

### Phase 3: editing and language wiring

- decide how users modify imported effects
- decide whether SceneMax stores overrides, wrappers, or full editable copies
- add language/API support only after preview and runtime playback are stable

## Key Risks

- native integration complexity on Windows
- effect compatibility gaps if we use an incomplete bridge
- dependency resolution for imported folders with nontrivial relative paths
- render-surface embedding complexity if we insist on embedding too early

Mitigation:

- use the official Effekseer runtime instead of custom particle conversion
- start with out-of-process preview hosting
- validate against the local sample corpus before exposing runtime scripting hooks

## Recommendation

The fastest path to the requested outcome is:

1. introduce a new Effekseer-native particle asset document
2. import native Effekseer files into project-local asset folders
3. preview them with an Effekseer-based native host
4. defer editing and SceneMax language/runtime wiring until preview fidelity is solved

That keeps us aligned with the user's requirement: import and preview real Effekseer particle systems without relying on the JME particle system at all.

## References

- [Effekseer official site](https://effekseer.github.io/en/)
- [Effekseer tool reference](https://effekseer.github.io/Help_Tool/en/)
- [Effekseer runtime reference](https://effekseer.github.io/Help_Runtime/en/index.html)
- [Effekseer GitHub repository](https://github.com/effekseer/Effekseer)
