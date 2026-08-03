param(
    [Parameter(Mandatory = $true)]
    [string] $Destination
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Require-File {
    param([Parameter(Mandatory = $true)][string] $Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Required file is missing: $Path"
    }
}

if (-not $IsWindows) {
    throw "The Windows app image must be built on Windows"
}
if ([System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture -ne
    [System.Runtime.InteropServices.Architecture]::X64) {
    throw "The Windows app image requires an x64 build host"
}

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$version = (Get-Content -LiteralPath (Join-Path $repositoryRoot "release/version.txt") -Raw).Trim()
if ($version -notmatch '^0\.1\.0-alpha\.(\d+)$') {
    throw "Unsupported Windows app-image product version: $version"
}
$appVersion = "0.1.$($Matches[1])"
$installDirectory = Join-Path $repositoryRoot "client/build/install/sunderfront-client"
$libraryDirectory = Join-Path $installDirectory "lib"
$mainJarName = "client-${version}.jar"
$mainJar = Join-Path $libraryDirectory $mainJarName
Require-File $mainJar

$jpackage = Join-Path $env:JAVA_HOME "bin/jpackage.exe"
$jdeps = Join-Path $env:JAVA_HOME "bin/jdeps.exe"
Require-File $jpackage
Require-File $jdeps

$destinationPath = [System.IO.Path]::GetFullPath($Destination)
Remove-Item -Recurse -Force $destinationPath -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $destinationPath | Out-Null

$generatedDirectory = Join-Path $destinationPath "generated"
$icon = Join-Path $generatedDirectory "Sunderfront.ico"
& python (Join-Path $repositoryRoot "release/windows/generate_sunderfront_icon.py") $icon
if ($LASTEXITCODE -ne 0) {
    throw "Icon generation failed with exit code $LASTEXITCODE"
}
Require-File $icon

$classPath = (Get-ChildItem -LiteralPath $libraryDirectory -Filter '*.jar' -File |
    ForEach-Object { $_.FullName }) -join ';'
$jdepsOutput = & $jdeps `
    --multi-release 21 `
    --ignore-missing-deps `
    --print-module-deps `
    --class-path $classPath `
    $mainJar 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "jdeps failed: $($jdepsOutput -join [Environment]::NewLine)"
}
$detectedLine = $jdepsOutput |
    Where-Object { $_ -match '^[a-z0-9.,]+$' } |
    Select-Object -Last 1
if (-not $detectedLine) {
    throw "jdeps did not return a module dependency list"
}

$modules = [System.Collections.Generic.SortedSet[string]]::new([StringComparer]::Ordinal)
@(
    'java.base',
    'java.desktop',
    'java.logging',
    'java.management',
    'java.naming',
    'java.net.http',
    'java.prefs',
    'java.sql',
    'java.xml',
    'jdk.crypto.ec',
    'jdk.unsupported'
) | ForEach-Object { [void] $modules.Add($_) }
$detectedLine.Split(',') |
    Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
    ForEach-Object { [void] $modules.Add($_.Trim()) }
$moduleList = ($modules | ForEach-Object { $_ }) -join ','

$appImageParent = Join-Path $destinationPath "image"
New-Item -ItemType Directory -Force -Path $appImageParent | Out-Null
$jpackageArguments = @(
    '--type', 'app-image',
    '--dest', $appImageParent,
    '--name', 'Sunderfront',
    '--app-version', $appVersion,
    '--vendor', 'Grzegorz',
    '--copyright', 'Copyright (c) 2026 Grzegorz',
    '--description', 'Sunderfront standalone multiplayer alpha client',
    '--input', $libraryDirectory,
    '--main-jar', $mainJarName,
    '--main-class', 'pl.grzegorz2047.standalonethewalls.client.ClientMain',
    '--icon', $icon,
    '--add-modules', $moduleList,
    '--java-options', '-Dfile.encoding=UTF-8'
)
& $jpackage @jpackageArguments
if ($LASTEXITCODE -ne 0) {
    throw "jpackage failed with exit code $LASTEXITCODE"
}

$appImage = Join-Path $appImageParent "Sunderfront"
Require-File (Join-Path $appImage "Sunderfront.exe")
Require-File (Join-Path $appImage "runtime/release")

Copy-Item -LiteralPath (Join-Path $repositoryRoot "release/client/README-WINDOWS.md") `
    -Destination (Join-Path $appImage "README.md")
Copy-Item -LiteralPath (Join-Path $repositoryRoot "release/client/README-WINDOWS-PL.txt") `
    -Destination (Join-Path $appImage "README-PL.txt")
Copy-Item -LiteralPath (Join-Path $repositoryRoot "release/windows/ICON.md") `
    -Destination (Join-Path $appImage "ICON-LICENSE.md")
Copy-Item -LiteralPath (Join-Path $repositoryRoot "LICENSE") `
    -Destination (Join-Path $appImage "LICENSE.txt")
New-Item -ItemType Directory -Force -Path (Join-Path $appImage "assets") | Out-Null
Copy-Item -LiteralPath (Join-Path $repositoryRoot "assets/assets.lock.json") `
    -Destination (Join-Path $appImage "assets/assets.lock.json")

Write-Output $appImage
