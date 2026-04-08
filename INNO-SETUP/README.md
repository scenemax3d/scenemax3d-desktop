# SceneMax3D Packaging

## Windows

Use [build-installer.ps1](/C:/dev/scenemax_desktop/INNO-SETUP/build-installer.ps1). It does four things in order:

1. Builds the desktop fat JAR and projector artifact with Gradle.
2. Regenerates `LAUNCH4J-PROJECT\scenemax3d.exe` from the current fat JAR.
3. Compiles [scenemax-setup-project.iss](/C:/dev/scenemax_desktop/INNO-SETUP/scenemax-setup-project.iss) with Inno Setup.
4. Signs the generated app EXE and final setup EXE if you pass a PFX certificate.

Examples:

```powershell
.\INNO-SETUP\build-installer.ps1
.\INNO-SETUP\create-dev-code-signing-cert.ps1
.\INNO-SETUP\build-installer.ps1 `
  -SignPfxPath .\INNO-SETUP\certs\scenemax-dev-code-signing.pfx `
  -SignPfxPassword changeit
```

You can also invoke the Windows packaging flow from Gradle:

```powershell
.\gradlew.bat buildWindowsSetup
.\gradlew.bat buildWindowsSetup -PinstallerSignPfxPath=.\INNO-SETUP\certs\scenemax-dev-code-signing.pfx -PinstallerSignPfxPassword=changeit -PinstallerSignAlias=scenemax-dev-code-signing
```

The generated installer is written to `INNO-SETUP\Output`.

## Signing

[create-dev-code-signing-cert.ps1](/C:/dev/scenemax_desktop/INNO-SETUP/create-dev-code-signing-cert.ps1) creates a self-signed development certificate for local testing. It proves the signing pipeline works, but it is not a production-trusted certificate.

The Windows build script signs with the bundled `jsign` tool and a PKCS#12/PFX certificate. That works well for local/dev certificates and also supports production certificates when you replace the PFX and alias.

For production releases you should replace it with a real code-signing certificate from a trusted CA. Otherwise Windows SmartScreen and other machines will still warn users.

## Linux and macOS

Inno Setup is Windows-only. Native Linux and macOS packages must be built on Linux and macOS respectively, but the repo now includes starter `jpackage` scripts:

- [build-linux-package.sh](/C:/dev/scenemax_desktop/INNO-SETUP/build-linux-package.sh)
- [build-macos-package.sh](/C:/dev/scenemax_desktop/INNO-SETUP/build-macos-package.sh)

Examples:

```bash
./INNO-SETUP/build-linux-package.sh app-image
./INNO-SETUP/build-linux-package.sh deb
./INNO-SETUP/build-macos-package.sh app-image
./INNO-SETUP/build-macos-package.sh dmg
```

These scripts package the same app payload that Windows uses:

- `scenemax_desktop.jar`
- `out/artifacts/scenemax_win_projector.jar`
- `Launch4j`
- `resources`
- `macro`
- `export_targets/android_native.zip`

## Notable installer changes

- The Windows installer keeps the bundled `Launch4j` toolchain because the IDE uses it later to generate EXE files.
- The installer does not seed `data\scenemax3d.db`; the app creates it on first run.
- It installs per-user under `%LOCALAPPDATA%\Programs\SceneMax3D` instead of writing into `Program Files`.
- It keeps the app working directory as `{app}`, which matches the current runtime's use of `launcher*.jar`, `build_games`, and other relative paths.
