# SceneMax Studio packaging

Use [build-studio-installer.ps1](build-studio-installer.ps1) to build the Windows
installer for the Rust/Bevy IDE and runtime:

```powershell
.\INNO-SETUP\build-studio-installer.ps1
```

Publish a stable GitHub release marked **Latest** first, run this script, then
attach the resulting installer to that release. The script fetches the latest tag
once and derives the installer version (`v3.0.2` becomes `3.0.2`). Drafts and
prereleases are not selected. The tag is also embedded separately to prevent
redundant updates on fresh installations.

Use `-ReleaseTag v3.0.2` to override GitHub discovery (also works offline), or
`-AppVersion 3.0` to override the installer version/filename. Both can be supplied
together. Nonnumeric tags require an explicit `-AppVersion`.

See [the Studio installer guide](STUDIO.md) for prerequisites, payload contents,
installation options, and validation scripts. For packaging games, see the
[deployment guide](../scenemax3d_nextgen/IDE/DEPLOYMENT.md).
