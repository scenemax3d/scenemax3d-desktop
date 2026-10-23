[CmdletBinding()]
param([string]$InstallDir = $PSScriptRoot, [switch]$CheckOnly)

$ErrorActionPreference = 'Stop'

function Invoke-StudioDependencyPlan {
    param([scriptblock]$Run, [switch]$CheckOnly)
    $report = & $Run 'Scan'
    foreach ($stage in @(
        @{ Operation='Rust'; Indices=@(0) },
        @{ Operation='Cpp'; Indices=@(1,2,3) },
        @{ Operation='Web'; Indices=@(4,5) }
    )) {
        if (@($report.packages).Count -ne 6) { throw 'Incomplete build-tool detection report; setup cannot confirm readiness.' }
        $missing = @($stage.Indices | Where-Object { !$report.packages[$_].installed })
        if ($missing.Count -and !$CheckOnly) {
            $report = & $Run $stage.Operation
            if ($report.restart_required) { return @{ Code=3010; Report=$report } }
            if (@($report.packages).Count -ne 6) { throw 'Installer returned an incomplete build-tool report.' }
            if (@($stage.Indices | Where-Object { !$report.packages[$_].installed }).Count) {
                throw "$($stage.Operation) setup finished but required tools are still missing. See the setup log."
            }
        }
    }
    $report = & $Run 'Scan'
    if (@($report.packages).Count -ne 6) { throw 'Final build-tool detection report is incomplete.' }
    $missing = @($report.packages | Where-Object { !$_.installed })
    if ($missing.Count) { throw ('Missing prerequisites: ' + (($missing | ForEach-Object { $_.name }) -join ', ')) }
    return @{ Code=0; Report=$report }
}

function Save-StudioBuildReport {
    param($Report, [string]$Path)
    $temporary = "$Path.$([guid]::NewGuid().ToString('N')).tmp"
    # Rust's JSON reader expects UTF-8 without a BOM. Replace atomically.
    [IO.File]::WriteAllText($temporary, ($Report | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
    if (Test-Path -LiteralPath $Path) { [IO.File]::Replace($temporary, $Path, [NullString]::Value) }
    else { [IO.File]::Move($temporary, $Path) }
}

function Install-StudioDependencies {
    param([string]$Root, [switch]$CheckOnly)
    $state = Join-Path $env:LOCALAPPDATA 'SceneMax'
    $logs = Join-Path $state 'setup'
    New-Item -ItemType Directory -Path $logs -Force | Out-Null
    $log = Join-Path $logs 'dependencies.log'
    $lock = $null
    try {
        $lock = [IO.File]::Open((Join-Path $logs 'dependencies.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
        "`r`n=== SceneMax dependencies $(Get-Date -Format o) ===" | Add-Content -LiteralPath $log
        $workspace = Join-Path $Root 'scenemax3d_nextgen'
        $toolchain = [regex]::Match((Get-Content -LiteralPath (Join-Path $workspace 'rust-toolchain.toml') -Raw), 'channel\s*=\s*"([^"]+)"').Groups[1].Value
        $bindgen = [regex]::Match((Get-Content -LiteralPath (Join-Path $workspace 'IDE/services/src/build_configuration.rs') -Raw), 'WASM_BINDGEN_VERSION:\s*&str\s*=\s*"([^"]+)"').Groups[1].Value
        if ($toolchain -notmatch '^\d+\.\d+\.\d+$' -or $bindgen -notmatch '^\d+\.\d+\.\d+$') { throw 'Cannot determine the tool versions required by this installation.' }
        $job = Join-Path $logs ([guid]::NewGuid().ToString('N'))
        New-Item -ItemType Directory -Path $job | Out-Null
        $worker = Join-Path $job 'build-tools.ps1'
        Copy-Item -LiteralPath (Join-Path $workspace 'IDE/services/src/build_configuration.ps1') -Destination $worker
        $run = {
            param($operation)
            "Stage: $operation" | Add-Content -LiteralPath $log
            Write-Host "SceneMax build tools: $operation (log: $log)"
            $found = $null
            $restart = $false
            & "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $worker -Operation $operation -Toolchain $toolchain -BindgenVersion $bindgen 2>&1 | ForEach-Object {
                $line = "$_"
                $line | Add-Content -LiteralPath $log
                if ($line.StartsWith('SCENEMAX_STATUS ')) { Write-Host $line.Substring(16) }
                elseif (!$line.StartsWith('SCENEMAX_')) { Write-Host $line }
                if ($line.StartsWith('SCENEMAX_RESULT ')) { $found = $line.Substring(16) | ConvertFrom-Json }
                if ($line -match 'A Windows restart is required|Setup complete\. Restart Windows') { $restart = $true }
            }
            if ($LASTEXITCODE -ne 0) { throw "$operation setup failed (exit $LASTEXITCODE). Check $log and retry." }
            if (!$found -or @($found.packages).Count -ne 6) { throw "$operation did not return a complete detection report." }
            $found | Add-Member -NotePropertyName restart_required -NotePropertyValue $restart -Force
            Save-StudioBuildReport $found (Join-Path $state 'build-tools.json')
            # Make newly detected compiler/SDK paths available to the next child.
            foreach ($property in $found.environment.PSObject.Properties) {
                [Environment]::SetEnvironmentVariable($property.Name, [string]$property.Value, 'Process')
            }
            return $found
        }
        $result = Invoke-StudioDependencyPlan -Run $run -CheckOnly:$CheckOnly
        if ($result.Code -eq 3010) {
            'Restart Windows, then rerun dependency setup from the Start menu. No restart was initiated.' | Add-Content -LiteralPath $log
            Write-Host 'Restart Windows, then rerun dependency setup from the Start menu. No restart was initiated.'
            return 3010
        }
        foreach ($file in @('native/scenemax_effekseer_bevy.dll', 'native/scenemax_effekseer_bevy.lib', 'scenemax3d_nextgen/Projector/app/src/web.rs', 'scenemax3d_nextgen/third_party/wgpu/Cargo.toml')) {
            if (!(Test-Path -LiteralPath (Join-Path $Root $file) -PathType Leaf)) { throw "Missing bundled dependency: $file. Repair the Studio installation." }
        }
        if (!('SceneMax.Setup.NativeProbe' -as [type])) {
            Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
namespace SceneMax.Setup {
    public static class NativeProbe {
        [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)]
        public static extern IntPtr LoadLibraryEx(string path, IntPtr file, uint flags);
        [DllImport("kernel32.dll")]
        public static extern bool FreeLibrary(IntPtr module);
    }
}
'@
        }
        $handle = [SceneMax.Setup.NativeProbe]::LoadLibraryEx((Join-Path $Root 'native/scenemax_effekseer_bevy.dll'), [IntPtr]::Zero, 0x1100)
        if ($handle -eq [IntPtr]::Zero) {
            $code = [Runtime.InteropServices.Marshal]::GetLastWin32Error()
            throw "Native Effekseer could not load (Windows error $code). Install/update your GPU manufacturer's Vulkan-capable driver, then retry. The Vulkan SDK does not install a GPU driver."
        }
        [void][SceneMax.Setup.NativeProbe]::FreeLibrary($handle)
        'Build tools installed and detected; bundled native effects library loads. Test rendering on this GPU. First game builds may download Cargo dependencies.' | Add-Content -LiteralPath $log
        Write-Host 'Setup complete. Build tools are detected and the native effects library loads. First game builds may download Cargo dependencies.'
        return 0
    } catch {
        "FAILED: $($_.Exception.Message)" | Add-Content -LiteralPath $log
        Write-Warning "Dependency setup incomplete. $($_.Exception.Message) Log: $log"
        return 1
    } finally {
        if ($lock) { $lock.Dispose() }
    }
}

if ($MyInvocation.InvocationName -ne '.') {
    exit (Install-StudioDependencies -Root $InstallDir -CheckOnly:$CheckOnly)
}
