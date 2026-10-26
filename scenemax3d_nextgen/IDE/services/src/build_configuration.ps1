param([ValidateSet('Scan','Rust','Cpp','Web')][string]$Operation='Scan', [string]$Toolchain, [string]$BindgenVersion='0.2.129')
$ErrorActionPreference='Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$ProgressPreference='SilentlyContinue'
function Status($message) { Write-Output "SCENEMAX_STATUS $message" }
function Download($url, $target) {
    Status "Downloading $url"
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $request=[Net.HttpWebRequest]::Create($url)
    $request.Timeout=60000
    $request.ReadWriteTimeout=60000
    $response=$request.GetResponse()
    $inputStream=$response.GetResponseStream()
    $outputStream=[IO.File]::Create($target)
    try {
        $buffer=New-Object byte[] 65536
        $total=0L; $previous=-1
        while (($count=$inputStream.Read($buffer,0,$buffer.Length)) -gt 0) {
            $outputStream.Write($buffer,0,$count); $total+=$count
            if ($total -gt 100MB) { throw 'Installer download exceeds size limit.' }
            if ($response.ContentLength -gt 0) {
                $percent=[int](100*$total/$response.ContentLength)
                if ($percent -ne $previous) { Write-Output "SCENEMAX_PROGRESS $percent"; $previous=$percent }
            }
        }
    } finally { $outputStream.Dispose(); $inputStream.Dispose(); $response.Dispose() }
}
function Locate($name, $fallback) {
    $cmd=Get-Command $name -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    if ($fallback -and (Test-Path -LiteralPath $fallback)) { return $fallback }
    return ''
}
function Merge-StudioSearchPath([string[]]$Values) {
    # Preserve lookup order without growing PATH on every dependency scan.
    $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    $paths = [Collections.Generic.List[string]]::new()
    foreach ($value in $Values) {
        foreach ($entry in ($value -split ';')) {
            $entry = $entry.Trim()
            $key = $entry.Trim('"').TrimEnd('\', '/')
            if ($key -and $seen.Add($key)) { $paths.Add($entry) }
        }
    }
    return $paths -join ';'
}
try {
    # A running IDE may predate a tool installation or custom Cargo home setting.
    foreach ($key in @('CARGO_HOME','RUSTUP_HOME')) {
        if (![Environment]::GetEnvironmentVariable($key,'Process')) {
            $value=[Environment]::GetEnvironmentVariable($key,'User')
            if (!$value) { $value=[Environment]::GetEnvironmentVariable($key,'Machine') }
            if ($value) { [Environment]::SetEnvironmentVariable($key,$value,'Process') }
        }
    }
    $env:PATH=Merge-StudioSearchPath @($env:PATH, [Environment]::GetEnvironmentVariable('PATH','User'), [Environment]::GetEnvironmentVariable('PATH','Machine'))
    $cargoHome=if($env:CARGO_HOME){$env:CARGO_HOME}else{Join-Path $env:USERPROFILE '.cargo'}
    $cargo=Locate 'cargo.exe' (Join-Path $cargoHome 'bin\cargo.exe')
    $rustup=Locate 'rustup.exe' (Join-Path $cargoHome 'bin\rustup.exe')
    $vswhere=Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
    $vs=''
    if (Test-Path -LiteralPath $vswhere) {
        $vs=@(& $vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath) | Select-Object -First 1
    }
    if ($Operation -in @('Rust','Web')) {
        if (!$rustup) {
            $installer=Join-Path $PSScriptRoot 'rustup-init.exe'
            $url='https://static.rust-lang.org/rustup/dist/x86_64-pc-windows-msvc/rustup-init.exe'
            Download $url $installer
            Download "$url.sha256" "$installer.sha256"
            $expected=((Get-Content -LiteralPath "$installer.sha256" -Raw).Trim() -split '\s+')[0]
            if ((Get-FileHash -LiteralPath $installer -Algorithm SHA256).Hash -ne $expected) { throw 'Rust installer SHA256 verification failed.' }
            Status 'Installing rustup; existing default toolchain will be preserved.'
            $env:RUSTUP_INIT_SKIP_MSVC_CHECK='yes'
            & $installer -y --no-modify-path --default-toolchain none
            if ($LASTEXITCODE -ne 0) { throw "rustup setup failed ($LASTEXITCODE)" }
            $rustup=Join-Path $cargoHome 'bin\rustup.exe'
        }
        Status "Installing Rust $Toolchain; download details appear below."
        $ErrorActionPreference='Continue'
        & $rustup toolchain install "$Toolchain-x86_64-pc-windows-msvc" --profile minimal --component rustfmt --component clippy 2>&1 | ForEach-Object { "$_" }
        $ErrorActionPreference='Stop'
        if ($LASTEXITCODE -ne 0) { throw "Rust toolchain installation failed ($LASTEXITCODE)" }
        $cargo=Join-Path (Split-Path $rustup) 'cargo.exe'
    }
    $webTools=Join-Path $env:LOCALAPPDATA "SceneMax\web-tools\$BindgenVersion"
    if ($Operation -eq 'Web') {
        Status 'Installing the Rust browser target...'
        $ErrorActionPreference='Continue'
        & $rustup target add wasm32-unknown-unknown --toolchain "$Toolchain-x86_64-pc-windows-msvc" 2>&1 | ForEach-Object { "$_" }
        $ErrorActionPreference='Stop'
        if ($LASTEXITCODE -ne 0) { throw "Browser target installation failed ($LASTEXITCODE)" }
        Status "Building wasm-bindgen $BindgenVersion. This requires the C++ build tools and may take several minutes."
        $ErrorActionPreference='Continue'
        & $cargo "+$Toolchain-x86_64-pc-windows-msvc" install wasm-bindgen-cli --version $BindgenVersion --locked --root $webTools 2>&1 | ForEach-Object { "$_" }
        $ErrorActionPreference='Stop'
        if ($LASTEXITCODE -ne 0) { throw "wasm-bindgen installation failed ($LASTEXITCODE)" }
    }
    if ($Operation -eq 'Cpp') {
        $installer=Join-Path $PSScriptRoot 'vs_buildtools.exe'
        Download 'https://aka.ms/vs/17/release/vs_buildtools.exe' $installer
        $signature=Get-AuthenticodeSignature -LiteralPath $installer
        if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch 'O=Microsoft Corporation') { throw 'Microsoft installer signature verification failed.' }
        Status 'Installing C++ tools and Windows SDK. Approve the Windows administrator prompt; follow progress in Microsoft Setup.'
        $arguments='--passive --wait --norestart --add Microsoft.VisualStudio.Workload.VCTools --includeRecommended --add Microsoft.VisualStudio.Component.VC.CMake.Project'
        # Modify an existing Build Tools installation, while keeping full Visual Studio editions separate.
        $buildTools=if(Test-Path $vswhere){@(& $vswhere -latest -products Microsoft.VisualStudio.Product.BuildTools -property installationPath) | Select-Object -First 1}else{''}
        if ($buildTools) { $arguments='modify --installPath "'+$buildTools+'" '+$arguments }
        $started=Get-Date
        $process=Start-Process -FilePath $installer -ArgumentList $arguments -Verb RunAs -WindowStyle Hidden -PassThru
        $lastLog=''
        while (!$process.WaitForExit(2000)) {
            $latest=Get-ChildItem -LiteralPath $env:TEMP -Filter 'dd_setup_*.log' -ErrorAction SilentlyContinue | Where-Object {$_.LastWriteTime -ge $started} | Sort-Object LastWriteTime -Descending | Select-Object -First 1
            if ($latest) {
                $tail=(Get-Content -LiteralPath $latest.FullName -Tail 5 -ErrorAction SilentlyContinue) -join "`n"
                if ($tail -and $tail -ne $lastLog) { Write-Output $tail; $lastLog=$tail }
            }
        }
        if ($process.ExitCode -notin @(0,3010,1641)) { throw "Microsoft Setup failed ($($process.ExitCode)). Check %TEMP%\dd_* setup logs." }
        if ($process.ExitCode -in @(3010,1641)) { Write-Output 'A Windows restart is required before building.'; $reboot=$true }
        $vs=@(& $vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath) | Select-Object -First 1
    }
    Status 'Checking versions, compiler, SDK and build environment...'
    $environment=@{}
    $rustReady=$false
    $rustDetail="Missing Rust $Toolchain (MSVC) / Cargo."
    if ($rustup) {
        $ErrorActionPreference='Continue'
        $rustc=@(& $rustup which --toolchain "$Toolchain-x86_64-pc-windows-msvc" rustc 2>$null) | Select-Object -First 1
        $ErrorActionPreference='Stop'
        if ($LASTEXITCODE -eq 0 -and $rustc -and (Test-Path $rustc) -and $cargo) {
            $rustReady=$true; $rustDetail="$(& $rustc --version) | $cargo"
            $environment['RUSTUP_TOOLCHAIN']="$Toolchain-x86_64-pc-windows-msvc"
        }
    }
    $msvc=''; $sdk=''; $cmake=Locate 'cmake.exe' ''
    if ($vs) {
        $devcmd=Join-Path $vs 'Common7\Tools\VsDevCmd.bat'
        if (Test-Path $devcmd) {
            $env:SCENEMAX_VSDEVCMD=$devcmd
            $lines=& $env:ComSpec /d /s /c '""%SCENEMAX_VSDEVCMD%" -no_logo -arch=x64 -host_arch=x64 >nul && set"'
            foreach($line in $lines) {
                if ($line -match '^([^=]+)=(.*)$' -and $Matches[1] -in @('PATH','INCLUDE','LIB','LIBPATH','WindowsSdkDir','WindowsSDKVersion','VCToolsInstallDir','VSINSTALLDIR')) { $environment[$Matches[1]]=$Matches[2] }
            }
            if ($environment['VCToolsInstallDir']) { $msvc=Join-Path $environment['VCToolsInstallDir'] 'bin\Hostx64\x64\cl.exe' }
            if ($environment['WindowsSdkDir'] -and $environment['WindowsSDKVersion']) { $sdk=Join-Path $environment['WindowsSdkDir'] ('Lib\'+$environment['WindowsSDKVersion']+'um\x64\kernel32.lib') }
        }
    }
    if (!$sdk) {
        $kits=Get-ItemProperty -LiteralPath 'HKLM:\SOFTWARE\Microsoft\Windows Kits\Installed Roots' -ErrorAction SilentlyContinue
        $kitsRoot=if($kits.KitsRoot10){$kits.KitsRoot10}else{Join-Path ${env:ProgramFiles(x86)} 'Windows Kits\10'}
        if (Test-Path (Join-Path $kitsRoot 'Lib')) {
            foreach($version in @(Get-ChildItem -LiteralPath (Join-Path $kitsRoot 'Lib') -Directory | Sort-Object Name -Descending)) {
                $candidate=Join-Path $version.FullName 'um\x64\kernel32.lib'
                if ((Test-Path $candidate) -and (Test-Path (Join-Path $version.FullName 'ucrt\x64\ucrt.lib'))) {
                    $environment['WindowsSdkDir']=$kitsRoot
                    $environment['WindowsSDKVersion']=$version.Name+'\'
                    $sdk=$candidate
                    break
                }
            }
        }
    }
    # Native effects require CMake >= 3.18. Older VS installations can expose
    # an incompatible CMake even when a supported copy is installed elsewhere.
    $cmakeCandidates=@($cmake)
    if ($vs) { $cmakeCandidates+=Join-Path $vs 'Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe' }
    if (Test-Path $vswhere) {
        foreach($installation in @(& $vswhere -products '*' -property installationPath)) {
            $cmakeCandidates+=Join-Path $installation 'Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe'
        }
    }
    $cmakeCandidates+=Join-Path $env:ProgramFiles 'CMake\bin\cmake.exe'
    $cmake=''; $cmakeVersion=''
    foreach($candidate in $cmakeCandidates | Select-Object -Unique) {
        if (!$candidate -or !(Test-Path -LiteralPath $candidate)) { continue }
        $ErrorActionPreference='Continue'
        $versionText=(& $candidate --version 2>$null) -join ' '
        $ErrorActionPreference='Stop'
        if ($LASTEXITCODE -eq 0 -and $versionText -match 'cmake version (\d+\.\d+\.\d+)' -and [version]$Matches[1] -ge [version]'3.18.0') {
            $cmake=$candidate; $cmakeVersion=$Matches[1]; break
        }
    }
    $paths=@()
    if ($cargo) { $paths+=Split-Path $cargo }
    if ($cmake) { $paths+=Split-Path $cmake }
    $paths+=if($environment['PATH']){$environment['PATH']}else{$env:PATH}
    $environment['PATH']=Merge-StudioSearchPath $paths
    if ($env:CARGO_HOME) { $environment['CARGO_HOME']=$env:CARGO_HOME }
    if ($env:RUSTUP_HOME) { $environment['RUSTUP_HOME']=$env:RUSTUP_HOME }
    $wasmReady=$false
    if ($rustReady) {
        $ErrorActionPreference='Continue'
        $targets=@(& $rustup target list --installed --toolchain "$Toolchain-x86_64-pc-windows-msvc" 2>$null)
        $ErrorActionPreference='Stop'
        $wasmReady=$targets -contains 'wasm32-unknown-unknown'
    }
    $bindgen=Join-Path $webTools 'bin\wasm-bindgen.exe'
    if (!(Test-Path $bindgen)) { $bindgen=Locate 'wasm-bindgen.exe' (Join-Path $cargoHome 'bin\wasm-bindgen.exe') }
    $bindgenReady=$false
    if ($bindgen) {
        $ErrorActionPreference='Continue'
        $bindgenReady=((& $bindgen --version 2>$null) -join '').Trim() -eq "wasm-bindgen $BindgenVersion"
        $ErrorActionPreference='Stop'
    }
    $packages=@(
        @{name="Rust $Toolchain + Cargo";installed=$rustReady;detail=$rustDetail},
        @{name='Microsoft C++ compiler (x64)';installed=[bool]($msvc -and (Test-Path $msvc) -and (Test-Path (Join-Path (Split-Path $msvc) 'link.exe')));detail=$(if($msvc){$msvc}else{'Missing MSVC x64 build tools.'})},
        @{name='Windows SDK';installed=[bool]($sdk -and (Test-Path $sdk) -and (Test-Path (Join-Path $environment['WindowsSdkDir'] ('Lib\'+$environment['WindowsSDKVersion']+'ucrt\x64\ucrt.lib'))) -and (Test-Path (Join-Path $environment['WindowsSdkDir'] ('Include\'+$environment['WindowsSDKVersion']+'um\Windows.h'))));detail=$(if($sdk){$sdk}else{'Missing Windows SDK libraries and headers.'})},
        @{name='CMake (native effects)';installed=[bool]$cmake;detail=$(if($cmake){"$cmakeVersion | $cmake"}else{'Missing CMake 3.18 or newer; included with C++ setup.'})},
        @{name='Rust browser target';installed=$wasmReady;detail="wasm32-unknown-unknown for Rust $Toolchain"},
        @{name="Web bindings $BindgenVersion";installed=$bindgenReady;detail=$(if($bindgen){$bindgen}else{'Missing wasm-bindgen; use Install Web tools.'})}
    )
    Write-Output ('SCENEMAX_RESULT '+(@{packages=$packages;environment=$environment;cargo=$cargo} | ConvertTo-Json -Depth 5 -Compress))
    Write-Output 'SCENEMAX_PROGRESS 100'
    if ($reboot) { Status 'Setup complete. Restart Windows, then refresh before building.' }
    elseif (@($packages | Where-Object {!$_.installed}).Count -eq 0) { Status 'Ready for Windows x64 and Web builds. Detected paths saved for exports.' }
    else { Status 'Scan complete. Install missing components below, then refresh.' }
} catch { Write-Output $_.Exception.Message; exit 1 }
