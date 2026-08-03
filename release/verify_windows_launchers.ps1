Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Require-File {
    param([Parameter(Mandatory = $true)][string] $Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Required distribution file is missing: $Path"
    }
}

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)][string] $Path,
        [string[]] $Arguments = @()
    )

    & $Path @Arguments
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        throw "Command failed with exit code ${exitCode}: $Path $($Arguments -join ' ')"
    }
}

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
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
    Invoke-Checked $serverLauncher @("--validate-config")

    Require-File (Join-Path $serverDirectory "credentials/server-ed25519-key.pk8")
    Require-File (Join-Path $serverDirectory "credentials/server-ed25519-certificate.der")
    Require-File (Join-Path $serverDirectory "credentials/registry-trust-roots.hex")
    Require-File (Join-Path $serverDirectory "credentials/server-fingerprint.txt")
}
finally {
    Pop-Location
}
