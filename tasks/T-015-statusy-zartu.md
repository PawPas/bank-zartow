---
id: T-015
epic: E1
title: Statusy żartu z dowolnymi przejściami
status: todo
depends_on: [T-014]
manual_review: true
---

## Cel
Dać żartowi status z czterema wartościami, aby użytkownik mógł prowadzić pomysł od zapisu do publikacji. Przejścia są dowolne — to narzędzie dla jednej osoby, nie workflow z regułami.

## Zakres
- migracja Flyway dodająca kolumnę `status` do `joke` (`not null`, domyślnie `pomysl`); kolejny wolny numer `V<n>`, bez edycji istniejących migracji
- cztery statusy: `pomysl`, `do_poprawy`, `gotowy`, `opublikowany`; dowolne przejścia między nimi; `opublikowany` ustawiany również ręcznie
- `PATCH /api/jokes/{id}/status {status, version}` → zmiana statusu; `409` przy konflikcie wersji; `400` `ProblemDetail` dla nieznanego statusu
- `JokeDetail` i `JokeSummary` zawierają status
- frontend: zmiana statusu w widoku szczegółów (wybór spośród czterech), etykiety po polsku

## Poza zakresem
- reguły dozwolonych przejść (celowo brak — przejścia są dowolne)
- automatyczna zmiana statusu przy publikacji z poziomu kolejki (należy do E3)
- filtr po statusie (T-017)

## Kryteria akceptacji
- [ ] nowo utworzony żart ma status `pomysl`
- [ ] `PATCH` statusu zmienia go na dowolną z czterech wartości (każde przejście jest dozwolone)
- [ ] `opublikowany` można ustawić ręcznie przez `PATCH`
- [ ] nieznany status → `400` `ProblemDetail`; nieaktualna wersja → `409` `ProblemDetail`, bez zmian
- [ ] status jest zwracany w szczegółach i na liście

## Testy
- IT (`*IT`): domyślny status po utworzeniu; przejście do każdego z czterech; ręczne ustawienie `opublikowany`; `400` dla nieznanego; `409` przy konflikcie wersji
- frontend (Vitest + RTL): zmiana statusu wywołuje klienta API i odświeża widok

## Blokada
