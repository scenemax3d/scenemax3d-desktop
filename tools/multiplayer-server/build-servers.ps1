param(
    [string]$ZigPath = "zig"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$source = Join-Path $root "multiplayer-server\zig\scenemax_multiplayer_server.zig"
$targets = @(
    @{ ZigTarget = "x86_64-windows"; Output = "windows-x64\scenemax-mp-server.exe" },
    @{ ZigTarget = "x86_64-linux"; Output = "linux-x64\scenemax-mp-server" },
    @{ ZigTarget = "x86_64-macos"; Output = "macos-x64\scenemax-mp-server" }
)

foreach ($target in $targets) {
    $output = Join-Path (Join-Path $root "multiplayer-server\bin") $target.Output
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $output) | Out-Null
    & $ZigPath build-exe -O ReleaseFast -target $target.ZigTarget "-femit-bin=$output" $source
    if ($LASTEXITCODE -ne 0) {
        throw "Zig build failed for $($target.ZigTarget)"
    }
    $pdb = [System.IO.Path]::ChangeExtension($output, ".pdb")
    if (Test-Path $pdb) {
        Remove-Item -LiteralPath $pdb -Force
    }
}
