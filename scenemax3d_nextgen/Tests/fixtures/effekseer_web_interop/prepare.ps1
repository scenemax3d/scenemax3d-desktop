param([switch]$SkipBuild, [string]$WasmBindgen)
$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$work = Join-Path $workspace 'target/effekseer-web-proof'
$vendor = Join-Path $workspace 'Engine/effects/web/vendor'
$site = Join-Path $work 'site'
if (!$WasmBindgen) {
    $managed = Join-Path $env:LOCALAPPDATA 'SceneMax/web-tools/0.2.129/bin/wasm-bindgen.exe'
    $WasmBindgen = if (Test-Path -LiteralPath $managed) { $managed } else { 'wasm-bindgen' }
}
if ((& $WasmBindgen --version) -ne 'wasm-bindgen 0.2.129') { throw 'Use wasm-bindgen 0.2.129 (SceneMax Settings > Build Configuration > Web tools).' }
New-Item -ItemType Directory -Force $work,$site | Out-Null
foreach ($line in Get-Content -LiteralPath (Join-Path $vendor 'SHA256SUMS')) {
    $expected, $name = $line -split '  ', 2
    if ((Get-FileHash -LiteralPath (Join-Path $vendor $name) -Algorithm SHA256).Hash -ne $expected) {
        throw "Effekseer checksum mismatch: $name"
    }
}
Push-Location $workspace
try {
    if (!$SkipBuild) {
        cargo build --locked --release -p scenemax_effects --example web_interop --target wasm32-unknown-unknown
        if ($LASTEXITCODE -ne 0) { throw 'Proof compilation failed' }
    }
    & $WasmBindgen 'target/wasm32-unknown-unknown/release/examples/web_interop.wasm' --target web --out-name proof_runtime --out-dir $site
    if ($LASTEXITCODE -ne 0) { throw 'Proof bindings failed' }
} finally { Pop-Location }
foreach ($file in @('index.html','proof.js')) { Copy-Item -LiteralPath (Join-Path $PSScriptRoot $file) -Destination $site }
foreach ($file in @('effekseer.js','effekseer-webgpu.js','effekseer-webgpu.wasm')) { Copy-Item -LiteralPath (Join-Path $vendor $file) -Destination $site }
$resource = Join-Path $site 'Resources/00_Basic'
New-Item -ItemType Directory -Force (Join-Path $resource 'Texture') | Out-Null
$fixture = Join-Path $workspace 'Tests/fixtures/web_effects/project/resources/effects/fixture'
Copy-Item -LiteralPath (Join-Path $fixture 'effect.efkefc') -Destination (Join-Path $resource 'Simple_Sprite_BillBoard.efkefc')
Copy-Item -LiteralPath (Join-Path $fixture 'Texture/Particle01.png') -Destination (Join-Path $resource 'Texture/Particle01.png')
Write-Output "Proof site: $site"
