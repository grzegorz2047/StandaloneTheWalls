@echo off
setlocal EnableExtensions
cd /d "%~dp0"

call :inspect_credentials
if "%CREDENTIAL_PRESENT%"=="0" goto :generate
if "%CREDENTIAL_PRESENT%"=="4" if "%CREDENTIAL_READY%"=="4" goto :already_complete
goto :partial_credentials

:generate
call "tools\windows\require-java-21.bat"
set "EXIT_CODE=%ERRORLEVEL%"
if not "%EXIT_CODE%"=="0" goto :failure

call "bin\sunderfront-server-credentials.bat" --output "%~dp0credentials" %*
set "EXIT_CODE=%ERRORLEVEL%"
if not "%EXIT_CODE%"=="0" (
    echo [BLAD] Nie udalo sie wygenerowac credentials. Kod: %EXIT_CODE%.
    goto :failure
)

call :inspect_credentials
if not "%CREDENTIAL_PRESENT%"=="4" goto :incomplete_after_generation
if not "%CREDENTIAL_READY%"=="4" goto :incomplete_after_generation

echo.
echo [OK] Credentials zostaly wygenerowane bez nadpisywania istniejacych plikow.
echo Publiczny fingerprint serwera:
type "credentials\server-fingerprint.txt"
echo.
echo Nastepny krok: uruchom 2_URUCHOM_SERWER.bat.
goto :success

:already_complete
echo [OK] Kompletny zestaw credentials serwera juz istnieje.
echo Nie wygenerowano nowych kluczy i nie zmieniono tozsamosci serwera.
echo Publiczny fingerprint serwera:
type "credentials\server-fingerprint.txt"
echo.
echo Nastepny krok: uruchom 2_URUCHOM_SERWER.bat.
goto :success

:partial_credentials
set "EXIT_CODE=16"
echo [BLAD] Katalog credentials zawiera niekompletny albo pusty zestaw plikow.
echo Brakujace, puste albo nieprawidlowe sciezki:
call :report_unready "credentials\server-ed25519-key.pk8"
call :report_unready "credentials\server-ed25519-certificate.der"
call :report_unready "credentials\registry-trust-roots.hex"
call :report_unready "credentials\server-fingerprint.txt"
echo.
echo Niczego nie wygenerowano, nie usunieto ani nie nadpisano.
echo Najpierw wykonaj kopie calego katalogu credentials.
echo Jezeli masz kompletna kopie, przywroc caly zestaw razem.
echo Nowy pusty katalog wygeneruje nowa tozsamosc serwera i ostrzeze powracajacych graczy.
goto :failure

:incomplete_after_generation
set "EXIT_CODE=17"
echo [BLAD] Generator zakonczyl sie bez kompletnego zestawu credentials.
echo Niczego nie usuwaj. Wykonaj kopie katalogu credentials i sprawdz README-PL.txt.
goto :failure

:success
if /i not "%SUNDERFRONT_NO_PAUSE%"=="1" pause
exit /b 0

:failure
if not defined EXIT_CODE set "EXIT_CODE=1"
echo.
echo Sprawdz README-PL.txt oraz komunikaty powyzej.
if /i not "%SUNDERFRONT_NO_PAUSE%"=="1" pause
exit /b %EXIT_CODE%

:inspect_credentials
set "CREDENTIAL_PRESENT=0"
set "CREDENTIAL_READY=0"
call :inspect_one "credentials\server-ed25519-key.pk8"
call :inspect_one "credentials\server-ed25519-certificate.der"
call :inspect_one "credentials\registry-trust-roots.hex"
call :inspect_one "credentials\server-fingerprint.txt"
exit /b 0

:inspect_one
if not exist "%~1" exit /b 0
set /a CREDENTIAL_PRESENT+=1
if exist "%~1\NUL" exit /b 0
for %%F in ("%~1") do if %%~zF GTR 0 set /a CREDENTIAL_READY+=1
exit /b 0

:report_unready
if not exist "%~1" (
    echo   - %~1 ^(brak^)
    exit /b 0
)
if exist "%~1\NUL" (
    echo   - %~1 ^(to jest katalog, nie plik^)
    exit /b 0
)
for %%F in ("%~1") do if %%~zF LEQ 0 (
    echo   - %~1 ^(plik jest pusty^)
    exit /b 0
)
exit /b 0
