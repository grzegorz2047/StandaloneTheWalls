SUNDERFRONT 0.1.0-alpha.2 — SERWER WINDOWS
============================================

To jest dedykowany serwer technicznej wersji Direct Connect Alpha. Nie zawiera
jeszcze właściwej rozgrywki The Walls.

PIERWSZE URUCHOMIENIE
--------------------

1. Rozpakuj całe archiwum do zwykłego katalogu z prawem zapisu.
2. Zainstaluj 64-bitową Java 21, jeżeli nie jest jeszcze dostępna.
3. Kliknij dwukrotnie 1_GENERUJ_CREDENTIALS.bat dokładnie jeden raz.
4. Zapisz kopię katalogów credentials i data w bezpiecznym miejscu.
5. Kliknij dwukrotnie 2_URUCHOM_SERWER.bat.
6. Przekaż graczom publiczny fingerprint z pliku
   credentials\server-fingerprint.txt. Nigdy nie przekazuj prywatnego klucza.

Launcher numer 2 zawsze przekazuje pełną konfigurację serwera, identity i TLS.
Dzięki temu domyślny start otwiera reliable TLS i minimal lobby. Uruchomienie
samego bin\sunderfront-server.bat bez argumentów jest trybem technicznym z
wyłączoną siecią i nie przyjmie klienta Direct Connect.

PORTY I ZAPORA
--------------

- Domyślny reliable port klienta to TCP 27420.
- Port 27421 jest zarezerwowany dla przyszłego realtime transportu i w tej alphie
  nie zapewnia rozgrywki.
- Dla LAN zezwól zaporze Windows na przychodzące połączenia TCP 27420.
- Dla Internetu potrzebne może być przekierowanie TCP 27420 na routerze.
- Ta alpha nie zapewnia relay, NAT traversal ani obejścia CGNAT.

DANE I SEKRETY
--------------

- credentials\server-ed25519-key.pk8 — tajny prywatny klucz serwera;
- credentials\server-ed25519-certificate.der — publiczny certyfikat;
- credentials\registry-trust-roots.hex — publiczny root używany przez LOCAL_TOFU;
- credentials\server-fingerprint.txt — publiczny fingerprint dla graczy;
- data\identity.sqlite — lokalne przypięcia handle i bany;
- data\registry.sfrb — opcjonalny cache rejestru.

Nie publikuj prywatnego klucza, bazy SQLite ani całego katalogu data. Utrata
prywatnego klucza zmienia kryptograficzną tożsamość serwera i wywoła ostrzeżenie
u powracających graczy.

CO OZNACZAJĄ KATALOGI
---------------------

- bin — techniczne launchery JVM używane przez skrypty i operatorów;
- lib — biblioteki wymagane do uruchomienia serwera, a nie kod źródłowy projektu;
- config — jawne konfiguracje procesu, identity i TLS;
- credentials — lokalnie generowane klucze i publiczny fingerprint;
- data — prywatny stan runtime;
- tools — wewnętrzne skrypty pomocnicze paczki.

ROZWIĄZYWANIE PROBLEMÓW
-----------------------

- Brak lub zła Java: uruchom ponownie odpowiedni numerowany skrypt i przeczytaj
  komunikat pozostawiony w oknie konsoli.
- Brak credentials: uruchom najpierw 1_GENERUJ_CREDENTIALS.bat. Generator celowo
  nie nadpisuje istniejących plików.
- Klient widzi Połączenie nieudane: sprawdź, czy serwer został uruchomiony przez
  2_URUCHOM_SERWER.bat i czy TCP 27420 nie jest blokowany. Log z tekstem
  reliable TLS disabled albo minimal lobby disabled oznacza uruchomienie złego,
  technicznego launchera bez wymaganych argumentów.
- Konfigurację można sprawdzić bez uruchamiania listenera poleceniem:
  2_URUCHOM_SERWER.bat --validate-config

OGRANICZENIA
------------

Brak gameplayu, mapy, drużyn, walki, realtime UDP/DTLS, reconnectu, publicznej
listy serwerów, zdalnej administracji, automatycznej rotacji certyfikatu,
dołączonego runtime Java i podpisanego instalatora.
