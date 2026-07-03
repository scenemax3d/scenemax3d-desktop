param(
    [string]$ZigPath = "zig"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$source = Join-Path $root "native-launcher\zig\scenemax_launcher.zig"

$targets = @(
    @{ ZigTarget = "x86_64-windows"; Output = "windows-x64\scenemax-selfextract.exe" },
    @{ ZigTarget = "x86_64-linux"; Output = "linux-x64\scenemax-selfextract" },
    @{ ZigTarget = "x86_64-macos"; Output = "macos-x64\scenemax-selfextract" }
)

foreach ($target in $targets) {
    $output = Join-Path (Join-Path $root "native-launcher\bin") $target.Output
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $output) | Out-Null
    $args = @("build-exe", "-O", "ReleaseSmall", "-target", $target.ZigTarget, "-femit-bin=$output")
    if ($target.ZigTarget -eq "x86_64-windows") {
        $args += @("--subsystem", "windows")
    }
    $args += $source
    & $ZigPath @args
    Write-Host "Built $output"
}
