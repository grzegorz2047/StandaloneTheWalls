@echo off
setlocal EnableExtensions
cd /d "%~dp0"

call "tools\windows\require-java-21.bat"
set "EXIT_CODE=%ERRORLEVEL%"
if not "%EXIT_CODE%"=="0" goto :failure

call "bin\sunderfront-server-credentials.bat" --output "%~dp0credentials" %*
set "EXIT_CODE=%ERRORLEVEL%"
if not "%EXIT_CODE%"=="0" (
    echo [BLAD] Nie udalo sie wygenerowac credentials. Kod: %EXIT_CODE%.
    goto :failure
)

echo.
echo Credentials zostaly wygenerowane bez nadpisywania istniejacych plikow.
if exist "credentials\server-fingerprint.txt" (
    echo Publiczny fingerprint serwera:
    type "credentials\server-fingerprint.txt"
)
echo.
echo Nastepny krok: uruchom 2_URUCHOM_SERWER.bat.
if /i not "%SUNDERFRONT_NO_PAUSE%"=="1" pause
exit /b 0

:failure
if not defined EXIT_CODE set "EXIT_CODE=1"
echo.
echo Sprawdz README-PL.txt oraz komunikaty powyzej.
if /i not "%SUNDERFRONT_NO_PAUSE%"=="1" pause
exit /b %EXIT_CODE%
