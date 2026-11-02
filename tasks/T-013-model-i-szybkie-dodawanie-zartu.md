---
id: T-013
epic: E1
title: Model żartu, szybkie dodawanie i lista
status: todo
depends_on: [T-006]
manual_review: true
---

## Cel
Dać minimalny rdzeń banku żartów: zapis żartu z telefonu samą treścią i prostą listę. To pierwszy pionowy wycinek E1, na którym opierają się kolejne taski.

## Zakres
- pakiet `joke`: encja `Joke` (`id`, `content`, `created_at`, `updated_at`, `version` jako `@Version`), repozytorium, serwis, kontroler zgodnie z `CONVENTIONS.md` (kontroler bez logiki, serwis bez HTTP, DTO jako rekordy)
- migracja Flyway tworząca tabelę `joke` (kolejny wolny numer `V<n>` w chwili implementacji; istniejących migracji się nie edytuje)
- `content` to kompletny oryginalny żart; wymagany i niepusty po normalizacji (wykorzystaj `common/TextNormalizer`)
- `POST /api/jokes {content}` → `201` z `JokeResponse` (`id`, `content`, `createdAt`, `version`)
- `GET /api/jokes` → lista skrótów (`JokeSummary`) posortowana malejąco po `createdAt`, bez filtrów
- optymistyczna wersja (`@Version`) obecna od początku, zwracana w DTO i stosowana spójnie przez kolejne taski
- DTO szczegółów/listy traktujemy jako rozszerzalne — kolejne taski dołożą status, tagi i alternatywy (nie deklarujemy kształtu DTO jako finalnego)
- frontend: widok listy (mobile-first, 360 px) oraz szybkie dodawanie przez moduł `frontend/src/api/client.ts`; teksty po polsku
- szybkie dodawanie mieści się w najwyżej dwóch dotknięciach poza wpisaniem treści (dotknięcie pola treści, dotknięcie „Dodaj”)

## Poza zakresem
- szczegóły, edycja i usuwanie (T-014)
- statusy (T-015), tagi (T-016), filtry i paginacja (T-017)
- puenty alternatywne (T-018, T-019)

## Kryteria akceptacji
- [ ] `POST /api/jokes` z niepustą treścią tworzy żart i zwraca `201` z `id`, treścią i `version`
- [ ] `POST /api/jokes` z treścią pustą lub samymi białymi znakami zwraca `400` w formacie `ProblemDetail` i nie tworzy żartu
- [ ] `GET /api/jokes` zwraca utworzone żarty w kolejności od najnowszego
- [ ] żądania do `/api/jokes` bez sesji zwracają `401` (zgodnie z T-006), a żądania modyfikujące są chronione CSRF
- [ ] żart zapisany w jednej transakcji jest odczytywalny w niezależnym odczycie na realnym PostgreSQL (Testcontainers, nie H2) — dane są trwałe
- [ ] dodanie żartu z telefonu wymaga najwyżej dwóch dotknięć poza wpisaniem treści

## Testy
- IT (`*IT`, Testcontainers): utworzenie i odczyt żartu; odrzucenie pustej treści (`400` `ProblemDetail`); `401` bez sesji
- IT: żart zapisany i odczytany niezależnym zapytaniem/w nowej transakcji pozostaje w bazie (trwałość)
- jednostkowy serwisu: walidacja i normalizacja treści (`rejectsJokeWithoutContent`)
- frontend (Vitest + RTL): lista renderuje żarty z klienta API; dodanie żartu liczy najwyżej dwa dotknięcia poza wpisaniem treści

## Blokada
