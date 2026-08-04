SUNDERFRONT 0.1.0-alpha.5 — SERWER INTERAKTYWNEGO LOBBY M2
============================================================

To jest dedykowany serwer wersji M2: bezpieczne Direct Connect, autorytatywne
lobby czterech drużyn, ready, countdown oraz przejście do minimalnej sceny 3D
PREPARATION z autorytatywnymi spawnami drużyn. Serwer wymaga osobno
zainstalowanej 64-bitowej Java 21. Paczka z Sunderfront.exe i własnym runtime
dotyczy tylko klienta Windows x64.

PIERWSZE URUCHOMIENIE
--------------------

1. Rozpakuj całe archiwum do zwykłego, nowego katalogu z prawem zapisu.
2. Zainstaluj 64-bitową Java 21, jeżeli nie jest jeszcze dostępna.
3. Kliknij dwukrotnie 1_GENERUJ_CREDENTIALS.bat.
4. Zapisz kopię katalogów credentials i data w bezpiecznym miejscu.
5. Kliknij dwukrotnie 2_URUCHOM_SERWER.bat.
6. Przekaż graczom publiczny fingerprint z pliku
   credentials\server-fingerprint.txt. Nigdy nie przekazuj prywatnego klucza.

Nie kopiuj samych numerowanych skryptów do katalogu starszej wersji. Pliki
1_GENERUJ_CREDENTIALS.bat, 2_URUCHOM_SERWER.bat, bin, lib, config i tools muszą
pochodzić z tego samego kompletnego archiwum. W przeciwnym razie launcher przerwie
pracę jako niekompletna albo mieszana paczka.

Ponowne uruchomienie kroku 1 przy kompletnym zestawie jest bezpieczne: launcher
nie generuje nowych kluczy, nie zmienia tożsamości serwera, pokazuje istniejący
publiczny fingerprint i kieruje do kroku 2. Generator nigdy nie nadpisuje żadnego
pliku credentials. Zestaw częściowy albo zawierający pusty wymagany plik kończy
się fail-closed bez tworzenia, usuwania lub naprawiania plików.

Launcher numer 2 zawsze przekazuje pełną konfigurację serwera, identity i TLS.
Dzięki temu domyślny start otwiera reliable TLS oraz autorytatywne lobby.
Uruchomienie samego bin\sunderfront-server.bat bez argumentów jest trybem
technicznym z wyłączoną siecią i nie przyjmie klienta Direct Connect.

TEST M2: AUTORYTATYWNE LOBBY
----------------------------

Standardowa konfiguracja obsługuje maksymalnie 40 graczy, maksymalnie 10 na
drużynę i wymaga minimum dwóch gotowych graczy w co najmniej dwóch
reprezentowanych drużynach.

1. Uruchom serwer i dwa klienty z oddzielnymi tożsamościami.
2. W obu klientach potwierdź publiczny fingerprint serwera.
3. Przydziel graczy do różnych drużyn i ustaw obu jako gotowych.
4. Serwer musi opublikować jedno odliczanie liczone fixed-tickami, a oba klienty
   muszą pokazywać tę samą wartość.
5. Przed zerem wyłącz gotowość albo zmień drużynę jednego gracza. Countdown musi
   zostać anulowany u wszystkich. Przywrócenie kompletnego gotowego lobby musi
   rozpocząć nowe pełne odliczanie.
6. Po zerze serwer musi dokładnie raz wejść do PREPARATION, zablokować dalsze
   zmiany drużyny i ready, załadować zweryfikowaną minimalną mapę i przekazać
   każdemu graczowi właściwy autorytatywny spawn drużyny.
7. Ruch widoczny w scenie preparation jest obecnie lokalną prezentacją klienta.
   Serwer nie publikuje jeszcze realtime snapshotów pozycji graczy.

AKTUALIZACJA ZE STARSZEJ ALPHY
-----------------------------

1. Rozpakuj całe archiwum sunderfront-server-0.1.0-alpha.5.zip do nowego pustego
   katalogu.
2. Wykonaj kopię całych katalogów credentials i data starego serwera.
3. Jeżeli stary katalog credentials zawiera wszystkie cztery niepuste pliki,
   skopiuj cały katalog jako jeden zestaw do paczki alpha.5.
4. Nie łącz pojedynczych plików z różnych uruchomień generatora.
5. Uruchom 1_GENERUJ_CREDENTIALS.bat. Kompletny zestaw zostanie zaakceptowany bez
   zmiany zawartości i hashy.
6. Uruchom 2_URUCHOM_SERWER.bat.

PORTY I ZAPORA
--------------

- Domyślny reliable port klienta to TCP 27420.
- Port 27421 jest zarezerwowany dla przyszłego realtime transportu i w tej alphie
  nie zapewnia replikacji ruchu.
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

NIEKOMPLETNY KATALOG CREDENTIALS
--------------------------------

Jeżeli krok 1 zgłasza niekompletny zestaw, launcher celowo niczego nie tworzy,
nie usuwa i nie nadpisuje. Najpierw wykonaj kopię całego katalogu credentials.
Następnie:

- jeżeli masz kompletną kopię, przywróć razem wszystkie cztery pliki;
- nie łącz plików pochodzących z różnych uruchomień generatora;
- pusty nowy katalog można wygenerować ponownie, ale stworzy on nową tożsamość
  serwera. Powracający gracze zobaczą wtedy ostrzeżenie o zmianie fingerprintu.

Nie usuwaj pojedynczego pliku z działającego zestawu i nie próbuj odtwarzać go
osobno. Klucz, certyfikat i fingerprint muszą pozostać jednym spójnym zestawem.

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

- Paczka niekompletna albo pliki z różnych wersji: rozpakuj całe archiwum serwera
  do nowego pustego katalogu i dopiero wtedy przenieś kompletny zapisany katalog
  credentials.
- Brak lub zła Java: uruchom ponownie odpowiedni numerowany skrypt i przeczytaj
  komunikat pozostawiony w oknie konsoli.
- Countdown nie startuje: każdy połączony gracz musi mieć drużynę i ready, a co
  najmniej dwie drużyny muszą być reprezentowane.
- Klient widzi Połączenie nieudane: sprawdź, czy serwer został uruchomiony przez
  2_URUCHOM_SERWER.bat i czy TCP 27420 nie jest blokowany.
- Konfigurację można sprawdzić bez uruchamiania listenera poleceniem:
  2_URUCHOM_SERWER.bat --validate-config

OGRANICZENIA
------------

Serwer zatrzymuje zaimplementowany lifecycle w minimalnym PREPARATION. Brak
wydobycia, budowania, craftingu, klas, ekwipunku, otwierania ścian, walki,
deathmatchu, wyników, kolejnej rundy, autorytatywnej replikacji ruchu, reconnectu,
publicznej listy serwerów, relay/NAT traversal, zdalnej administracji, finalnych
assetów i audio, automatycznej rotacji certyfikatu, dołączonego runtime serwera,
serwerowego pliku EXE i podpisanego instalatora.
