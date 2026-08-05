# Wizualny playtest Sunderfront

Najkrótsza działająca ścieżka prowadzi przez opublikowaną wersję
[`v0.1.0-alpha.5`](https://github.com/grzegorz2047/StandaloneTheWalls/releases/tag/v0.1.0-alpha.5).
Nie wymaga budowania gry ze źródeł.

Ta alpha używa istniejącego stosu projektu zamiast równoległych implementacji:

- jMonkeyEngine 3.9.0-stable do renderowania sceny 3D;
- Gradle Application Plugin do dystrybucji klienta i serwera;
- Bouncy Castle TLS 1.3 do bezpiecznego Direct Connect;
- wersjonowanego i weryfikowanego `.twmap` jako źródła świata;
- autorytatywnego rosteru, ready, countdownu i przydziału spawnów po stronie serwera.

## Co pobrać

Z release `v0.1.0-alpha.5` pobierz:

1. `sunderfront-server-0.1.0-alpha.5.zip`;
2. `sunderfront-client-windows-x64-0.1.0-alpha.5.zip`.

Klient Windows x64 zawiera `Sunderfront.exe` oraz własny ograniczony runtime Java
21. Serwer wymaga osobno zainstalowanej 64-bitowej Java 21.

## Uruchomienie lokalne

### 1. Serwer

1. Rozpakuj całe archiwum serwera do nowego katalogu.
2. Uruchom `1_GENERUJ_CREDENTIALS.bat`.
3. Otwórz `credentials/server-fingerprint.txt` i zachowaj publiczny fingerprint.
4. Uruchom `2_URUCHOM_SERWER.bat`.

Nie udostępniaj pliku `credentials/server-ed25519-key.pk8` ani katalogu `data`.

### 2. Dwa klienty

Domyślna konfiguracja wymaga dwóch gotowych graczy w co najmniej dwóch drużynach.

1. Rozpakuj archiwum klienta dwa razy, do dwóch oddzielnych katalogów, na przykład
   `client-a` i `client-b`.
2. Uruchom `Sunderfront.exe` w obu katalogach.
3. Wybierz `Graj` i wpisz `127.0.0.1:27420`.
4. Użyj dwóch różnych handle, na przykład `demo_a` i `demo_b`.
5. Przy pierwszym połączeniu porównaj fingerprint klienta z
   `server-fingerprint.txt`, zaakceptuj zaufanie i połącz się ponownie.
6. W lobby wybierz dwie różne drużyny i ustaw obu graczy jako gotowych.
7. Pozwól autorytatywnemu countdownowi dojść do zera.

Oba klienty powinny dokładnie raz przejść bez restartu do sceny 3D
`PREPARATION` i pojawić się na spawnach przypisanych przez serwer.

## Sterowanie w świecie

- kliknięcie sceny lub `Enter` przejmuje kursor;
- `WASD` porusza lokalnym graczem;
- mysz obraca kamerę poziomo i pionowo;
- `Esc` zwalnia kursor;
- drugi `Esc` wraca do menu przez kontrolowane rozłączenie.

Ruch jest ograniczony do zweryfikowanego obszaru drużyny i sprawdzany względem
oddzielnej, niewidocznej geometrii kolizji.

## Co faktycznie jest generowane i weryfikowane

Minimalny świat nie jest ręcznie zapisanym binarnym assetem w zwykłej historii
Git. Projekt deterministycznie generuje kompletny bundle `minimal_preparation`
z czytelnych źródeł i sprawdza:

- manifest i pełny SHA-256 archiwum;
- GLB sceny wizualnej i osobny GLB kolizji;
- cztery obszary drużyn, ściany, podłoże i oświetlenie;
- 40 unikalnych spawnów, po 10 na drużynę;
- zgodność mapy po stronie serwera i klienta przed wejściem do sceny.

Serwer wybiera spawn z autorytatywnej drużyny. Klient nie zgaduje pozycji i nie
uruchamia świata przy błędnym bundle, hashu, wersji lub przydziale.

## Aktualne ograniczenia

To wizualny pionowy wycinek, nie pełny mecz. Nie ma jeszcze wydobycia, budowania,
craftingu, klas, ekwipunku, otwierania ścian, walki, wyników ani kolejnej rundy.
Ruch widoczny w `PREPARATION` jest lokalną prezentacją klienta i nie jest jeszcze
replikowany przez autorytatywne realtime snapshoty.

Kanał realtime pozostaje fail-closed. Alpha używa działającego reliable TLS 1.3
do połączenia, lobby i wejścia do świata; nie wykonuje cichego downgrade do DTLS
1.2 ani nie dołącza niezatwierdzonego natywnego providera.
