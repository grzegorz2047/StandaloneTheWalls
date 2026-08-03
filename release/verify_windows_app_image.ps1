Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Require-File {
    param([Parameter(Mandatory = $true)][string] $Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Required Windows app-image file is missing: $Path"
    }
}

function Assert-NoRuntimeData {
    param([Parameter(Mandatory = $true)][string] $Image)

    $forbiddenSuffixes = @('.pk8', '.sqlite', '.sqlite-wal', '.sqlite-shm', '.sfki', '.sftr', '.sfrb')
    Get-ChildItem -LiteralPath $Image -Recurse -Force | ForEach-Object {
        $name = $_.Name.ToLowerInvariant()
        foreach ($suffix in $forbiddenSuffixes) {
            if ($name.EndsWith($suffix, [StringComparison]::Ordinal)) {
                throw "Runtime or credential file was packaged: $($_.FullName)"
            }
        }
    }
    foreach ($directory in @('data', 'credentials', 'cache')) {
        if (Test-Path -LiteralPath (Join-Path $Image $directory)) {
            throw "Runtime data directory was packaged: $directory"
        }
    }
}

function Invoke-AppImageSmoke {
    param(
        [Parameter(Mandatory = $true)][string] $Image,
        [Parameter(Mandatory = $true)][string] $WorkingDirectory
    )

    $executable = Join-Path $Image 'Sunderfront.exe'
    $runtimeRelease = Join-Path $Image 'runtime/release'
    $runtimeJava = Join-Path $Image 'runtime/bin/java.exe'
    Require-File $executable
    Require-File $runtimeRelease
    Require-File $runtimeJava
    Require-File (Join-Path $Image 'app/Sunderfront.cfg')
    Require-File (Join-Path $Image 'README.md')
    Require-File (Join-Path $Image 'README-PL.txt')
    Require-File (Join-Path $Image 'ICON-LICENSE.md')
    Require-File (Join-Path $Image 'assets/assets.lock.json')

    $runtimeProperties = & $runtimeJava -XshowSettings:properties -version 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Bundled runtime could not report its properties"
    }
    $runtimeText = $runtimeProperties -join [Environment]::NewLine
    if ($runtimeText -notmatch '(?m)^\s*java\.version\s*=\s*21(?:\.|$)') {
        throw "Bundled runtime is not Java 21: $runtimeText"
    }
    if ($runtimeText -notmatch '(?m)^\s*os\.arch\s*=\s*(?:amd64|x86_64)\s*$') {
        throw "Bundled runtime is not x64: $runtimeText"
    }
    foreach ($tool in @('javac.exe', 'javadoc.exe', 'jpackage.exe', 'jcmd.exe', 'jconsole.exe')) {
        if (Test-Path -LiteralPath (Join-Path $Image "runtime/bin/$tool")) {
            throw "Bundled runtime contains a forbidden JDK tool: $tool"
        }
    }
    Assert-NoRuntimeData $Image

    New-Item -ItemType Directory -Force -Path $WorkingDirectory | Out-Null
    $savedJavaHome = $env:JAVA_HOME
    $savedPath = $env:PATH
    try {
        Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
        $env:PATH = "$env:SystemRoot\System32;$env:SystemRoot"
        $process = Start-Process `
            -FilePath $executable `
            -ArgumentList '--smoke' `
            -WorkingDirectory $WorkingDirectory `
            -Wait `
            -PassThru
        if ($process.ExitCode -ne 0) {
            throw "Sunderfront.exe --smoke failed with exit code $($process.ExitCode)"
        }
    }
    finally {
        if ($null -eq $savedJavaHome) {
            Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
        }
        else {
            $env:JAVA_HOME = $savedJavaHome
        }
        $env:PATH = $savedPath
    }
}

if (-not $IsWindows) {
    throw "The Windows app-image verification must run on Windows"
}

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $repositoryRoot
$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("sunderfront-app-image-" + [guid]::NewGuid())
try {
    $version = (Get-Content -LiteralPath 'release/version.txt' -Raw).Trim()
    $archiveBase = "sunderfront-client-windows-x64-${version}"
    $firstRoot = Join-Path $temporaryRoot 'first'
    $secondRoot = Join-Path $temporaryRoot 'second'
    $relocatedRoot = Join-Path $temporaryRoot 'relocated'
    $firstZip = Join-Path $temporaryRoot 'first.zip'
    $secondZip = Join-Path $temporaryRoot 'second.zip'

    & .\gradlew.bat --no-daemon --no-configuration-cache clean :client:installDist
    if ($LASTEXITCODE -ne 0) {
        throw "First clean client build failed with exit code $LASTEXITCODE"
    }
    & .\release\windows\build_app_image.ps1 -Destination $firstRoot | Out-Host
    $firstImage = Join-Path $firstRoot 'image/Sunderfront'
    Invoke-AppImageSmoke $firstImage (Join-Path $temporaryRoot 'working-first')
    & python .\release\windows\package_app_image.py $firstImage $firstZip $archiveBase
    if ($LASTEXITCODE -ne 0) {
        throw "First deterministic archive failed with exit code $LASTEXITCODE"
    }

    New-Item -ItemType Directory -Force -Path $relocatedRoot | Out-Null
    $relocatedImage = Join-Path $relocatedRoot 'Sunderfront'
    Move-Item -LiteralPath $firstImage -Destination $relocatedImage
    Invoke-AppImageSmoke $relocatedImage (Join-Path $temporaryRoot 'working-relocated')

    & .\gradlew.bat --no-daemon --no-configuration-cache clean :client:installDist
    if ($LASTEXITCODE -ne 0) {
        throw "Second clean client build failed with exit code $LASTEXITCODE"
    }
    & .\release\windows\build_app_image.ps1 -Destination $secondRoot | Out-Host
    $secondImage = Join-Path $secondRoot 'image/Sunderfront'
    Invoke-AppImageSmoke $secondImage (Join-Path $temporaryRoot 'working-second')
    & python .\release\windows\package_app_image.py $secondImage $secondZip $archiveBase
    if ($LASTEXITCODE -ne 0) {
        throw "Second deterministic archive failed with exit code $LASTEXITCODE"
    }

    & python .\release\windows\compare_app_images.py $relocatedImage $secondImage
    if ($LASTEXITCODE -ne 0) {
        throw "The two clean jpackage app images differ"
    }
    $firstHash = (Get-FileHash -LiteralPath $firstZip -Algorithm SHA256).Hash
    $secondHash = (Get-FileHash -LiteralPath $secondZip -Algorithm SHA256).Hash
    if ($firstHash -cne $secondHash) {
        throw "Deterministic Windows archives differ: first=$firstHash second=$secondHash"
    }

    $releaseDirectory = Join-Path $repositoryRoot 'build/windows-release'
    Remove-Item -Recurse -Force $releaseDirectory -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $releaseDirectory | Out-Null
    $releaseArchive = Join-Path $releaseDirectory "${archiveBase}.zip"
    Copy-Item -LiteralPath $secondZip -Destination $releaseArchive
    Write-Output "Windows app-image archive: $releaseArchive"
    Write-Output "SHA-256: $secondHash"
}
finally {
    Pop-Location
    Remove-Item -Recurse -Force $temporaryRoot -ErrorAction SilentlyContinue
}
