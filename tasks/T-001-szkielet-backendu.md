---
id: T-001
epic: E0
title: Szkielet backendu i Makefile
status: done
depends_on: []
manual_review: true
---

## Cel
Uruchamialna aplikacja Spring Boot i wspólne komendy, na których opiera się reszta projektu.

## Zakres
- projekt Maven z wrapperem w `backend/`, Java 21, przypięta aktualna stabilna wersja Spring Boot
- pakiet główny `pl.bankzartow`
- endpoint healthcheck `/actuator/health`
- `Makefile` z celami `test-fast` i `test` (na razie tylko backend)
- `.gitignore` i `.env.example`

## Poza zakresem
- baza danych, frontend, logowanie

## Kryteria akceptacji
- [ ] `make test-fast` i `make test` kończą się sukcesem na czystym klonie z zainstalowanym JDK 21
- [ ] uruchomiona aplikacja zwraca status `UP` pod `/actuator/health`
- [ ] wersje Javy i Spring Boot są przypięte w `pom.xml`

## Testy
- test jednostkowy przykładowej klasy w `common`
- test sprawdzający odpowiedź healthchecka

## Blokada
