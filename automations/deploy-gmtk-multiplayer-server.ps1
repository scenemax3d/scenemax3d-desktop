param(
    [string]$ProjectName = "GMTK 2026",
    [string]$RemoteHost = "178.128.144.17",
    [string]$RemoteUser = "root",
    [string]$PuttySession = "mamreem",
    [string]$PuttyKeyPath = "C:\dev\certificates\putty_private_key_scenemax_server.ppk",
    [string]$RemoteDir = "/opt/scenemax-mp-server",
    [string]$ServiceName = "scenemax-mp-server",
    [switch]$OnlyIfChanged
)

$ErrorActionPreference = "Stop"

function Resolve-Tool {
    param(
        [string]$Name,
        [string]$FallbackPath = ""
    )

    $tool = Get-Command $Name -ErrorAction SilentlyContinue
    if ($tool) {
        return $tool.Source
    }
    if ($FallbackPath -and (Test-Path -LiteralPath $FallbackPath)) {
        return $FallbackPath
    }
    throw "Required tool was not found: $Name"
}

function Find-Bytes {
    param(
        [byte[]]$Haystack,
        [byte[]]$Needle,
        [int]$Start
    )

    for ($i = $Start; $i -le $Haystack.Length - $Needle.Length; $i++) {
        $matched = $true
        for ($j = 0; $j -lt $Needle.Length; $j++) {
            if ($Haystack[$i + $j] -ne $Needle[$j]) {
                $matched = $false
                break
            }
        }
        if ($matched) {
            return $i
        }
    }
    return -1
}

function Write-FixedString {
    param(
        [byte[]]$Buffer,
        [ref]$Offset,
        [string]$Value,
        [int]$Length
    )

    $encoded = [System.Text.Encoding]::UTF8.GetBytes($(if ($null -eq $Value) { "" } else { $Value }))
    $count = [Math]::Min($encoded.Length, $Length - 1)
    [Array]::Copy($encoded, 0, $Buffer, $Offset.Value, $count)
    $Offset.Value += $Length
}

function Patch-SceneMaxConfig {
    param(
        [string]$ExecutablePath,
        [object]$Project
    )

    $beginMarker = [System.Text.Encoding]::ASCII.GetBytes("SCENEMAX_MP_CONFIG_BEGIN")
    $endMarker = [System.Text.Encoding]::ASCII.GetBytes("SCENEMAX_MP_CONFIG_END")
    $bytes = [System.IO.File]::ReadAllBytes($ExecutablePath)
    $begin = Find-Bytes $bytes $beginMarker 0
    if ($begin -lt 0) {
        throw "The multiplayer server executable does not contain a SceneMax config block."
    }

    $payloadStart = $begin + $beginMarker.Length
    $end = Find-Bytes $bytes $endMarker $payloadStart
    if ($end -lt 0 -or ($end - $payloadStart) -lt 4096) {
        throw "The multiplayer server config block is too small or malformed."
    }

    $port = 9001
    if ($Project.multiplayerServerPort -and [int]$Project.multiplayerServerPort -gt 0) {
        $port = [int]$Project.multiplayerServerPort
    }

    $payload = New-Object byte[] 4096
    $offset = 0
    [Array]::Copy([System.Text.Encoding]::ASCII.GetBytes("SMXMPCFG"), 0, $payload, $offset, 8)
    $offset += 8
    [Array]::Copy([BitConverter]::GetBytes([UInt16]3), 0, $payload, $offset, 2)
    $offset += 2
    [Array]::Copy([BitConverter]::GetBytes([UInt16]0), 0, $payload, $offset, 2)
    $offset += 2
    [Array]::Copy([BitConverter]::GetBytes([UInt32]$port), 0, $payload, $offset, 4)
    $offset += 4
    Write-FixedString $payload ([ref]$offset) $Project.name 128
    Write-FixedString $payload ([ref]$offset) $Project.path 256

    if ($Project.multiplayerPassword) {
        $hash = [System.Security.Cryptography.SHA256]::Create().ComputeHash(
            [System.Text.Encoding]::UTF8.GetBytes([string]$Project.multiplayerPassword)
        )
        [Array]::Copy($hash, 0, $payload, $offset, 32)
    }
    $offset += 32

    Write-FixedString $payload ([ref]$offset) $Project.projectGuid 64

    [Array]::Clear($bytes, $payloadStart, $end - $payloadStart)
    [Array]::Copy($payload, 0, $bytes, $payloadStart, $payload.Length)
    [System.IO.File]::WriteAllBytes($ExecutablePath, $bytes)
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$zig = Resolve-Tool "zig"
$plink = Resolve-Tool "plink.exe" "C:\Program Files\PuTTY\plink.exe"
$pscp = Resolve-Tool "pscp.exe" "C:\Program Files\PuTTY\pscp.exe"

if (-not (Test-Path -LiteralPath $PuttyKeyPath)) {
    throw "PuTTY key was not found: $PuttyKeyPath"
}

Set-Location $repoRoot

$projectsJsonPath = Join-Path $repoRoot "projects\projects.json"
$projectsConfig = Get-Content -LiteralPath $projectsJsonPath -Raw | ConvertFrom-Json
$project = $projectsConfig.projects | Where-Object { $_.name -eq $ProjectName } | Select-Object -First 1
if (-not $project) {
    throw "Project was not found in projects/projects.json: $ProjectName"
}
if (-not $project.projectGuid) {
    throw "Project is missing projectGuid: $ProjectName"
}

$buildFile = Join-Path $repoRoot "tools\multiplayer-server\build.zig"
$linuxTemplate = Join-Path $repoRoot "tools\multiplayer-server\bin\linux-x64\scenemax-mp-server"
$projectOutputDir = Join-Path $repoRoot "projects\GMTK_2026\multiplayer_server"
$projectOutput = Join-Path $projectOutputDir "scenemax-mp-server"

Write-Host "Building Linux multiplayer server..."
& $zig build --build-file $buildFile linux --summary all
if ($LASTEXITCODE -ne 0) {
    throw "Zig build failed with exit code $LASTEXITCODE"
}

New-Item -ItemType Directory -Force -Path $projectOutputDir | Out-Null
Copy-Item -LiteralPath $linuxTemplate -Destination $projectOutput -Force
Patch-SceneMaxConfig -ExecutablePath $projectOutput -Project $project

$localHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $projectOutput).Hash.ToLowerInvariant()
Write-Host "Prepared patched binary: $projectOutput"
Write-Host "Local SHA-256: $localHash"

if ($OnlyIfChanged) {
    Write-Host "Checking currently deployed binary..."
    $remoteHashCommand = "if [ -f $RemoteDir/scenemax-mp-server ]; then sha256sum $RemoteDir/scenemax-mp-server | awk '{print `$1}'; fi"
    $remoteHashOutput = & $plink -batch -load $PuttySession $remoteHashCommand 2>&1
    $remoteHashExit = $LASTEXITCODE
    if ($remoteHashExit -ne 0) {
        throw "Remote hash check failed with exit code $remoteHashExit`n$remoteHashOutput"
    }
    $remoteHash = [string]($remoteHashOutput | Select-Object -First 1)
    $remoteHash = $remoteHash.Trim().ToLowerInvariant()
    if ($remoteHash) {
        Write-Host "Remote SHA-256: $remoteHash"
    } else {
        Write-Host "Remote SHA-256: no deployed binary found"
    }
    if ($remoteHash -eq $localHash) {
        Write-Host "No Zig server changes detected. Skipping upload and restart."
        exit 0
    }
}

$remoteTarget = "$RemoteUser@$RemoteHost`:$RemoteDir/scenemax-mp-server.new"
Write-Host "Uploading to $remoteTarget..."
& $pscp -batch -i $PuttyKeyPath $projectOutput $remoteTarget
if ($LASTEXITCODE -ne 0) {
    throw "Upload failed with exit code $LASTEXITCODE"
}

$backupStamp = Get-Date -Format "yyyyMMddHHmmss"
$remoteCommand = "set -e; cd $RemoteDir; chmod 0755 scenemax-mp-server.new; chown root:root scenemax-mp-server.new; remote_hash=`$(sha256sum scenemax-mp-server.new | awk '{print `$1}'); echo Remote SHA-256: `$remote_hash; test `$remote_hash = $localHash; if [ -f scenemax-mp-server ]; then cp -a scenemax-mp-server scenemax-mp-server.bak.$backupStamp; fi; mv -f scenemax-mp-server.new scenemax-mp-server; systemctl restart $ServiceName; sleep 1; systemctl --no-pager --full status $ServiceName; ss -lunp | grep -E ':9001\b|scenemax' || true; journalctl -u $ServiceName -n 8 --no-pager"

Write-Host "Installing and restarting $ServiceName..."
& $plink -batch -load $PuttySession $remoteCommand
if ($LASTEXITCODE -ne 0) {
    throw "Remote install failed with exit code $LASTEXITCODE"
}

Write-Host "Done. $ServiceName is redeployed."
