@echo off
setlocal EnableExtensions
cd /d "%~dp0"

call "tools\windows\require-java-21.bat"
set "EXIT_CODE=%ERRORLEVEL%"
if not "%EXIT_CODE%"=="0" goto :failure

if not exist "data" mkdir "data"
if errorlevel 1 (
    set "EXIT_CODE=14"
    echo [BLAD] Nie mozna utworzyc katalogu danych: "%~dp0data".
    goto :failure
)

call "bin\sunderfront-client.bat" --data-dir "%~dp0data" %*
set "EXIT_CODE=%ERRORLEVEL%"
if "%EXIT_CODE%"=="0" exit /b 0

echo [BLAD] Klient zakonczyl dzialanie z kodem %EXIT_CODE%.

:failure
if not defined EXIT_CODE set "EXIT_CODE=1"
echo.
echo Sprawdz README-PL.txt oraz komunikaty powyzej.
if /i not "%SUNDERFRONT_NO_PAUSE%"=="1" pause
exit /b %EXIT_CODE%
