---
id: T-007
epic: E0
title: Przygotowanie wdrożenia na Railway
status: blocked
depends_on: [T-006]
manual_review: true
---

## Cel
Merge do `main` ma kończyć się działającą wersją pod publicznym adresem.

## Zakres
- plik konfiguracji Railway w repo: build z `Dockerfile`, healthcheck `/actuator/health`
- aplikacja czyta port i dane bazy ze zmiennych dostarczanych przez Railway
- `docs/DEPLOY.md`: kroki dla właściciela (projekt, usługa PostgreSQL, zmienne, podłączenie repo, kopie zapasowe bazy)
- pełna lista wymaganych zmiennych środowiskowych

## Poza zakresem
- tworzenie projektu i ustawianie zmiennych na Railway (wykonuje właściciel)
- własna domena, środowisko staging

## Kryteria akceptacji
- [ ] po wykonaniu kroków z `docs/DEPLOY.md` aplikacja odpowiada pod publicznym adresem Railway (potwierdza właściciel)
- [ ] bez zalogowania publiczny adres nie pokazuje żadnych danych
- [ ] nieudany healthcheck nowej wersji nie zastępuje działającej
- [ ] repozytorium nie zawiera żadnych wartości sekretów

## Testy
- test integracyjny: aplikacja startuje z konfiguracją w formacie zmiennych Railway

## Blokada

Przygotowanie wdrożenia jest ukończone i przetestowane lokalnie; blokada dotyczy
wyłącznie bramki akceptacyjnej, która jest zastrzeżona dla właściciela.

Zrobione i zweryfikowane lokalnie:
- `railway.json`: build z `Dockerfile`, healthcheck `/actuator/health`,
  `healthcheckTimeout` 300 s, `restartPolicyType` `ON_FAILURE`.
- Aplikacja czyta `PORT` i `SPRING_DATASOURCE_*` ze zmiennych środowiskowych
  (bez zmian w kodzie — potwierdzone testem).
- `docs/DEPLOY.md`: kroki dla właściciela (projekt, usługa PostgreSQL, zmienne,
  mapowanie bazy przez referencje Railway, podłączenie repo, kopie zapasowe) oraz
  pełna lista zmiennych środowiskowych.
- Test integracyjny `RailwayEnvironmentConfigIT`: aplikacja startuje z konfiguracją
  w formacie zmiennych Railway (`UPPER_SNAKE_CASE`), nasłuchuje na wstrzykniętym
  `PORT` i zwraca `/actuator/health` = `UP`.
- Kryterium „repozytorium nie zawiera żadnych wartości sekretów” spełnione
  (użyto wyłącznie efemerycznych fixture'ów testowych).
- `make test-fast` i `make test` (z Docker smoke) zielone.

Czego nie można zrobić bez właściciela (poza zakresem taska i SCOPE §8):
- utworzenie projektu i usług na Railway, ustawienie zmiennych, podłączenie repo,
  wykonanie deployu i potwierdzenie, że aplikacja odpowiada pod publicznym adresem
  (kryterium 1, oraz potwierdzenie na żywo kryteriów 2 i 3).

Agent nie ma dostępu do konta Railway ani do sekretów i nie wykonuje deployu.
Prośba do właściciela: wykonać kroki z `docs/DEPLOY.md`, potwierdzić działanie pod
publicznym adresem, a następnie zdecydować o merge (task ma `manual_review: true`) i
ustawić `status: done`.
