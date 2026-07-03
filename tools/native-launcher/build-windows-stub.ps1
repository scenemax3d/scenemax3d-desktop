param(
    [string]$ZigPath = "zig"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$source = Join-Path $root "native-launcher\zig\scenemax_launcher.zig"
$outputDir = Join-Path $root "native-launcher\bin\windows-x64"
$output = Join-Path $outputDir "scenemax-selfextract.exe"

New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
& $ZigPath build-exe -O ReleaseSmall -target x86_64-windows --subsystem windows "-femit-bin=$output" $source

Write-Host "Built $output"
