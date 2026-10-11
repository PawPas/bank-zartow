---
id: T-007
epic: E0
title: Przygotowanie wdrożenia na Railway
status: todo
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
