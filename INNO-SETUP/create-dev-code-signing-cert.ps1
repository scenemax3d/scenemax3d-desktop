[CmdletBinding()]
param(
    [string]$Subject = "CN=SceneMax3D Dev Code Signing, OU=Engineering, O=Abware Informatica, L=Jerusalem, ST=Jerusalem, C=IL",
    [string]$Password = "changeit",
    [string]$Alias = "scenemax-dev-code-signing",
    [string]$OutputDir,
    [int]$ValidYears = 3
)

$ErrorActionPreference = "Stop"

if (-not $OutputDir) {
    $OutputDir = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) "certs"
}

if ($Password.Length -lt 6) {
    throw "The certificate password must be at least 6 characters for PKCS12."
}

if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
}

$keytool = (Get-Command keytool.exe -ErrorAction SilentlyContinue).Source
if (-not $keytool) {
    throw "keytool.exe was not found on PATH."
}

$pfxPath = Join-Path $OutputDir "scenemax-dev-code-signing.pfx"
$cerPath = Join-Path $OutputDir "scenemax-dev-code-signing.cer"

Remove-Item -LiteralPath $pfxPath, $cerPath -Force -ErrorAction SilentlyContinue

& $keytool `
    -genkeypair `
    -noprompt `
    -alias $Alias `
    -dname $Subject `
    -keyalg RSA `
    -keysize 2048 `
    -sigalg SHA256withRSA `
    -validity ($ValidYears * 365) `
    -storetype PKCS12 `
    -keystore $pfxPath `
    -storepass $Password `
    -keypass $Password `
    -ext "eku=codeSigning" `
    -ext "KU=digitalSignature"

if ($LASTEXITCODE -ne 0) {
    throw "keytool failed while creating the self-signed certificate."
}

& $keytool `
    -exportcert `
    -rfc `
    -alias $Alias `
    -storetype PKCS12 `
    -keystore $pfxPath `
    -storepass $Password `
    -file $cerPath

if ($LASTEXITCODE -ne 0) {
    throw "keytool failed while exporting the certificate."
}

Write-Host "Created self-signed development code-signing certificate:"
Write-Host "  PFX: $pfxPath"
Write-Host "  CER: $cerPath"
Write-Host ""
Write-Host "Use it for local testing only. Windows SmartScreen and other machines will not trust it by default."
