@echo off
setlocal EnableExtensions

set "JAVA_EXE="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_EXE (
    for %%J in (java.exe) do set "JAVA_EXE=%%~$PATH:J"
)

if not defined JAVA_EXE (
    echo [BLAD] Nie znaleziono Java 21.
    echo Zainstaluj 64-bitowy runtime Java 21 i uruchom ten plik ponownie.
    exit /b 10
)

set "CHECK_FILE=%TEMP%\sunderfront-java-check-%RANDOM%-%RANDOM%.txt"
"%JAVA_EXE%" -XshowSettings:properties -version > "%CHECK_FILE%" 2>&1
if errorlevel 1 (
    del /q "%CHECK_FILE%" >nul 2>&1
    echo [BLAD] Nie mozna uruchomic Java z: "%JAVA_EXE%".
    echo Sprawdz JAVA_HOME i zmienna PATH.
    exit /b 11
)

set "JAVA_VERSION="
set "JAVA_DATA_MODEL="
set "JAVA_ARCH="
for /f "tokens=3" %%V in ('findstr /c:"java.specification.version =" "%CHECK_FILE%"') do set "JAVA_VERSION=%%V"
for /f "tokens=3" %%V in ('findstr /c:"sun.arch.data.model =" "%CHECK_FILE%"') do set "JAVA_DATA_MODEL=%%V"
for /f "tokens=3" %%V in ('findstr /c:"os.arch =" "%CHECK_FILE%"') do set "JAVA_ARCH=%%V"
del /q "%CHECK_FILE%" >nul 2>&1

if not "%JAVA_VERSION%"=="21" (
    if not defined JAVA_VERSION set "JAVA_VERSION=nieznana"
    echo [BLAD] Wykryto Java %JAVA_VERSION%, ale Sunderfront wymaga Java 21.
    echo Ustaw JAVA_HOME na 64-bitowa Java 21 albo popraw zmienna PATH.
    exit /b 12
)

if "%JAVA_DATA_MODEL%"=="64" exit /b 0
if /i "%JAVA_ARCH%"=="amd64" exit /b 0
if /i "%JAVA_ARCH%"=="x86_64" exit /b 0
if /i "%JAVA_ARCH%"=="aarch64" exit /b 0

if not defined JAVA_ARCH set "JAVA_ARCH=nieznana"
echo [BLAD] Wykryta Java 21 nie jest 64-bitowa ^(architektura: %JAVA_ARCH%^).
echo Zainstaluj 64-bitowy runtime Java 21 i uruchom ten plik ponownie.
exit /b 13
