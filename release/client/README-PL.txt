SUNDERFRONT 0.1.0-alpha.2 — KLIENT WINDOWS
============================================

To jest techniczna wersja alpha sprawdzająca bezpieczne połączenie bezpośrednie
i wejście do minimalnego lobby. Nie zawiera jeszcze właściwej rozgrywki The Walls.

PIERWSZE URUCHOMIENIE
--------------------

1. Rozpakuj całe archiwum do zwykłego katalogu z prawem zapisu.
2. Zainstaluj 64-bitową Java 21, jeżeli nie jest jeszcze dostępna.
3. Kliknij dwukrotnie URUCHOM_KLIENTA.bat w katalogu głównym paczki.
4. Wybierz Play i wpisz adres serwera, np. 127.0.0.1:27420.
5. Użyj handle złożonego z 3–24 małych liter, cyfr lub znaku podkreślenia.
6. Przy pierwszym połączeniu porównaj fingerprint pokazany przez klienta z
   plikiem server-fingerprint.txt przekazanym przez operatora serwera. Dopiero
   potem wybierz Trust and reconnect.

KATALOG DANYCH
--------------

Launcher używa przenośnego katalogu data obok siebie. Znajduje się tam prywatna
tożsamość gracza i zapamiętane fingerprinty serwerów. Nie udostępniaj jego
zawartości. Kopia katalogu data zachowuje tę samą tożsamość gracza.

CO OZNACZAJĄ KATALOGI
---------------------

- bin — techniczne launchery JVM używane przez skrypty i operatorów;
- lib — biblioteki wymagane do uruchomienia gry, a nie kod źródłowy projektu;
- assets — przypięty manifest assetów; w tej alphie jest celowo pusty;
- tools — narzędzia diagnostyczne i release smoke, nie główna aplikacja;
- data — prywatne dane tworzone po pierwszym uruchomieniu.

ROZWIĄZYWANIE PROBLEMÓW
-----------------------

- Brak lub zła Java: uruchom ponownie URUCHOM_KLIENTA.bat i przeczytaj
  komunikat pozostawiony w oknie konsoli.
- Połączenie z localhost nie działa: operator serwera powinien uruchomić
  1_GENERUJ_CREDENTIALS.bat, a następnie 2_URUCHOM_SERWER.bat. Samo uruchomienie
  bin\sunderfront-server.bat bez konfiguracji startuje proces z wyłączonym TLS
  i lobby, do którego klient nie może się połączyć.
- Połączenie z innego komputera: użyj adresu LAN serwera i zezwól zaporze Windows
  na przychodzący TCP port 27420. Ta alpha nie omija NAT ani CGNAT.
- Zmieniony fingerprint serwera jest blokowany celowo. Nie usuwaj zaufania bez
  potwierdzenia operatora.

OGRANICZENIA
------------

Brak gameplayu, mapy, drużyn, walki, realtime UDP/DTLS, reconnectu, publicznej
listy serwerów, finalnych assetów, audio, automatycznej aktualizacji, pliku .exe
i podpisanego instalatora. Obecna paczka wymaga osobno zainstalowanej Java 21.
