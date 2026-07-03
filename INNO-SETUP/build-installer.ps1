[CmdletBinding()]
param(
    [string]$AppVersion,
    [string]$SignPfxPath,
    [string]$SignPfxPassword,
    [string]$SignAlias = "scenemax-dev-code-signing",
    [string]$SignToolPath,
    [string]$TimestampUrl = "http://timestamp.digicert.com",
    [switch]$SkipGradleBuild
)

$ErrorActionPreference = "Stop"

function Get-AppVersion {
    param([string]$RepoRoot)

    $utilPath = Join-Path $RepoRoot "src\com\scenemax\desktop\Util.java"
    $match = Select-String -Path $utilPath -Pattern 'APPLICATION_VERSION\s*=\s*"([^"]+)"' | Select-Object -First 1
    if (-not $match) {
        throw "Could not find APPLICATION_VERSION in $utilPath"
    }

    return $match.Matches[0].Groups[1].Value
}

function Find-SignTool {
    $candidates = @(
        "C:\Program Files (x86)\Windows Kits\10\bin\10.0.26100.0\x64\signtool.exe",
        "C:\Program Files (x86)\Windows Kits\10\bin\10.0.22621.0\x64\signtool.exe",
        "C:\Program Files (x86)\Windows Kits\10\bin\10.0.22000.0\x64\signtool.exe",
        "C:\Program Files (x86)\Windows Kits\10\bin\10.0.19041.0\x64\signtool.exe",
        "C:\Program Files (x86)\Windows Kits\10\bin\10.0.18362.0\x64\signtool.exe",
        "C:\Program Files (x86)\Microsoft SDKs\ClickOnce\SignTool\signtool.exe"
    )

    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    return $null
}

function Assert-FileExists {
    param(
        [string]$Path,
        [string]$Description
    )

    if (-not (Test-Path $Path -PathType Leaf)) {
        throw "$Description was not found: $Path"
    }
}

function Invoke-CodeSigner {
    param(
        [string]$SignToolPath,
        [string]$PfxPath,
        [string]$PfxPassword,
        [string]$FileToSign,
        [string]$TimestampServer
    )

    $arguments = @(
        "sign",
        "/f", $PfxPath,
        "/p", $PfxPassword,
        "/fd", "SHA256"
    )

    if ($TimestampServer) {
        $arguments += @("/tr", $TimestampServer, "/td", "SHA256")
    }

    $arguments += $FileToSign
    Invoke-Step -FilePath $SignToolPath -Arguments $arguments -WorkingDirectory (Split-Path -Parent $FileToSign)
}

function Invoke-Step {
    param(
        [string]$FilePath,
        [string[]]$Arguments,
        [string]$WorkingDirectory
    )

    Write-Host "Running: $FilePath $($Arguments -join ' ')"
    Push-Location $WorkingDirectory
    try {
        & $FilePath @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "Command failed with exit code ${LASTEXITCODE}: $FilePath"
        }
    }
    finally {
        Pop-Location
    }
}

function Ensure-NativeLauncherStub {
    param(
        [string]$RepoRoot,
        [string]$StubPath
    )

    if (Test-Path $StubPath -PathType Leaf) {
        return
    }

    $zig = (Get-Command zig.exe -ErrorAction SilentlyContinue).Source
    if (-not $zig) {
        throw "Native launcher stub was not found and zig.exe is not available on PATH: $StubPath"
    }

    $source = Join-Path $RepoRoot "tools\native-launcher\zig\scenemax_launcher.zig"
    Assert-FileExists -Path $source -Description "Native launcher source"

    $stubDir = Split-Path -Parent $StubPath
    if (-not (Test-Path $stubDir)) {
        New-Item -ItemType Directory -Force -Path $stubDir | Out-Null
    }

    Invoke-Step -FilePath $zig -Arguments @(
        "build-exe",
        "-O", "ReleaseSmall",
        "-target", "x86_64-windows",
        "--subsystem", "windows",
        "-femit-bin=$StubPath",
        $source
    ) -WorkingDirectory $RepoRoot
}

function New-SceneMaxNativeJavaExecutable {
    param(
        [string]$StubPath,
        [string]$JarPath,
        [string]$OutputPath
    )

    Assert-FileExists -Path $StubPath -Description "Native launcher stub"
    Assert-FileExists -Path $JarPath -Description "Desktop fat jar"

    $outputDir = Split-Path -Parent $OutputPath
    if (-not (Test-Path $outputDir)) {
        New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
    }

    $payloadPath = Join-Path $env:TEMP "scenemax-native-payload-$PID.smxp"
    $jarEntryName = "scenemax3d_scene.jar"
    $ascii = [System.Text.Encoding]::ASCII
    $utf8 = [System.Text.UTF8Encoding]::new($false)

    $writer = [System.IO.BinaryWriter]::new([System.IO.File]::Open($payloadPath, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None))
    try {
        $entryNameBytes = $utf8.GetBytes($jarEntryName)
        $writer.Write($ascii.GetBytes("SMXPKG1"))
        $writer.Write([uint32]1)
        $writer.Write([byte]0)
        $writer.Write([byte]0)
        $writer.Write([uint32]$entryNameBytes.Length)
        $writer.Write([uint64](Get-Item -LiteralPath $JarPath).Length)
        $writer.Write($entryNameBytes)

        $jarStream = [System.IO.File]::OpenRead($JarPath)
        try {
            $jarStream.CopyTo($writer.BaseStream)
        }
        finally {
            $jarStream.Dispose()
        }
    }
    finally {
        $writer.Dispose()
    }

    try {
        $sha = [System.Security.Cryptography.SHA256]::Create()
        try {
            $payloadHash = $sha.ComputeHash([System.IO.File]::ReadAllBytes($payloadPath))
        }
        finally {
            $sha.Dispose()
        }

        $outStream = [System.IO.File]::Open($OutputPath, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
        try {
            foreach ($file in @($StubPath, $payloadPath)) {
                $inStream = [System.IO.File]::OpenRead($file)
                try {
                    $inStream.CopyTo($outStream)
                }
                finally {
                    $inStream.Dispose()
                }
            }

            $footer = [System.IO.BinaryWriter]::new($outStream, $ascii, $true)
            try {
                $footer.Write([uint64](Get-Item -LiteralPath $payloadPath).Length)
                $footer.Write($payloadHash)
                $footer.Write($ascii.GetBytes("SCENEMAX_PAYLOAD"))
            }
            finally {
                $footer.Dispose()
            }
        }
        finally {
            $outStream.Dispose()
        }
    }
    finally {
        if (Test-Path $payloadPath) {
            Remove-Item -LiteralPath $payloadPath -Force
        }
    }

    Write-Host "Built native SceneMax3D executable: $OutputPath"
}

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptRoot

if (-not $AppVersion) {
    $AppVersion = Get-AppVersion -RepoRoot $repoRoot
}
$AppVersion = $AppVersion.Trim()

$gradleWrapper = Join-Path $repoRoot "gradlew.bat"
$innoCompiler = "C:\Program Files (x86)\Inno Setup 6\ISCC.exe"
$installerScript = Join-Path $scriptRoot "scenemax-setup-project.iss"
$desktopJar = Join-Path $repoRoot "build\libs\scenemax_desktop-1.0-SNAPSHOT-all.jar"
$projectorArtifactDir = Join-Path $repoRoot "out\artifacts"
$projectorJars = @(
    Join-Path $projectorArtifactDir "scenemax_projector-windows.jar"
    Join-Path $projectorArtifactDir "scenemax_projector-linux.jar"
    Join-Path $projectorArtifactDir "scenemax_projector-macos.jar"
)
$outputDir = Join-Path $scriptRoot "Output"
$nativeLauncherStub = Join-Path $repoRoot "tools\native-launcher\bin\windows-x64\scenemax-selfextract.exe"
$nativeLauncherOutput = Join-Path $repoRoot "build\native-launcher\scenemax3d.exe"

Assert-FileExists -Path $gradleWrapper -Description "Gradle wrapper"
Assert-FileExists -Path $innoCompiler -Description "Inno Setup compiler"
Assert-FileExists -Path $installerScript -Description "Installer script"

if (-not $SkipGradleBuild) {
    $gradleUserHome = Join-Path $repoRoot ".gradle-home"
    if (-not (Test-Path $gradleUserHome)) {
        New-Item -ItemType Directory -Path $gradleUserHome | Out-Null
    }
    $env:GRADLE_USER_HOME = $gradleUserHome
    Invoke-Step -FilePath $gradleWrapper -Arguments @(
        "build",
        ":scenemax_win_projector:publishProjectorArtifact"
    ) -WorkingDirectory $repoRoot
}

Assert-FileExists -Path $desktopJar -Description "Desktop fat jar"
foreach ($projectorJar in $projectorJars) {
    Assert-FileExists -Path $projectorJar -Description "Projector jar"
}

Ensure-NativeLauncherStub -RepoRoot $repoRoot -StubPath $nativeLauncherStub
New-SceneMaxNativeJavaExecutable -StubPath $nativeLauncherStub -JarPath $desktopJar -OutputPath $nativeLauncherOutput
Assert-FileExists -Path $nativeLauncherOutput -Description "Generated native SceneMax3D executable"

if ($SignPfxPath -and -not $SignToolPath) {
    $SignToolPath = Find-SignTool
}

$isccArgs = @(
    "/DMyAppVersion=$AppVersion",
    "/DOutputBaseFilename=scenemax3d-$AppVersion-setup"
)

if ($SignPfxPath) {
    $SignPfxPath = (Resolve-Path $SignPfxPath).Path
    Assert-FileExists -Path $SignPfxPath -Description "Signing certificate"
    if (-not $SignPfxPassword) {
        throw "Signing was requested, but -SignPfxPassword was not provided."
    }
    if (-not $SignToolPath) {
        throw "Signing was requested, but signtool.exe was not found. Pass -SignToolPath or install the Windows SDK."
    }

    Invoke-CodeSigner -SignToolPath $SignToolPath -PfxPath $SignPfxPath -PfxPassword $SignPfxPassword -FileToSign $nativeLauncherOutput -TimestampServer $TimestampUrl
}

$isccArgs += $installerScript
Invoke-Step -FilePath $innoCompiler -Arguments $isccArgs -WorkingDirectory $scriptRoot

$setupExe = Join-Path $outputDir "scenemax3d-$AppVersion-setup.exe"
Assert-FileExists -Path $setupExe -Description "Final installer"

if ($SignPfxPath) {
    Invoke-CodeSigner -SignToolPath $SignToolPath -PfxPath $SignPfxPath -PfxPassword $SignPfxPassword -FileToSign $setupExe -TimestampServer $TimestampUrl
}

Write-Host ""
Write-Host "Installer ready:"
Write-Host "  $setupExe"
