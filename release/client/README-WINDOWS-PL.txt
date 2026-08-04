SUNDERFRONT 0.1.0-alpha.5 — INTERAKTYWNE LOBBY M2, WINDOWS X64
================================================================

Ta wersja alpha prowadzi od menu przez bezpieczne Direct Connect i autorytatywne
lobby czterech drużyn do minimalnej sceny 3D PREPARATION. Nie jest jeszcze pełnym
meczem The Walls.

URUCHOMIENIE
------------

1. Rozpakuj całe archiwum do zwykłego katalogu z prawem zapisu.
2. Uruchom plik Sunderfront.exe.
3. Nie instaluj osobnej Javy. Wymagany 64-bitowy runtime Java 21 znajduje się
   w katalogu runtime tej paczki.
4. Wybierz Graj i wpisz adres serwera, np. 127.0.0.1:27420.
5. Użyj handle złożonego z 3–24 małych liter, cyfr lub znaku podkreślenia.
6. Przy pierwszym połączeniu porównaj fingerprint z plikiem
   server-fingerprint.txt przekazanym przez operatora serwera. Dopiero po zgodności
   wybierz zaufanie i ponowne połączenie.

DANE GRACZA
-----------

Sunderfront.exe używa przenośnego katalogu data obok pliku wykonywalnego.
Znajduje się tam prywatna tożsamość gracza i zapamiętane fingerprinty serwerów.
Nie udostępniaj jego zawartości. Kopia całego katalogu aplikacji zachowuje tę
samą tożsamość gracza. Dwa jednoczesne klienty testowe uruchamiaj z dwóch
oddzielnie rozpakowanych katalogów.

Operator serwera powinien użyć kompletnego archiwum
sunderfront-server-0.1.0-alpha.5.zip. Nie wolno kopiować samych numerowanych
skryptów serwera do starszego katalogu bin/lib.

TEST M2
-------

Domyślny serwer wymaga dwóch gotowych graczy w co najmniej dwóch reprezentowanych
drużynach.

1. Uruchom dwa klienty z oddzielnych katalogów i połącz je z tym samym
   zweryfikowanym serwerem.
2. Wybierz różne drużyny i ustaw obu graczy jako gotowych.
3. Sprawdź, czy oba klienty pokazują tę samą wartość countdownu.
4. Wyłącz gotowość jednego gracza przed zerem. Odliczanie musi zostać anulowane
   w obu klientach. Ponownie włącz gotowość.
5. Po dojściu nowego countdownu do zera oba klienty muszą dokładnie raz wejść do
   PREPARATION i pojawić się na autorytatywnych spawnach swoich drużyn.
6. Kliknij scenę albo naciśnij Enter, aby przejąć kursor. Sprawdź ruch WASD,
   poziomy obrót, ograniczony pitch, kolizję ze sceną, oddanie kursora przez Esc
   oraz ponowne przejęcie.

STRUKTURA PACZKI
----------------

- Sunderfront.exe — normalny punkt wejścia;
- runtime — prywatny, ograniczony runtime Java 21 wymagany przez aplikację;
- app — biblioteki i konfiguracja launchera;
- assets — przypięty manifest assetów;
- README.md / README-PL.txt — instrukcje i procedura testu M2;
- ICON-LICENSE.md — pochodzenie ikony.

Nie uruchamiaj plików z runtime ręcznie i nie przenoś pojedynczego pliku EXE bez
pozostałych katalogów. Przenosić lub kopiować należy cały rozpakowany katalog.

BEZPIECZEŃSTWO I OGRANICZENIA
-----------------------------

Ta alpha nie ma wydobycia, budowania, craftingu, klas, ekwipunku, otwierania
ścian, walki, deathmatchu, wyników, kolejnej rundy, autorytatywnej replikacji
ruchu, reconnectu, listy serwerów, relay/NAT traversal, finalnych assetów,
animacji, audio, instalatora, podpisu Authenticode ani auto-update. Windows może
pokazać ostrzeżenie dla nowego, niepodpisanego pliku. Pobieraj wydanie tylko z
repozytorium projektu i zweryfikuj SHA256SUMS.
