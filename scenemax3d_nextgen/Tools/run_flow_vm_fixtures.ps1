param(
    [int]$SecondsPerFixture = 4
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$exe = Join-Path $root "target\debug\scenemax_projector_nextgen.exe"
$project = Join-Path $root "Tests/fixtures/projector"
$runtimeLog = Join-Path $project "scenemax-nextgen-runtime.log"
$logDir = Join-Path $project "logs"

$fixtures = @(
    "flow_control_logger.code",
    "expression_vm_logger.code",
    "when_recurring_logger.code",
    "vm_if_guard_logger.code",
    "vm_expression_matrix_logger.code",
    "vm_when_after_logger.code",
    "vm_wait_async_logger.code",
    "vm_recurring_guard_logger.code"
)

New-Item -ItemType Directory -Path $logDir -Force | Out-Null

foreach ($fixture in $fixtures) {

    Remove-Item -LiteralPath $runtimeLog -ErrorAction SilentlyContinue

    $script = Join-Path (Join-Path $project "scripts") $fixture
    $arguments = @(
        "run",
        "--project-root", $project,
        "--script", $script,
        "--width", "640",
        "--height", "360"
    )

    $process = Start-Process -FilePath $exe -ArgumentList $arguments -PassThru -WindowStyle Hidden
    Start-Sleep -Seconds $SecondsPerFixture

    if (-not $process.HasExited) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    }

    Start-Sleep -Milliseconds 250

    $targetLog = Join-Path $logDir ($fixture -replace "\.code$", ".log")
    if (Test-Path $runtimeLog) {
        Copy-Item -LiteralPath $runtimeLog -Destination $targetLog -Force
    } else {
        Set-Content -LiteralPath $targetLog -Value "[ERROR] no runtime log was produced"
    }

    $lines = @(Get-Content -LiteralPath $targetLog)
    $passCount = @($lines | Where-Object { $_ -match "PASS:" }).Count
    $failCount = @($lines | Where-Object { $_ -match "FAIL:" }).Count
    $errorCount = @($lines | Where-Object { $_ -match "^\[ERROR\]" -and $_ -notmatch "FAIL:" }).Count

    [pscustomobject]@{
        Fixture = $fixture
        Lines = $lines.Count
        Pass = $passCount
        Fail = $failCount
        Errors = $errorCount
        Log = $targetLog
    }
}

