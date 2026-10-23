[CmdletBinding()]
param(
    [string]$AppVersion = '3.0',
    [string]$Compiler = 'C:\Program Files (x86)\Inno Setup 6\ISCC.exe',
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$workspace = Join-Path $repo 'scenemax3d_nextgen'
if (!(Test-Path -LiteralPath $Compiler)) { throw "Install Inno Setup or supply -Compiler: $Compiler" }
if ($AppVersion -notmatch '^\d+\.\d+(\.\d+){0,2}$') { throw 'Use a numeric installer version such as 3.0.' }
if (!$SkipBuild) {
    Push-Location $workspace
    try {
        & cargo build --locked --release -p scenemax_ide -p scenemax_projector_nextgen --features scenemax_projector_nextgen/effekseer_native
        if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
    } finally { Pop-Location }
}
# Always use a new staging directory; never delete or overwrite project data.
$job = Join-Path $workspace ('target\studio-installer\' + [guid]::NewGuid().ToString('N'))
$payload = Join-Path $job 'payload'
New-Item -ItemType Directory -Path $payload -Force | Out-Null
$cleanup = [Collections.Generic.List[string]]::new()
function Add-ObsoleteFile([string]$Destination) {
    $relative = $Destination.Substring($payload.Length + 1)
    if ($relative.Contains('"') -or $relative.Contains('..')) { throw "Invalid cleanup path: $relative" }
    $cleanup.Add('Type: files; Name: "{app}\' + $relative + '"')
}
function Copy-SourceTree([string]$Source, [string]$Destination) {
    New-Item -ItemType Directory -Path $Destination -Force | Out-Null
    foreach ($item in Get-ChildItem -LiteralPath $Source -Force) {
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Source links must be reviewed before packaging: $($item.FullName)" }
        if ($item.PSIsContainer) {
            if ($item.Name -in @('user-state', 'target', '.git', 'node_modules', '__pycache__', 'artifacts', '.scenemax-studio')) { continue }
            if ($item.Name -ceq 'Tests' -or $item.Name -eq 'fixtures') {
                foreach ($obsolete in Get-ChildItem -LiteralPath $item.FullName -Recurse -File) {
                    Add-ObsoleteFile (Join-Path $Destination ($obsolete.FullName.Substring($Source.Length + 1)))
                }
                continue
            }
            Copy-SourceTree $item.FullName (Join-Path $Destination $item.Name)
        } elseif ($item.Extension -in @('.j3o', '.j3odata')) {
            Add-ObsoleteFile (Join-Path $Destination $item.Name)
        } elseif ($item.Extension -notin @('.pdb', '.log', '.jar', '.jks', '.pfx') -and $item.Name -ne '.DS_Store') {
            Copy-Item -LiteralPath $item.FullName -Destination $Destination
        }
    }
}
Copy-SourceTree $workspace (Join-Path $payload 'scenemax3d_nextgen')
Copy-SourceTree (Join-Path $repo 'resources-basic\resources') (Join-Path $payload 'resources')
Copy-SourceTree (Join-Path $PSScriptRoot 'starter-projects') (Join-Path $payload 'projects')
Copy-SourceTree (Join-Path $payload 'resources\Models\dragon1') (Join-Path $payload 'projects\Hello World\resources\Models\dragon1')

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
'1' | Set-Content -LiteralPath (Join-Path $payload '.scenemax-studio-installation') -Encoding ASCII
$cleanupPath = Join-Path $job 'obsolete-files.iss'
$cleanup | Set-Content -LiteralPath $cleanupPath -Encoding UTF8
foreach ($name in @('scenemax_ide.exe','scenemax_projector_nextgen.exe','scenemax_effekseer_bevy.dll')) {
    Copy-Item -LiteralPath (Join-Path $workspace "target\release\$name") -Destination $payload
}
$native = Join-Path $payload 'native'
New-Item -ItemType Directory -Path $native | Out-Null
Copy-Item -LiteralPath (Join-Path $payload 'scenemax_effekseer_bevy.dll') -Destination $native
Copy-Item -LiteralPath (Join-Path $repo 'tmp\eb\release\i\lib\scenemax_effekseer_bevy.lib') -Destination $native
foreach ($license in @('LICENSE','LICENSE_RUNTIME_VULKAN')) {
    Copy-Item -LiteralPath (Join-Path $repo "third_party\Effekseer\$license") -Destination (Join-Path $native "Effekseer-$license.txt")
}
Copy-Item -LiteralPath (Join-Path $repo 'scenemax.ico') -Destination $payload
Copy-Item -LiteralPath (Join-Path $repo 'LICENSE') -Destination $payload
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'setup-studio-dependencies.ps1') -Destination $payload
& rustc (Join-Path $PSScriptRoot 'studio-launcher.rs') --edition 2024 -C opt-level=z -C strip=symbols -C linker=rust-lld.exe -C link-arg=/DEBUG:NONE -o (Join-Path $payload 'SceneMaxStudio.exe')
if ($LASTEXITCODE -ne 0) { throw 'Studio installation launcher failed to build.' }
@'
SceneMax Studio - Windows x64 distribution

Launch using the SceneMax Studio shortcut (SceneMaxStudio.exe). It configures
installation-relative source, resource and native tool paths for this process.
The IDE, desktop runtime, standard resources, import tools, Web effect binaries,
source workspace and documentation are included. No user game projects, test fixtures or J3O models are included.
Each installed copy keeps its own project history in user-state next to the IDE.
A fresh installation opens projects\Hello World, with scripts\main and resources.
The starter project includes a glTF dragon and its named fly animation.
Upgrades preserve existing starter files and the last project you opened.
Existing glTF/GLB models are included. Legacy-only model entries are omitted.
Use a supported GPU with current drivers, including the Vulkan loader used by
the bundled native effects library.

Select "Download and install build tools" during installation (recommended).
Setup detects installed tools, downloads missing components, and saves their paths.
To retry later, use "Set up SceneMax build tools" in the Start menu, or open
File > Settings > Build Configuration and install missing components in order:
1. Install / repair Rust.
2. Install C++ / SDK / CMake.
3. Install Web tools for browser builds, then Refresh detection.
These tools and Cargo dependencies require internet and additional disk space.
Setup never forces a restart. If Microsoft Setup requires one, restart Windows
and rerun dependency setup to finish. Logs: %LOCALAPPDATA%\SceneMax\setup\dependencies.log
Butler is optional and downloaded separately for
automatic itch.io publishing. No Java or separate Effekseer SDK is required.

Read scenemax3d_nextgen\IDE\WEB_EXPORT.md for full setup and publishing details.
This installer is unsigned and has not been certified on a clean machine.
Native effect rebuilding uses the bundled prebuilt bridge; rebuilding that bridge
itself requires a separate complete upstream Effekseer/Vulkan source checkout.
'@ | Set-Content -LiteralPath (Join-Path $payload 'INSTALLATION.txt') -Encoding UTF8
$files = @(Get-ChildItem -LiteralPath $payload -Recurse -File)
if ($files | Where-Object { $_.Extension -in @('.jar','.jks','.pfx','.j3o','.j3odata') }) { throw 'Unexpected Java or credential file in distribution.' }
& (Join-Path $PSScriptRoot 'test-studio-payload.ps1') -Payload $payload
$bytes = ($files | Measure-Object -Property Length -Sum).Sum
$output = Join-Path $job 'output'
& $Compiler "/DCleanupManifest=$cleanupPath" "/DPayload=$payload" "/DOutputDir=$output" "/DAppVersion=$AppVersion" (Join-Path $PSScriptRoot 'scenemax-studio.iss')
if ($LASTEXITCODE -ne 0) { throw 'Installer compilation failed.' }
$installer = Get-Item -LiteralPath (Join-Path $output "scenemax-studio-$AppVersion.exe")
$report = [ordered]@{ installer=$installer.FullName; installer_bytes=$installer.Length; payload_bytes=$bytes; payload_files=$files.Count; sha256=(Get-FileHash -LiteralPath $installer.FullName -Algorithm SHA256).Hash; configuration='release'; signed=$false }
$report | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $job 'size-report.json') -Encoding UTF8
$report | ConvertTo-Json
