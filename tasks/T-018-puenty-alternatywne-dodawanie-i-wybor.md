---
id: T-018
epic: E1
title: Puenty alternatywne — dodawanie i pierwsza wybrana
status: todo
depends_on: [T-014]
manual_review: true
---

## Cel
Pozwolić zapisać do żartu alternatywne puenty z oznaczeniem jednej wybranej, nie naruszając kompletnej oryginalnej treści żartu. To podstawa pod późniejszą funkcję AI z E2.

## Zakres
- migracja Flyway: tabela `punchline` (`id`, `joke_id` FK, `content`, `selected` boolean, `created_at`, `version`) z częściowym unikalnym indeksem „jedna wybrana na żart” (`unique (joke_id) where selected`); kolejny wolny numer `V<n>`, bez edycji istniejących migracji
- semantyka: `joke.content` to kompletny oryginalny żart i NIE jest nadpisywany; puenty alternatywne są opcjonalne
- `POST /api/jokes/{id}/punchlines {content, version}` → dodaje alternatywę; pierwsza dodana do żartu staje się automatycznie wybraną, kolejne jako niewybrane
- `POST /api/jokes/{id}/punchlines/{pid}/select {version}` → ustawia wskazaną jako wybraną, zdejmując zaznaczenie z poprzedniej; dokładnie jedna wybrana, gdy istnieją alternatywy
- wybrana i wskazywana puenta muszą należeć do tego samego żartu (inaczej `404`/`400` `ProblemDetail`); `409` przy konflikcie wersji
- `JokeDetail` zawiera listę alternatyw z oznaczeniem wybranej
- frontend: lista alternatyw w szczegółach, dodawanie i ręczny wybór wybranej, teksty po polsku

## Poza zakresem
- usuwanie alternatyw i niezmienniki przy usuwaniu wybranej (T-019)
- propozycje AI i strumieniowanie (E2 / E-RX)
- nadpisywanie oryginalnej treści żartu

## Kryteria akceptacji
- [ ] żart bez alternatyw ma zero puent, a oryginalna treść jest nienaruszona
- [ ] pierwsza dodana alternatywa staje się automatycznie wybraną
- [ ] gdy istnieje co najmniej jedna alternatywa, dokładnie jedna jest wybrana (pilnuje tego częściowy unikalny indeks)
- [ ] wybór innej alternatywy zdejmuje zaznaczenie z poprzedniej (nadal dokładnie jedna wybrana)
- [ ] nie można wybrać puenty należącej do innego żartu (`404`/`400` `ProblemDetail`)
- [ ] dodanie alternatywy nie zmienia oryginalnej treści żartu; `409` przy konflikcie wersji

## Testy
- IT (`*IT`): brak alternatyw → brak wybranej, oryginał bez zmian; pierwsza → wybrana; wybór przełącza wybraną; częściowy unikalny indeks pilnuje jednej wybranej; wybór puenty z innego żartu odrzucony; `409` przy konflikcie wersji
- frontend (Vitest + RTL): dodanie i wybór alternatywy przez klienta API; UI oznacza wybraną

## Blokada
