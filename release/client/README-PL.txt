SUNDERFRONT 0.1.0-alpha.5 — INTERAKTYWNE LOBBY M2
==================================================

Ta paczka jest techniczną, przenośną dystrybucją JVM prowadzącą od menu,
przez autorytatywne lobby, do minimalnej sceny 3D PREPARATION. Wymaga osobno
zainstalowanej 64-bitowej Java 21.

Zwykły użytkownik Windows x64 powinien pobrać paczkę:

sunderfront-client-windows-x64-0.1.0-alpha.5.zip

Zawiera ona Sunderfront.exe oraz własny ograniczony runtime Java 21 i nie wymaga
instalowania Javy.

URUCHOMIENIE TEJ PACZKI JVM NA WINDOWS
--------------------------------------

1. Rozpakuj całe archiwum do zwykłego katalogu z prawem zapisu.
2. Zainstaluj 64-bitową Java 21, jeżeli nie jest jeszcze dostępna.
3. Kliknij dwukrotnie URUCHOM_KLIENTA.bat w katalogu głównym paczki.
4. Wybierz Graj i wpisz adres serwera, np. 127.0.0.1:27420.
5. Użyj handle złożonego z 3–24 małych liter, cyfr lub znaku podkreślenia.
6. Przy pierwszym połączeniu porównaj fingerprint pokazany przez klienta z
   plikiem server-fingerprint.txt przekazanym przez operatora serwera. Dopiero
   potem wybierz zaufanie i ponowne połączenie.

KATALOG DANYCH
--------------

Launcher używa przenośnego katalogu data obok siebie. Znajduje się tam prywatna
tożsamość gracza i zapamiętane fingerprinty serwerów. Nie udostępniaj jego
zawartości. Kopia katalogu data zachowuje tę samą tożsamość gracza. Dwa
jednocześnie uruchomione klienty testowe muszą używać oddzielnych rozpakowanych
katalogów i oddzielnych katalogów data.

TEST M2: LOBBY I PREPARATION
----------------------------

Domyślna konfiguracja serwera wymaga dwóch gotowych graczy w co najmniej dwóch
reprezentowanych drużynach.

1. Uruchom dwa klienty z dwóch oddzielnych katalogów i połącz je z tym samym
   serwerem.
2. Zweryfikuj fingerprint serwera w obu klientach.
3. W lobby wybierz dla graczy różne drużyny i ustaw obu jako gotowych.
4. Oba klienty muszą pokazać tę samą wartość autorytatywnego odliczania.
5. Przed zerem wyłącz gotowość jednego gracza. Odliczanie musi zostać anulowane
   w obu klientach.
6. Ponownie ustaw gotowość i pozwól pełnemu odliczaniu dojść do zera.
7. Oba klienty muszą dokładnie raz przejść bez restartu do PREPARATION, załadować
   zweryfikowaną minimalną scenę i pojawić się na spawnie właściwej drużyny.
8. Kliknij scenę albo naciśnij Enter, aby przejąć kursor. Sprawdź ruch WASD,
   poziome i pionowe obracanie kamery, ograniczenie pitchu oraz kolizję ze sceną.
9. Naciśnij Esc, aby oddać kursor, a następnie przejmij go ponownie.

Scena preparation celowo jest minimalna. Potwierdza weryfikację mapy, autorytatywny
spawn, lifecycle kamery i ruchu oraz bounded promień ciała gracza. Nie jest jeszcze
pełnym meczem The Walls.

CO OZNACZAJĄ KATALOGI
---------------------

- bin — techniczne launchery JVM używane przez skrypty i operatorów;
- lib — biblioteki wymagane do uruchomienia gry, a nie kod źródłowy projektu;
- assets — przypięty manifest assetów;
- tools — narzędzia diagnostyczne i release smoke, nie główna aplikacja;
- data — prywatne dane tworzone po pierwszym uruchomieniu.

ROZWIĄZYWANIE PROBLEMÓW
-----------------------

- Brak lub zła Java: użyj zalecanej paczki Windows x64 albo uruchom ponownie
  URUCHOM_KLIENTA.bat i przeczytaj komunikat pozostawiony w oknie konsoli.
- Połączenie z localhost nie działa: operator serwera powinien użyć kompletnej
  paczki serwera alpha.5, uruchomić 1_GENERUJ_CREDENTIALS.bat, a następnie
  2_URUCHOM_SERWER.bat.
- Countdown nie startuje: każdy połączony gracz musi mieć drużynę, być gotowy,
  a co najmniej dwie drużyny muszą być reprezentowane. Klient pokazuje jawny
  stan i odrzucenia serwera; nie zgaduje lokalnie.
- Połączenie z innego komputera: użyj adresu LAN serwera i zezwól zaporze Windows
  na przychodzący TCP port 27420. Ta alpha nie omija NAT ani CGNAT.
- Zmieniony fingerprint serwera jest blokowany celowo. Nie usuwaj zaufania bez
  potwierdzenia operatora.

OGRANICZENIA
------------

Brak wydobycia, budowania, craftingu, klas, ekwipunku, otwierania ścian, walki,
deathmatchu, wyników i restartu kolejnej rundy. Ruch graczy nie jest jeszcze
replikowany jako autorytatywny realtime world snapshot. Brak reconnectu, publicznej
listy serwerów, relay/NAT traversal, finalnych modeli i tekstur, animacji, audio,
automatycznej aktualizacji i podpisanego instalatora. Tylko osobna paczka Windows
x64 zawiera Sunderfront.exe i własny runtime Java 21.
