---
id: T-016
epic: E1
title: Tagi żartu — normalizacja, unikalność i UI
status: todo
depends_on: [T-014]
manual_review: true
---

## Cel
Pozwolić użytkownikowi swobodnie tagować żarty (absurd, gry słowne, limeryk itd.) tak, aby ten sam tag zapisany różną wielkością liter czy z nadmiarem spacji był rozpoznawany jako jeden.

## Zakres
- migracja Flyway: tabela `tag` (`id`, znormalizowana `name` unikalna globalnie) oraz `joke_tag` (`joke_id`, `tag_id`) z unikalnością pary; kolejny wolny numer `V<n>`, bez edycji istniejących migracji
- tagi tworzone dowolnie przez użytkownika; normalizacja nazwy: małe litery, przycięcie i złożenie białych znaków (wykorzystaj/rozszerz `common/TextNormalizer`)
- ten sam tag nie może wystąpić dwa razy na jednym żarcie; ponowne dodanie tego samego tagu jest idempotentne (bez duplikatu, nie błąd)
- `POST /api/jokes/{id}/tags {name, version}` → dodaje tag (tworzy go, gdy nie istnieje); `DELETE /api/jokes/{id}/tags/{name}` z wersją → odpina tag od żartu
- operacje na tagach żartu przyjmują aktualną wersję żartu i zwracają `409` przy konflikcie (model współbieżności jak w `docs/EPIC-1.md`)
- `JokeDetail` zawiera listę tagów żartu
- frontend: dodawanie i odpinanie tagów w widoku szczegółów przez klienta API; UI pokazuje znormalizowaną nazwę, teksty po polsku

## Poza zakresem
- filtr po tagu i paginacja (T-017)
- zarządzanie globalną listą tagów (zmiana nazwy, usuwanie osieroconych) poza zakresem E1

## Kryteria akceptacji
- [ ] tag jest normalizowany; „Absurd”, „absurd ” i „ ABSURD” trafiają do jednego tagu
- [ ] ten sam tag nie występuje dwa razy na jednym żarcie; ponowne dodanie jest idempotentne
- [ ] żart może mieć wiele różnych tagów, a jeden tag może należeć do wielu żartów
- [ ] odpięcie tagu usuwa powiązanie z żartem i nie dotyka innych żartów z tym tagiem
- [ ] operacje na tagach wymagają sesji (`401` bez niej), są chronione CSRF i zwracają `409` przy nieaktualnej wersji żartu

## Testy
- IT (`*IT`): normalizacja do jednego tagu; idempotentne ponowne dodanie; wiele tagów na żarcie; współdzielenie tagu między żartami; odpięcie; `409` przy konflikcie wersji
- frontend (Vitest + RTL): dodanie i odpięcie tagu przez klienta API; UI pokazuje znormalizowaną nazwę

## Blokada
