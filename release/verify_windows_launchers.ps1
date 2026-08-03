Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Require-File {
    param([Parameter(Mandatory = $true)][string] $Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Required distribution file is missing: $Path"
    }
}

function Invoke-ExpectExitCode {
    param(
        [Parameter(Mandatory = $true)][string] $Path,
        [string[]] $Arguments = @(),
        [Parameter(Mandatory = $true)][int] $ExpectedExitCode
    )

    & $Path @Arguments
    $actualExitCode = $LASTEXITCODE
    if ($actualExitCode -ne $ExpectedExitCode) {
        throw "Command returned ${actualExitCode}, expected ${ExpectedExitCode}: $Path $($Arguments -join ' ')"
    }
    $global:LASTEXITCODE = 0
}

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)][string] $Path,
        [string[]] $Arguments = @()
    )

    Invoke-ExpectExitCode -Path $Path -Arguments $Arguments -ExpectedExitCode 0
}

$credentialFiles = @(
    "server-ed25519-key.pk8",
    "server-ed25519-certificate.der",
    "registry-trust-roots.hex",
    "server-fingerprint.txt"
)

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$partialDirectory = $null
Push-Location $repositoryRoot
try {
    Remove-Item -Recurse -Force "client/build/install/sunderfront-client" -ErrorAction SilentlyContinue
    Remove-Item -Recurse -Force "server/build/install/sunderfront-server" -ErrorAction SilentlyContinue

    & .\gradlew.bat --no-daemon --no-configuration-cache :client:installDist :server:installDist
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle installDist failed with exit code $LASTEXITCODE"
    }

    $clientDirectory = (Resolve-Path "client/build/install/sunderfront-client").Path
    $serverDirectory = (Resolve-Path "server/build/install/sunderfront-server").Path

    $clientLauncher = Join-Path $clientDirectory "URUCHOM_KLIENTA.bat"
    $serverCredentialsLauncher = Join-Path $serverDirectory "1_GENERUJ_CREDENTIALS.bat"
    $serverLauncher = Join-Path $serverDirectory "2_URUCHOM_SERWER.bat"

    Require-File $clientLauncher
    Require-File (Join-Path $clientDirectory "README-PL.txt")
    Require-File (Join-Path $clientDirectory "tools/windows/require-java-21.bat")
    Require-File (Join-Path $clientDirectory "tools/sunderfront-direct-connect-smoke.bat")
    if (Test-Path -LiteralPath (Join-Path $clientDirectory "bin/sunderfront-direct-connect-smoke.bat")) {
        throw "Diagnostic smoke launcher must not be exposed in the client bin directory"
    }

    Require-File $serverCredentialsLauncher
    Require-File $serverLauncher
    Require-File (Join-Path $serverDirectory "README-PL.txt")
    Require-File (Join-Path $serverDirectory "tools/windows/require-java-21.bat")

    $env:SUNDERFRONT_NO_PAUSE = "1"
    Invoke-Checked $clientLauncher @("--smoke")

    Invoke-Checked $serverCredentialsLauncher
    $credentialHashes = @{}
    foreach ($fileName in $credentialFiles) {
        $path = Join-Path $serverDirectory "credentials/$fileName"
        Require-File $path
        $credentialHashes[$fileName] = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
    }

    Invoke-Checked $serverCredentialsLauncher
    foreach ($fileName in $credentialFiles) {
        $path = Join-Path $serverDirectory "credentials/$fileName"
        $currentHash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
        if ($currentHash -ne $credentialHashes[$fileName]) {
            throw "Repeated credential launcher changed existing file: $fileName"
        }
    }

    Invoke-Checked $serverLauncher @("--validate-config")

    $partialDirectory = Join-Path $env:RUNNER_TEMP "sunderfront-server-partial-$([Guid]::NewGuid().ToString('N'))"
    New-Item -ItemType Directory -Path $partialDirectory | Out-Null
    Copy-Item -Path (Join-Path $serverDirectory "*") -Destination $partialDirectory -Recurse -Force
    $partialCredentials = Join-Path $partialDirectory "credentials"
    Remove-Item -Path (Join-Path $partialCredentials "*") -Recurse -Force -ErrorAction SilentlyContinue

    $sentinelPath = Join-Path $partialCredentials "server-ed25519-key.pk8"
    [System.IO.File]::WriteAllBytes($sentinelPath, [byte[]](0x53, 0x46, 0x54, 0x45, 0x53, 0x54))
    $sentinelHash = (Get-FileHash -LiteralPath $sentinelPath -Algorithm SHA256).Hash

    $partialLauncher = Join-Path $partialDirectory "1_GENERUJ_CREDENTIALS.bat"
    Invoke-ExpectExitCode -Path $partialLauncher -ExpectedExitCode 16

    if ((Get-FileHash -LiteralPath $sentinelPath -Algorithm SHA256).Hash -ne $sentinelHash) {
        throw "Partial credential handling modified the existing private-key sentinel"
    }
    foreach ($fileName in $credentialFiles | Where-Object { $_ -ne "server-ed25519-key.pk8" }) {
        if (Test-Path -LiteralPath (Join-Path $partialCredentials $fileName)) {
            throw "Partial credential handling created a missing file: $fileName"
        }
    }

    Remove-Item -LiteralPath (Join-Path $partialDirectory "bin/sunderfront-server-credentials.bat") -Force
    Invoke-ExpectExitCode -Path $partialLauncher -ExpectedExitCode 18
    if ((Get-FileHash -LiteralPath $sentinelPath -Algorithm SHA256).Hash -ne $sentinelHash) {
        throw "Mixed-package detection modified the existing private-key sentinel"
    }
    foreach ($fileName in $credentialFiles | Where-Object { $_ -ne "server-ed25519-key.pk8" }) {
        if (Test-Path -LiteralPath (Join-Path $partialCredentials $fileName)) {
            throw "Mixed-package detection created a credential file: $fileName"
        }
    }
}
finally {
    if ($null -ne $partialDirectory -and (Test-Path -LiteralPath $partialDirectory)) {
        Remove-Item -LiteralPath $partialDirectory -Recurse -Force -ErrorAction SilentlyContinue
    }
    Pop-Location
}
