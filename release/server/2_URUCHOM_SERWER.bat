@echo off
setlocal EnableExtensions
cd /d "%~dp0"

call "tools\windows\require-java-21.bat"
set "EXIT_CODE=%ERRORLEVEL%"
if not "%EXIT_CODE%"=="0" goto :failure

if not exist "credentials\server-ed25519-key.pk8" goto :missing_credentials
if not exist "credentials\server-ed25519-certificate.der" goto :missing_credentials
if not exist "credentials\registry-trust-roots.hex" goto :missing_credentials

call "bin\sunderfront-server.bat" ^
    --config "%~dp0config\server.properties" ^
    --identity-config "%~dp0config\identity.properties" ^
    --tls-config "%~dp0config\tls.properties" ^
    %*
set "EXIT_CODE=%ERRORLEVEL%"
if "%EXIT_CODE%"=="0" exit /b 0

echo [BLAD] Serwer zakonczyl dzialanie z kodem %EXIT_CODE%.
goto :failure

:missing_credentials
set "EXIT_CODE=15"
echo [BLAD] Brakuje lokalnych credentials serwera.
echo Najpierw uruchom 1_GENERUJ_CREDENTIALS.bat dokladnie jeden raz.

:failure
if not defined EXIT_CODE set "EXIT_CODE=1"
echo.
echo Sprawdz README-PL.txt oraz komunikaty powyzej.
if /i not "%SUNDERFRONT_NO_PAUSE%"=="1" pause
exit /b %EXIT_CODE%
