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

function Convert-ToLaunch4jVersion {
    param([string]$Version)

    if (-not $Version) {
        throw "Application version is empty."
    }

    $clean = $Version.Trim()
    $parts = @()

    foreach ($part in ($clean -split '\.')) {
        if ($part -match '^\d+$') {
            $parts += $part
            continue
        }

        if ($part -match '^(\d+)') {
            $parts += $matches[1]
            continue
        }

        $parts += '0'
    }

    while ($parts.Count -lt 4) {
        $parts += '0'
    }

    if ($parts.Count -gt 4) {
        $parts = $parts[0..3]
    }

    return ($parts -join '.')
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

function Find-Java {
    $java = (Get-Command java.exe -ErrorAction SilentlyContinue).Source
    if (-not $java) {
        throw "java.exe was not found on PATH."
    }

    return $java
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
        [string]$JavaExe,
        [string]$JsignJar,
        [string]$PfxPath,
        [string]$PfxPassword,
        [string]$Alias,
        [string]$FileToSign,
        [string]$TimestampServer
    )

    $arguments = @(
        "-jar", $JsignJar,
        "--keystore", $PfxPath,
        "--storetype", "PKCS12",
        "--storepass", $PfxPassword,
        "--alias", $Alias,
        "--alg", "SHA-256"
    )

    if ($TimestampServer) {
        $arguments += @("--tsaurl", $TimestampServer)
    }

    $arguments += $FileToSign
    Invoke-Step -FilePath $JavaExe -Arguments $arguments -WorkingDirectory (Split-Path -Parent $FileToSign)
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

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptRoot

if (-not $AppVersion) {
    $AppVersion = Get-AppVersion -RepoRoot $repoRoot
}
$AppVersion = $AppVersion.Trim()
$launch4jVersion = Convert-ToLaunch4jVersion -Version $AppVersion

$gradleWrapper = Join-Path $repoRoot "gradlew.bat"
$launch4jCompiler = Join-Path $repoRoot "launch4j\launch4jc.exe"
$innoCompiler = "C:\Program Files (x86)\Inno Setup 6\ISCC.exe"
$installerScript = Join-Path $scriptRoot "scenemax-setup-project.iss"
$desktopJar = Join-Path $repoRoot "build\libs\scenemax_desktop-1.0-SNAPSHOT-all.jar"
$projectorJar = Join-Path $repoRoot "out\artifacts\scenemax_win_projector.jar"
$outputDir = Join-Path $scriptRoot "Output"
$launch4jOutput = Join-Path $repoRoot "LAUNCH4J-PROJECT\scenemax3d.exe"
$launch4jIcon = Join-Path $repoRoot "LAUNCH4J-PROJECT\scenemax.ico"
$jsignJar = Join-Path $repoRoot "launch4j\sign4j\jsign-2.0.jar"

Assert-FileExists -Path $gradleWrapper -Description "Gradle wrapper"
Assert-FileExists -Path $launch4jCompiler -Description "Launch4j compiler"
Assert-FileExists -Path $innoCompiler -Description "Inno Setup compiler"
Assert-FileExists -Path $installerScript -Description "Installer script"
Assert-FileExists -Path $launch4jIcon -Description "Launch4j icon"
Assert-FileExists -Path $jsignJar -Description "jsign jar"

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
Assert-FileExists -Path $projectorJar -Description "Projector jar"

$launch4jConfig = Join-Path $env:TEMP "scenemax-launch4j-$PID.xml"
@"
<?xml version="1.0" encoding="UTF-8"?>
<launch4jConfig>
  <dontWrapJar>false</dontWrapJar>
  <headerType>gui</headerType>
  <jar>$([System.Security.SecurityElement]::Escape($desktopJar))</jar>
  <outfile>$([System.Security.SecurityElement]::Escape($launch4jOutput))</outfile>
  <errTitle></errTitle>
  <cmdLine></cmdLine>
  <chdir>.</chdir>
  <priority>normal</priority>
  <downloadUrl>https://scenemax3d.com/java-run-time-install/</downloadUrl>
  <supportUrl>https://www.scenemax3d.com</supportUrl>
  <stayAlive>false</stayAlive>
  <restartOnCrash>false</restartOnCrash>
  <manifest></manifest>
  <icon>$([System.Security.SecurityElement]::Escape($launch4jIcon))</icon>
  <jre>
    <path>%JAVA_HOME%;%PATH%</path>
    <requiresJdk>false</requiresJdk>
    <requires64Bit>true</requires64Bit>
    <minVersion>11.0.21</minVersion>
    <maxVersion></maxVersion>
    <opt>-XX:MaxDirectMemorySize=1024m</opt>
  </jre>
  <versionInfo>
    <fileVersion>$launch4jVersion</fileVersion>
    <txtFileVersion>$AppVersion</txtFileVersion>
    <fileDescription>SceneMax3D Development Environment</fileDescription>
    <copyright>(c) 2026 Abware Informatica</copyright>
    <productVersion>$launch4jVersion</productVersion>
    <txtProductVersion>$AppVersion</txtProductVersion>
    <productName>SceneMax3D</productName>
    <companyName>Abware Informatica</companyName>
    <internalName>SceneMax3D</internalName>
    <originalFilename>scenemax3d.exe</originalFilename>
    <trademarks>SceneMax3D (TM)</trademarks>
    <language>ENGLISH_US</language>
  </versionInfo>
</launch4jConfig>
"@ | Set-Content -Path $launch4jConfig -Encoding UTF8

try {
    Invoke-Step -FilePath $launch4jCompiler -Arguments @($launch4jConfig) -WorkingDirectory $repoRoot
}
finally {
    if (Test-Path $launch4jConfig) {
        Remove-Item -LiteralPath $launch4jConfig -Force
    }
}

Assert-FileExists -Path $launch4jOutput -Description "Generated Launch4j executable"

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

    $javaExe = Find-Java
    Invoke-CodeSigner -JavaExe $javaExe -JsignJar $jsignJar -PfxPath $SignPfxPath -PfxPassword $SignPfxPassword -Alias $SignAlias -FileToSign $launch4jOutput -TimestampServer $TimestampUrl
}

$isccArgs += $installerScript
Invoke-Step -FilePath $innoCompiler -Arguments $isccArgs -WorkingDirectory $scriptRoot

$setupExe = Join-Path $outputDir "scenemax3d-$AppVersion-setup.exe"
Assert-FileExists -Path $setupExe -Description "Final installer"

if ($SignPfxPath) {
    Invoke-CodeSigner -JavaExe $javaExe -JsignJar $jsignJar -PfxPath $SignPfxPath -PfxPassword $SignPfxPassword -Alias $SignAlias -FileToSign $setupExe -TimestampServer $TimestampUrl
}

Write-Host ""
Write-Host "Installer ready:"
Write-Host "  $setupExe"
