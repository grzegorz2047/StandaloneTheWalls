SUNDERFRONT 0.1.0-alpha.3 — KLIENT WINDOWS X64
================================================

To jest techniczna wersja alpha sprawdzająca bezpieczne połączenie bezpośrednie
i wejście do minimalnego lobby. Nie zawiera jeszcze właściwej rozgrywki The Walls.

URUCHOMIENIE
------------

1. Rozpakuj całe archiwum do zwykłego katalogu z prawem zapisu.
2. Uruchom plik Sunderfront.exe.
3. Nie instaluj osobnej Javy. Wymagany 64-bitowy runtime Java 21 znajduje się
   w katalogu runtime tej paczki.
4. Wybierz Play i wpisz adres serwera, np. 127.0.0.1:27420.
5. Użyj handle złożonego z 3–24 małych liter, cyfr lub znaku podkreślenia.
6. Przy pierwszym połączeniu porównaj fingerprint z plikiem
   server-fingerprint.txt przekazanym przez operatora serwera. Dopiero potem
   wybierz Trust and reconnect.

DANE GRACZA
-----------

Sunderfront.exe używa przenośnego katalogu data obok pliku wykonywalnego.
Znajduje się tam prywatna tożsamość gracza i zapamiętane fingerprinty serwerów.
Nie udostępniaj jego zawartości. Kopia całego katalogu aplikacji zachowuje tę
samą tożsamość gracza.

STRUKTURA PACZKI
----------------

- Sunderfront.exe — normalny punkt wejścia;
- runtime — prywatny, ograniczony runtime Java 21 wymagany przez aplikację;
- app — biblioteki i konfiguracja launchera;
- assets — przypięty manifest assetów;
- README.md / README-PL.txt — instrukcje;
- ICON-LICENSE.md — pochodzenie ikony.

Nie uruchamiaj plików z runtime ręcznie i nie przenoś pojedynczego pliku EXE bez
pozostałych katalogów. Przenosić lub kopiować należy cały rozpakowany katalog.

BEZPIECZEŃSTWO I OGRANICZENIA
-----------------------------

Ta alpha nie ma podpisu Authenticode, instalatora, auto-update, gameplayu,
mapy, drużyn, walki, dźwięku ani finalnej grafiki. Windows może pokazać
ostrzeżenie dla nowego, niepodpisanego pliku. Pobieraj wydanie tylko z repozytorium
projektu i zweryfikuj SHA256SUMS.
