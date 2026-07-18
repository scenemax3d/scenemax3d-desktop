param(
    [string]$ZigPath = "zig"
)

$ErrorActionPreference = "Stop"
$zigVersion = (& $ZigPath version).Trim()
if ($LASTEXITCODE -ne 0) {
    throw "Could not run Zig at '$ZigPath'. Install Zig 0.16.0 or pass -ZigPath <path-to-zig>."
}

$majorMinor = ($zigVersion -split '\.')[0..1] -join '.'
if ($majorMinor -ne "0.16") {
    throw "SceneMax multiplayer server requires Zig 0.16.x. Found Zig $zigVersion at '$ZigPath'."
}

& $ZigPath build --build-file (Join-Path $PSScriptRoot "build.zig") --summary all
if ($LASTEXITCODE -ne 0) {
    throw "Zig multiplayer server build failed."
}
