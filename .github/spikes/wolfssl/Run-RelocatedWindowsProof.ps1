$ErrorActionPreference = "Stop"

$projectClasspath = "$env:GITHUB_WORKSPACE\protocol\build\classes\java\main;$env:GITHUB_WORKSPACE\transport-bctls\build\classes\java\main"
$compiledClasses = Join-Path $env:RUNNER_TEMP "compiled wolfssl spike classes"
$bundle = Join-Path $env:RUNNER_TEMP "relocated wolfssl proof bundle"
$nativeDirectory = Join-Path $bundle "native"
$libraryDirectory = Join-Path $bundle "lib"
$classesDirectory = Join-Path $bundle "classes"

Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $compiledClasses, $bundle
New-Item -ItemType Directory -Force -Path $compiledClasses, $nativeDirectory, $libraryDirectory, $classesDirectory | Out-Null

javac `
  -Xlint:all `
  -Werror `
  -classpath "$env:WOLFSSLJNI_JAR;$projectClasspath" `
  -d $compiledClasses `
  .github\spikes\wolfssl\PskDtls13Loopback.java `
  .github\spikes\wolfssl\PskDtls13NegativeMatrix.java
if ($LASTEXITCODE -ne 0) {
  throw "Windows relocation proof compilation failed"
}

Copy-Item -LiteralPath $env:WOLFSSL_DLL -Destination (Join-Path $nativeDirectory "wolfssl.dll")
Copy-Item -LiteralPath $env:WOLFSSLJNI_DLL -Destination (Join-Path $nativeDirectory "wolfssljni.dll")
Copy-Item -LiteralPath $env:WOLFSSLJNI_JAR -Destination (Join-Path $libraryDirectory "wolfssl-jsse.jar")

foreach ($source in @(
  (Join-Path $env:GITHUB_WORKSPACE "protocol\build\classes\java\main"),
  (Join-Path $env:GITHUB_WORKSPACE "transport-bctls\build\classes\java\main"),
  $compiledClasses
)) {
  if (-not (Test-Path $source)) {
    throw "Required class directory is missing"
  }
  Copy-Item -Path (Join-Path $source "*") -Destination $classesDirectory -Recurse -Force
}

$relocatedJar = Join-Path $libraryDirectory "wolfssl-jsse.jar"
$javaExecutable = Join-Path $env:JAVA_HOME "bin\java.exe"
$runtimeClasspath = "$classesDirectory;$relocatedJar"
$originalBuildMarkers = @($env:WOLFSSLJNI_DLL_DIR, $env:WOLFSSL_DLL_DIR, $env:WOLFSSLJNI_JAR)
foreach ($marker in $originalBuildMarkers) {
  if ($runtimeClasspath.Contains($marker, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Relocated runtime classpath still references an original build path"
  }
}

$env:PATH = "$nativeDirectory;$env:SystemRoot\System32;$env:SystemRoot"
$loopbackReport = Join-Path $env:GITHUB_WORKSPACE "wolfssl-dtls13-windows-loopback-report.txt"
& $javaExecutable `
  "-Djava.library.path=$nativeDirectory" `
  -classpath $runtimeClasspath `
  PskDtls13Loopback | Tee-Object -FilePath $loopbackReport
if ($LASTEXITCODE -ne 0) {
  throw "Relocated Windows DTLS 1.3 loopback failed"
}

& $javaExecutable `
  "-Djava.library.path=$nativeDirectory" `
  -classpath $runtimeClasspath `
  PskDtls13NegativeMatrix | Tee-Object -FilePath $loopbackReport -Append
if ($LASTEXITCODE -ne 0) {
  throw "Relocated Windows DTLS 1.3 negative matrix failed"
}

$proofOutput = Get-Content $loopbackReport -Raw
if ($proofOutput -notmatch 'loopback passed; replay rejected; secrets redacted') {
  throw "Relocated loopback did not emit the expected redacted proof"
}
if ($proofOutput -notmatch 'negative matrix passed; unknown expired bad-psk parallel-replay downgrade cleanup; secrets redacted') {
  throw "Relocated negative matrix did not emit the expected redacted proof"
}

$missingNativeDirectory = Join-Path $bundle "missing native"
$corruptNativeDirectory = Join-Path $bundle "corrupt native"
New-Item -ItemType Directory -Force -Path $missingNativeDirectory, $corruptNativeDirectory | Out-Null

$originalPath = $env:PATH
try {
  $env:PATH = "$env:SystemRoot\System32;$env:SystemRoot"
  $missingLog = Join-Path $env:RUNNER_TEMP "wolfssl-missing-native.log"
  & $javaExecutable `
    "-Djava.library.path=$missingNativeDirectory" `
    -classpath $runtimeClasspath `
    PskDtls13Loopback *> $missingLog
  $missingExit = $LASTEXITCODE

  Set-Content `
    -Path (Join-Path $corruptNativeDirectory "wolfssljni.dll") `
    -Value "not-a-native-library" `
    -Encoding ascii
  $corruptLog = Join-Path $env:RUNNER_TEMP "wolfssl-corrupt-native.log"
  & $javaExecutable `
    "-Djava.library.path=$corruptNativeDirectory" `
    -classpath $runtimeClasspath `
    PskDtls13Loopback *> $corruptLog
  $corruptExit = $LASTEXITCODE
} finally {
  $env:PATH = $originalPath
}

if ($missingExit -eq 0) {
  throw "Missing native provider did not fail closed"
}
if ($corruptExit -eq 0) {
  throw "Corrupt native provider did not fail closed"
}

$relocationReport = Join-Path $env:GITHUB_WORKSPACE "wolfssl-dtls13-windows-relocation-report.txt"
$binaryFiles = @(
  (Join-Path $nativeDirectory "wolfssl.dll"),
  (Join-Path $nativeDirectory "wolfssljni.dll"),
  $relocatedJar
)
$output = @(
  "bundle_name=$([IO.Path]::GetFileName($bundle))",
  "bundle_contains_spaces=$($bundle.Contains(' '))",
  "native_directory=$([IO.Path]::GetFileName($nativeDirectory))",
  "library_directory=$([IO.Path]::GetFileName($libraryDirectory))",
  "class_files=$((Get-ChildItem $classesDirectory -Recurse -File -Filter '*.class').Count)",
  "runtime_classpath_uses_relocated_bundle=true",
  "java_library_path_uses_relocated_bundle=true",
  "missing_native_fails_closed=true",
  "corrupt_native_fails_closed=true",
  "native_failure_logs_uploaded=false",
  "binary_artifacts_uploaded=false",
  "",
  "== Relocated binary checksums =="
)
foreach ($path in $binaryFiles) {
  $item = Get-Item $path
  $hash = Get-FileHash -Algorithm SHA256 $path
  $output += "$($hash.Hash.ToLowerInvariant())  $($item.Name)  bytes=$($item.Length)"
}
$output | Set-Content -Path $relocationReport -Encoding utf8
Get-Content $relocationReport

if ((Get-Content $relocationReport -Raw) -notmatch 'bundle_contains_spaces=True') {
  throw "Relocation report did not prove a path containing spaces"
}

exit 0
