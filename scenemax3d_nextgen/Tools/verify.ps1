param([switch]$Build)
$ErrorActionPreference = 'Stop'
$workspacePath = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
function Invoke-Checked([string]$Command, [string[]]$Arguments) {
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Command failed with exit code $LASTEXITCODE" }
}
Push-Location $workspacePath
try {
    Invoke-Checked python @('Tools/check_architecture.py', '--self-test')
    Invoke-Checked python @('Tools/check_architecture.py')
    Invoke-Checked python @('Tools/check_java_menu_parity.py')
    Invoke-Checked python @('Tools/check_java_syntax_parity.py')
    Invoke-Checked python @('Tools/check_java_completion_parity.py')
    $idePackages = @('-p', 'scenemax_ide', '-p', 'scenemax_ide_core', '-p', 'scenemax_ide_services', '-p', 'scenemax_ide_ui')
    Invoke-Checked cargo (@('fmt') + $idePackages + @('--check'))
    Invoke-Checked cargo (@('clippy', '--locked', '--no-deps') + $idePackages + @('--all-targets', '--', '-D', 'warnings'))
    Invoke-Checked cargo @('check', '--locked', '--workspace', '--all-targets')
    Invoke-Checked cargo (@('test', '--locked') + $idePackages + @('--lib'))
    # Separate products avoid unifying the IDE's UI-only Bevy features with 3D.
    Invoke-Checked cargo @('test', '--locked', '--workspace', '--exclude', 'scenemax_ide', '--exclude', 'scenemax_ide_ui', '--lib')
    if ($Build) {
        Invoke-Checked cargo @('build', '--locked', '-p', 'scenemax_ide')
        Invoke-Checked cargo @('build', '--locked', '-p', 'scenemax_projector_nextgen')
    }
} finally { Pop-Location }
