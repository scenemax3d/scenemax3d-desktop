param(
    [string]$ProjectRoot,
    [string]$Script,
    [string]$Projector
)
$ErrorActionPreference = 'Stop'
$runArgs = @('run', '-p', 'scenemax_ide', '--')
if ($ProjectRoot) { $runArgs += @('--project-root', (Resolve-Path -LiteralPath $ProjectRoot).Path) }
$catalogPath = Join-Path $PSScriptRoot 'projects/projects.json'
if (Test-Path -LiteralPath $catalogPath) { $runArgs += @('--project-catalog', $catalogPath) }
if ($Script) { $runArgs += @('--script', $Script) }
if ($Projector) { $runArgs += @('--projector', (Resolve-Path -LiteralPath $Projector).Path) }
Push-Location (Join-Path $PSScriptRoot 'scenemax3d_nextgen')
try {
    & cargo @runArgs
    if ($LASTEXITCODE -ne 0) { throw "Rust IDE exited with code $LASTEXITCODE" }
} finally { Pop-Location }
