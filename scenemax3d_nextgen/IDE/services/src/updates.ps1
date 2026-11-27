param(
    [ValidateSet('Check','Apply')][string]$Operation = 'Check',
    [Parameter(Mandatory)][string]$Root,
    [Parameter(Mandatory)][string]$Cache,
    [int]$ParentProcess = 0,
    [string]$Project = '',
    [switch]$Force
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

function Read-Json($Path) {
    if (Test-Path -LiteralPath $Path) {
        try { Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json }
        catch { return $null }
    }
}
function Write-Json($Path, $Value) {
    $temp = "$Path.tmp"
    $Value | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $temp -Encoding UTF8
    Move-Item -LiteralPath $temp -Destination $Path -Force
}
function Release-Version([string]$Tag) {
    $tag = $Tag.Trim()
    if (!$tag) { throw 'Missing release tag' }
    # Packaging historically omits the conventional numeric tag prefix.
    return ($tag -creplace '^[vV](?=\d)', '')
}
function Emit($Value) {
    [Console]::WriteLine(($Value | ConvertTo-Json -Depth 8 -Compress))
}
function File-Sha256([string]$Path) {
    $stream = [IO.File]::OpenRead($Path)
    $hash = [Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($hash.ComputeHash($stream)).Replace('-', '').ToLowerInvariant() }
    finally { $hash.Dispose(); $stream.Dispose() }
}
function Test-Package($Ready) {
    if (!$Ready -or $Ready.sha256 -notmatch '^[a-fA-F0-9]{64}$' -or $Ready.size -le 0) { return $false }
    $file = Join-Path $Cache 'installer.exe'
    if (!(Test-Path -LiteralPath $file)) { return $false }
    if ((Get-Item -LiteralPath $file).Length -ne $Ready.size) { return $false }
    return (File-Sha256 $file) -eq $Ready.sha256
}

New-Item -ItemType Directory -Path $Cache -Force | Out-Null
# Hold across discovery/download/apply, including separate IDE processes.
try { $lock = [IO.File]::Open((Join-Path $Cache 'lock'), 'OpenOrCreate', 'ReadWrite', 'None') }
catch {
    if ($Operation -eq 'Apply') {
        Add-Type -AssemblyName PresentationFramework
        [Windows.MessageBox]::Show('Another SceneMax update is in progress. Please reopen Studio and try again.', 'SceneMax update') | Out-Null
        exit 1
    }
    if ($Force) { Emit @{ status = 'busy'; version = ''; notes = 'Another Studio window is checking or downloading an update. Try again shortly.' } }
    exit 0
}
try {
    $readyPath = Join-Path $Cache 'ready.json'
    $ready = Read-Json $readyPath
    if ($Operation -eq 'Apply') {
        if ($ParentProcess -le 0) { throw 'Missing IDE process' }
        Wait-Process -Id $ParentProcess -ErrorAction SilentlyContinue
        if (!(Test-Package $ready)) { throw 'Update verification failed' }
        if (!(Test-Path -LiteralPath (Join-Path $Root '.scenemax-studio-installation'))) { throw 'Not an installed Studio directory' }
        $running = @(Get-Process -Name scenemax_ide,scenemax_projector_nextgen -ErrorAction SilentlyContinue | Where-Object { $_.Path -and (Split-Path -Parent $_.Path) -eq $Root })
        if ($running.Count -gt 0) { throw 'Close other Studio windows and running previews before installing the update' }
        $log = Join-Path $Cache 'install.log'
        $arguments = '/SILENT /SUPPRESSMSGBOXES /NORESTART /SP- /UPDATE /DIR="' + $Root + '" /LOG="' + $log + '"'
        $installer = Start-Process -FilePath (Join-Path $Cache 'installer.exe') -ArgumentList $arguments -PassThru
        $installer.WaitForExit()
        if ($installer.ExitCode -ne 0) { throw "Installer failed ($($installer.ExitCode)); see $log" }
        # The release tag is authoritative even when the installer embeds another version.
        $ready.version | Set-Content -LiteralPath (Join-Path $Root 'studio-release-tag.txt') -Encoding UTF8
        Remove-Item -LiteralPath $readyPath -Force
        Remove-Item -LiteralPath (Join-Path $Cache 'installer.exe') -Force
        $launchArgs = '--project-root "' + $Project + '"'
        Start-Process -FilePath (Join-Path $Root 'SceneMaxStudio.exe') -WorkingDirectory $Root -ArgumentList $launchArgs -WindowStyle Hidden
        exit 0
    }
    $installedTag = Join-Path $Root 'studio-release-tag.txt'
    # Older installations have only the product-version file.
    if (!(Test-Path -LiteralPath $installedTag)) { $installedTag = Join-Path $Root 'studio-version.txt' }
    $current = Release-Version ((Get-Content -LiteralPath $installedTag -Raw).Trim())
    if ($ready -and (Release-Version $ready.version) -ceq $current) {
        Remove-Item -LiteralPath $readyPath -Force
        $oldInstaller = Join-Path $Cache 'installer.exe'
        if (Test-Path -LiteralPath $oldInstaller) { Remove-Item -LiteralPath $oldInstaller -Force }
        $ready = $null
    }
    $cached = $ready -and (Release-Version $ready.version) -cne $current -and (Test-Package $ready)
    $statePath = Join-Path $Cache 'checked.json'
    $state = Read-Json $statePath
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    if (!$Force -and $state -and $state.time -le $now -and ($now - $state.time) -lt 86400) {
        if ($cached) { Emit $ready }
        exit 0
    }
    # Persist attempts, so offline launches and rate limits cannot cause a request loop.
    Write-Json $statePath @{ time = $now }
    $release = Invoke-RestMethod -Uri 'https://api.github.com/repos/scenemax3d/scenemax3d-desktop/releases/latest' -Headers @{ 'User-Agent'='SceneMax-Studio-Updater'; 'Accept'='application/vnd.github+json'; 'X-GitHub-Api-Version'='2022-11-28' } -TimeoutSec 60
    if ($release.draft -or $release.prerelease) { throw 'No stable release is available' }
    $version = [string]$release.tag_name
    $normalized = Release-Version $version
    if ($normalized -ceq $current) {
        Emit @{ status = 'current'; version = $version; notes = [string]$release.body }
        exit 0
    }
    if ($cached -and (Release-Version $ready.version) -ceq $normalized) {
        $ready | Add-Member -NotePropertyName notes -NotePropertyValue ([string]$release.body) -Force
        Emit $ready
        exit 0
    }
    $assets = @($release.assets | Where-Object { $_.name -match '\.exe$' -and $_.state -eq 'uploaded' })
    if ($assets.Count -ne 1) { throw 'Release must contain exactly one SceneMax Studio installer' }
    $asset = $assets[0]
    if ($asset.digest -notmatch '^sha256:([a-fA-F0-9]{64})$') { throw 'Release installer has no SHA-256 digest' }
    $digest = $Matches[1]
    if ($asset.size -le 0 -or $asset.size -ge 2147483648) { throw 'Invalid installer size' }
    if ($asset.browser_download_url -cnotmatch '^https://github\.com/scenemax3d/scenemax3d-desktop/releases/download/[^/]+/[^/]+$') { throw 'Unexpected installer URL' }
    Emit @{ status = 'downloading'; version = $version; notes = [string]$release.body }
    $partial = Join-Path $Cache 'installer.partial'
    try {
        Invoke-WebRequest -UseBasicParsing -Uri $asset.browser_download_url -OutFile $partial -TimeoutSec 7200
        if ((Get-Item -LiteralPath $partial).Length -ne $asset.size -or (File-Sha256 $partial) -ne $digest) { throw 'Downloaded installer failed verification' }
        Move-Item -LiteralPath $partial -Destination (Join-Path $Cache 'installer.exe') -Force
    } finally {
        if (Test-Path -LiteralPath $partial) { Remove-Item -LiteralPath $partial -Force }
    }
    $ready = @{ status = 'ready'; version = $version; notes = [string]$release.body; sha256 = $digest; size = $asset.size }
    Write-Json $readyPath $ready
    Emit $ready
} catch {
    $_ | Out-String | Set-Content -LiteralPath (Join-Path $Cache 'error.log')
    if ($Operation -eq 'Check' -and !$Force -and $cached) {
        Emit $ready
        exit 0
    }
    if ($Operation -eq 'Apply') {
        Add-Type -AssemblyName PresentationFramework
        [Windows.MessageBox]::Show("SceneMax update could not finish. Your projects are unchanged. See $Cache\error.log", 'SceneMax update') | Out-Null
    }
    exit 1
} finally { $lock.Dispose() }
