[CmdletBinding()]
param(
    [string]$AppVersion = '3.0',
    [string]$Compiler = 'C:\Program Files (x86)\Inno Setup 6\ISCC.exe',
    [string]$WasmBindgen = '',
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'studio-payload-policy.ps1')
$repo = Split-Path -Parent $PSScriptRoot
$workspace = Join-Path $repo 'scenemax3d_nextgen'
if (!(Test-Path -LiteralPath $Compiler)) { throw "Install Inno Setup or supply -Compiler: $Compiler" }
if ($AppVersion -notmatch '^\d+\.\d+(\.\d+){0,2}$') { throw 'Use a numeric installer version such as 3.0.' }
if (!$SkipBuild) {
    Push-Location $workspace
    try {
        & cargo build --locked --release -p scenemax_ide -p scenemax_game_launcher -p scenemax_projector_nextgen --features scenemax_projector_nextgen/effekseer_native
        if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
        & cargo build --locked --release --target wasm32-unknown-unknown -p scenemax_projector_nextgen --no-default-features
        if ($LASTEXITCODE -ne 0) { throw 'Browser projector release build failed.' }
        & cargo build --locked --release -p scenemax_multiplayer --features web-gateway --bin scenemax_web_gateway
        if ($LASTEXITCODE -ne 0) { throw 'Windows browser gateway build failed.' }
        $oldLinker = $env:CARGO_TARGET_X86_64_UNKNOWN_LINUX_MUSL_LINKER
        $oldFlags = $env:CARGO_TARGET_X86_64_UNKNOWN_LINUX_MUSL_RUSTFLAGS
        try {
            $env:CARGO_TARGET_X86_64_UNKNOWN_LINUX_MUSL_LINKER = 'rust-lld'
            $env:CARGO_TARGET_X86_64_UNKNOWN_LINUX_MUSL_RUSTFLAGS = '-C link-self-contained=yes -C target-feature=+crt-static'
            & cargo build --locked --release -p scenemax_multiplayer --features web-gateway --bin scenemax_web_gateway --target x86_64-unknown-linux-musl --target-dir target/browser-gateway
            if ($LASTEXITCODE -ne 0) { throw 'Linux browser gateway build failed.' }
        } finally {
            $env:CARGO_TARGET_X86_64_UNKNOWN_LINUX_MUSL_LINKER = $oldLinker
            $env:CARGO_TARGET_X86_64_UNKNOWN_LINUX_MUSL_RUSTFLAGS = $oldFlags
        }
    } finally { Pop-Location }
}
# Bindings and Wasm must come from the same locked runtime release.
$bindgenVersion = [regex]::Match((Get-Content (Join-Path $workspace 'IDE\services\src\build_configuration.rs') -Raw), 'WASM_BINDGEN_VERSION: &str = "([^"]+)"').Groups[1].Value
if (!$WasmBindgen) {
    $versionedBindgen = Join-Path $env:LOCALAPPDATA "SceneMax\web-tools\$bindgenVersion\bin\wasm-bindgen.exe"
    $WasmBindgen = if (Test-Path -LiteralPath $versionedBindgen) { $versionedBindgen } else { 'wasm-bindgen' }
}
if ((& $WasmBindgen --version) -ne "wasm-bindgen $bindgenVersion") { throw "Install wasm-bindgen-cli $bindgenVersion to package the browser runtime." }
# Always use a new staging directory; never delete or overwrite project data.
$job = Join-Path $workspace ('target\studio-installer\' + [guid]::NewGuid().ToString('N'))
$payload = Join-Path $job 'payload'
New-Item -ItemType Directory -Path $payload -Force | Out-Null
$cleanup = [Collections.Generic.List[string]]::new()
function Add-ObsoleteFile([string]$Destination) {
    $relative = $Destination.Substring($payload.Length + 1)
    if ($relative.StartsWith('projects\')) { return }
    if ($relative.Contains('"') -or $relative.Contains('..')) { throw "Invalid cleanup path: $relative" }
    $cleanup.Add('Type: files; Name: "{app}\' + $relative + '"')
}
function Copy-SourceTree([string]$Source, [string]$Destination) {
    New-Item -ItemType Directory -Path $Destination -Force | Out-Null
    foreach ($item in Get-ChildItem -LiteralPath $Source -Force) {
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Source links must be reviewed before packaging: $($item.FullName)" }
        if ($item.PSIsContainer) {
            if ($item.Name -in @('user-state', 'target', '.git', 'node_modules', '__pycache__', 'artifacts', '.scenemax-studio', 'prebuilt', '.zig-cache')) { continue }
            if ($item.Name -ceq 'Tests' -or $item.Name -eq 'fixtures') {
                foreach ($obsolete in Get-ChildItem -LiteralPath $item.FullName -Recurse -File) {
                    Add-ObsoleteFile (Join-Path $Destination ($obsolete.FullName.Substring($Source.Length + 1)))
                }
                continue
            }
            Copy-SourceTree $item.FullName (Join-Path $Destination $item.Name)
        } else {
            $destinationFile = Join-Path $Destination $item.Name
            $relative = $destinationFile.Substring($payload.Length + 1)
            if (Test-StudioPayloadFile $relative) {
                Copy-Item -LiteralPath $item.FullName -Destination $Destination
            } else {
                # Retain license notices without retaining the associated source tree.
                if ($relative.StartsWith('scenemax3d_nextgen\') -and $item.Name -match '^(?i:LICENSE|NOTICE|COPYING)' -and $item.Extension -in @('', '.md', '.txt', '.rtf')) {
                    $notice = Join-Path $payload ('licenses\' + $relative.Substring('scenemax3d_nextgen\'.Length))
                    New-Item -ItemType Directory -Path (Split-Path -Parent $notice) -Force | Out-Null
                    Copy-Item -LiteralPath $item.FullName -Destination $notice
                }
                Add-ObsoleteFile $destinationFile
            }
        }
    }
}
Copy-SourceTree $workspace (Join-Path $payload 'scenemax3d_nextgen')
Copy-SourceTree (Join-Path $repo 'resources-basic\resources') (Join-Path $payload 'resources')
Copy-SourceTree (Join-Path $PSScriptRoot 'starter-projects') (Join-Path $payload 'projects')


# Keep only usable native model entries; preserve existing glTF alternatives.
$catalogPath = Join-Path $payload 'resources\Models\models.json'
$catalogText = Get-Content -LiteralPath $catalogPath -Raw
# Match the engine's support for trailing commas, including on Windows PowerShell 5.1.
# Quoted strings are consumed first so commas inside string values are untouched.
$catalogText = [regex]::Replace($catalogText, '("(?:\\.|[^"\\])*")|,(?=\s*[}\]])', {
    param($match)
    if ($match.Groups[1].Success) { $match.Value } else { '' }
})
$catalog = $catalogText | ConvertFrom-Json
$catalog.models = @($catalog.models | Where-Object {
    $entry = $_
    $path = Join-Path (Join-Path $payload 'resources') $entry.path
    if ([IO.Path]::GetExtension($path) -notin @('.gltf', '.glb')) {
        $stem = $path -replace '(\.mesh)?\.j3o$', ''
        $alternatives = @($stem, ($stem + '.gltf'), ($stem + '.glb'))
        $replacement = $alternatives | Where-Object { [IO.Path]::GetExtension($_) -in @('.gltf', '.glb') -and (Test-Path -LiteralPath $_) } | Select-Object -First 1
        if ($replacement) { $entry.path = $replacement.Substring((Join-Path $payload 'resources').Length + 1).Replace('\','/'); $path = $replacement }
    }
    [IO.Path]::GetExtension($path) -in @('.gltf', '.glb') -and (Test-Path -LiteralPath $path)
})
[IO.File]::WriteAllText($catalogPath, ($catalog | ConvertTo-Json -Depth 100), [Text.UTF8Encoding]::new($false))
# Only catalog models and their referenced buffers/textures belong in the payload.
$modelFiles = Get-StudioModelFiles (Join-Path $payload 'resources') $catalog
$modelRoot = [IO.Path]::GetFullPath((Join-Path $payload 'resources\Models')) + '\'
foreach ($file in Get-ChildItem -LiteralPath $modelRoot -Recurse -File) {
    if (!$modelFiles.Contains($file.FullName)) {
        if (!$file.FullName.StartsWith($modelRoot, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe staging path' }
        Add-ObsoleteFile $file.FullName
        Remove-Item -LiteralPath $file.FullName
    }
}
Copy-SourceTree (Join-Path $payload 'resources\Models\dragon1') (Join-Path $payload 'projects\Hello World\resources\Models\dragon1')

'1' | Set-Content -LiteralPath (Join-Path $payload '.scenemax-studio-installation') -Encoding ASCII
$cleanupPath = Join-Path $job 'obsolete-files.iss'
$cleanup | Set-Content -LiteralPath $cleanupPath -Encoding UTF8
foreach ($name in @('scenemax_ide.exe','scenemax_projector_nextgen.exe','scenemax_effekseer_bevy.dll')) {
    Copy-Item -LiteralPath (Join-Path $workspace "target\release\$name") -Destination $payload
}
$exportRoot = Join-Path $payload 'scenemax3d_nextgen\prebuilt'
$desktopRuntime = Join-Path $exportRoot 'x86_64-pc-windows-msvc'
New-Item -ItemType Directory -Path $desktopRuntime -Force | Out-Null
foreach ($name in @('scenemax_projector_nextgen.exe', 'scenemax_game_launcher.exe', 'scenemax_effekseer_bevy.dll')) {
    Copy-Item -LiteralPath (Join-Path $workspace "target\release\$name") -Destination $desktopRuntime
}
$webRuntime = Join-Path $exportRoot 'web'
New-Item -ItemType Directory -Path $webRuntime -Force | Out-Null
& $WasmBindgen (Join-Path $workspace 'target\wasm32-unknown-unknown\release\scenemax_projector_nextgen.wasm') --target web --no-typescript --out-name runtime --out-dir $webRuntime
if ($LASTEXITCODE -ne 0) { throw 'Browser runtime binding generation failed.' }
$native = Join-Path $payload 'native'
New-Item -ItemType Directory -Path $native | Out-Null
foreach ($license in @('LICENSE','LICENSE_RUNTIME_VULKAN')) {
    Copy-Item -LiteralPath (Join-Path $repo "third_party\Effekseer\$license") -Destination (Join-Path $native "Effekseer-$license.txt")
}
Copy-Item -LiteralPath (Join-Path $repo 'scenemax.ico') -Destination $payload
Copy-Item -LiteralPath (Join-Path $repo 'LICENSE') -Destination $payload
& rustc (Join-Path $PSScriptRoot 'studio-launcher.rs') --edition 2024 -C opt-level=z -C strip=symbols -C linker=rust-lld.exe -C link-arg=/DEBUG:NONE -o (Join-Path $payload 'SceneMaxStudio.exe')
if ($LASTEXITCODE -ne 0) { throw 'Studio installation launcher failed to build.' }
# Multiplayer exports use prebuilt servers/adapters, never installed Rust sources.
Copy-SourceTree (Join-Path $repo 'tools/multiplayer-server/bin') (Join-Path $payload 'tools/multiplayer-server/bin')
foreach ($gateway in @(
    @('windows-x64', 'target/release/scenemax_web_gateway.exe'),
    @('linux-x64', 'target/browser-gateway/x86_64-unknown-linux-musl/release/scenemax_web_gateway')
)) {
    $gatewayDir = Join-Path $payload ('tools/browser-gateway/bin/' + $gateway[0])
    New-Item -ItemType Directory -Path $gatewayDir -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $workspace $gateway[1]) -Destination $gatewayDir
}
@'
SceneMax Studio - Windows x64 distribution

Launch using the SceneMax Studio shortcut (SceneMaxStudio.exe).
Includes the IDE, Windows and Web export runtimes, standard resources,
model import tools, multiplayer server/gateway binaries, and license notices.
Rust sources, Cargo/CMake build files, compiler setup scripts, development
fixtures and J3O models are not included. No compiler is needed for normal exports.

A fresh installation opens projects\Hello World. Upgrades preserve existing
project files and project history. Each game build gets its own output folder.
Use a supported GPU with current drivers and the Vulkan loader for native effects.
Butler is optional for itch.io publishing; it is acquired separately.

Read scenemax3d_nextgen\IDE\WEB_EXPORT.md for export and preview instructions.
Engine source builds require a separate developer checkout and toolchain.
This installer is unsigned.
'@ | Set-Content -LiteralPath (Join-Path $payload 'INSTALLATION.txt') -Encoding UTF8
# Do not install the empty shells of excluded development/model directories.
$payloadRoot = [IO.Path]::GetFullPath($payload).TrimEnd('\') + '\'
foreach ($directory in Get-ChildItem -LiteralPath $payload -Recurse -Directory | Sort-Object { $_.FullName.Length } -Descending) {
    if (!$directory.FullName.StartsWith($payloadRoot, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe staging directory' }
    if (!(Get-ChildItem -LiteralPath $directory.FullName -Force | Select-Object -First 1)) { Remove-Item -LiteralPath $directory.FullName }
}
$files = @(Get-ChildItem -LiteralPath $payload -Recurse -File)
if ($files | Where-Object { $_.Extension -in @('.jar','.jks','.pfx','.j3o','.j3odata') }) { throw 'Unexpected Java or credential file in distribution.' }
& (Join-Path $PSScriptRoot 'test-studio-payload.ps1') -Payload $payload
$bytes = ($files | Measure-Object -Property Length -Sum).Sum
$output = Join-Path $job 'output'
& $Compiler "/DCleanupManifest=$cleanupPath" "/DPayload=$payload" "/DOutputDir=$output" "/DAppVersion=$AppVersion" (Join-Path $PSScriptRoot 'scenemax-studio.iss')
if ($LASTEXITCODE -ne 0) { throw 'Installer compilation failed.' }
$installer = Get-Item -LiteralPath (Join-Path $output "scenemax-studio-$AppVersion.exe")
# Publish only after compilation succeeds, keeping the isolated staging output.
$finalOutput = Join-Path $PSScriptRoot 'Output'
New-Item -ItemType Directory -Path $finalOutput -Force | Out-Null
$finalInstaller = Join-Path $finalOutput $installer.Name
Copy-Item -LiteralPath $installer.FullName -Destination $finalInstaller -Force
$installer = Get-Item -LiteralPath $finalInstaller
$report = [ordered]@{ installer=$installer.FullName; installer_bytes=$installer.Length; payload_bytes=$bytes; payload_files=$files.Count; sha256=(Get-FileHash -LiteralPath $installer.FullName -Algorithm SHA256).Hash; configuration='release'; signed=$false }
$report | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $job 'size-report.json') -Encoding UTF8
$report | ConvertTo-Json
