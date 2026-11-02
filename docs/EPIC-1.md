# Epik 1: Bank żartów — model i projekt

Dokument projektowy do zatwierdzonego przez właściciela epiku 1 (E1). Opisuje model danych,
semantykę treści i puent, API, model współbieżności oraz podział na taski T-013..T-019.
Zakres i kryteria epiku są w `SCOPE.md` §4 (Epik 1) wraz z doprecyzowaniem zatwierdzonym
przez właściciela. Ten dokument niczego do zakresu nie dodaje — porządkuje decyzje projektowe.

Stan: projekt (do implementacji). Wszystkie taski E1 mają `manual_review: true`.

## 1. Cel i granice

E1 daje narzędzie do zbierania i porządkowania żartów: szybki zapis z telefonu, lista z
filtrami, statusy, tagi oraz alternatywne puenty z jedną wybraną. E1 nie obejmuje edytora
plansz, eksportu ani AI (to E2 i jego rozszerzenie E-RX), ani kolejki i wyników (E3).

Zakres jest celowo skromny. Limity i kształt API poniżej to propozycje domyślne, które można
dostroić przy implementacji, bez zmiany kryteriów akceptacji z `SCOPE.md`.

## 2. Model danych

Nazwy tabel i kolumn w `snake_case`, tabele w liczbie pojedynczej (`CONVENTIONS.md`). Schemat
tworzy wyłącznie Flyway; `spring.jpa.hibernate.ddl-auto=validate`. Czas w UTC (`Instant`).

Każda zmiana schematu to nowa migracja `V<numer>__opis.sql`. Numery nie są przydzielane w
taskach z góry — przy implementacji bierze się kolejny wolny numer, a istniejących migracji
się nie edytuje ani nie usuwa. Na `main` istnieje obecnie `V1__init.sql`.

### 2.1 `joke`
- `id` — klucz główny
- `content` — kompletny oryginalny żart; wymagany, niepusty po normalizacji
- `status` — jeden z `pomysl`, `do_poprawy`, `gotowy`, `opublikowany` (dodawany w T-015, domyślnie `pomysl`)
- `created_at`, `updated_at` — `Instant` (UTC)
- `version` — `@Version` do optymistycznej blokady

### 2.2 `tag` i `joke_tag`
- `tag`: `id`, `name` znormalizowana i unikalna globalnie
- `joke_tag`: `joke_id`, `tag_id`, z unikalnością pary (ten sam tag nie dubluje się na żarcie)
- tagi tworzone dowolnie przez użytkownika; jeden tag może należeć do wielu żartów

### 2.3 `punchline` (puenty alternatywne)
- `id`, `joke_id` (FK), `content`, `selected` (boolean), `created_at`, `version`
- częściowy unikalny indeks „jedna wybrana na żart”: `unique (joke_id) where selected`
- puenty są opcjonalne; `joke.content` pozostaje kompletnym oryginałem i nie jest przez nie nadpisywany

## 3. Semantyka treści i puent

- **Treść żartu to kompletny oryginał.** Pole `joke.content` zawiera cały żart (wraz z jego
  oryginalną puentą w treści). Puenty alternatywne są dodatkiem i nigdy nie nadpisują oryginału.
- **Alternatywy są opcjonalne.** Żart bez alternatyw ma zero wierszy `punchline` i nie ma
  wybranej puenty — to poprawny stan, a nie brak danych.
- **Dokładnie jedna wybrana, gdy istnieją alternatywy.** Przy co najmniej jednej alternatywie
  dokładnie jedna ma `selected = true`. Pilnuje tego częściowy unikalny indeks i logika serwisu.
- **Pierwsza dodana jest wybrana automatycznie.** Dodanie pierwszej alternatywy do żartu
  ustawia ją jako wybraną. Kolejne dodawane są jako niewybrane.
- **Usunięcie wybranej wymaga jawnej zastępczej.** Jeśli usuwana jest wybrana, a istnieją inne
  alternatywy, żądanie musi jawnie wskazać zastępczą (`replacementId` + jej `version`). Serwer
  w jednej transakcji usuwa wskazaną i ustawia zastępczą jako wybraną. **Nie ma automatycznego
  wyboru** (np. najstarszej). Brak wskazania → odrzucenie, stan bez zmian.
- **Usunięcie ostatniej zostawia zero.** Usunięcie jedynej alternatywy pozostawia żart bez
  alternatyw i bez wybranej; oryginał pozostaje nienaruszony.

## 4. Statusy

Cztery wartości: `pomysl`, `do_poprawy`, `gotowy`, `opublikowany`. Przejścia są **dowolne** —
to narzędzie dla jednej osoby, nie workflow z regułami. `opublikowany` można ustawić również
ręcznie (niezależnie od oznaczenia publikacji, które w E3 robi to automatycznie). Nowy żart
startuje w `pomysl`.

## 5. Tagi

Nazwa tagu jest normalizowana przed zapisem i porównaniem: małe litery, przycięcie i złożenie
białych znaków (na bazie `common/TextNormalizer`). Dzięki temu „Absurd”, „absurd ” i „ ABSURD”
to jeden tag. Ten sam tag nie dubluje się na żarcie; ponowne dodanie jest idempotentne.

## 6. Limity (propozycja domyślnych, skromne)

Wartości wstępne, do dostrojenia przy implementacji; nie są kryteriami akceptacji:

- treść żartu: 1–2000 znaków po normalizacji
- puenta alternatywna: 1–1000 znaków
- liczba alternatyw na żart: do 20
- nazwa tagu: 1–40 znaków po normalizacji
- liczba tagów na żart: do 20
- rozmiar strony listy: domyślnie 20, maksymalnie 100

## 7. API

Zgodnie z `CONVENTIONS.md`: endpointy pod `/api`, JSON, DTO jako rekordy (nigdy encje),
walidacja Bean Validation, błędy jako `ProblemDetail`. Wszystkie `/api/**` wymagają sesji
(T-006) — bez niej `401`. Żądania modyfikujące są chronione CSRF (token z ciasteczka
`XSRF-TOKEN` w nagłówku `X-XSRF-TOKEN`, jak w `frontend/src/api/client.ts`).

| Metoda i ścieżka | Opis | Task |
| --- | --- | --- |
| `POST /api/jokes` | utworzenie żartu (sama treść) | T-013 |
| `GET /api/jokes` | lista (filtry i paginacja dochodzą w T-017) | T-013, T-017 |
| `GET /api/jokes/{id}` | szczegóły | T-014 |
| `PUT /api/jokes/{id}` | edycja treści (`version`) | T-014 |
| `DELETE /api/jokes/{id}` | usunięcie (`version`) | T-014 |
| `PATCH /api/jokes/{id}/status` | zmiana statusu (`version`) | T-015 |
| `POST /api/jokes/{id}/tags` | dodanie tagu (`version`) | T-016 |
| `DELETE /api/jokes/{id}/tags/{name}` | odpięcie tagu (`version`) | T-016 |
| `POST /api/jokes/{id}/punchlines` | dodanie alternatywy (`version`) | T-018 |
| `POST /api/jokes/{id}/punchlines/{pid}/select` | wybór alternatywy (`version`) | T-018 |
| `DELETE /api/jokes/{id}/punchlines/{pid}` | usunięcie alternatywy (`version` + opcjonalna zastępcza) | T-019 |

Kształt DTO szczegółów (`JokeDetail`) jest **rozszerzalny**: T-013/T-014 zwracają treść,
czas i wersję, a kolejne taski dokładają status, tagi i alternatywy. Nie deklarujemy DTO jako
finalnego i niezmiennego, zanim dojdą alternatywy — unikamy zamykania kontraktu przed czasem.

## 8. Model współbieżności

Optymistyczna blokada przez `@Version` jest stosowana spójnie do wszystkich żądań
modyfikujących, aby równoległe zmiany nie nadpisywały się po cichu:

- każda operacja modyfikująca przyjmuje aktualną wersję odpowiedniej encji; niezgodność →
  `409` `ProblemDetail`, bez zmiany stanu
- `joke` i `punchline` mają własny `@Version`; zmiany tagów i puent traktujemy jako zmianę
  agregatu żartu i również wymagają aktualnej wersji żartu
- usunięcie wybranej puenty z podmianą przenosi `replacementId` + jego `version`; usunięcie i
  zaznaczenie zastępczej są w jednej transakcji — albo oba, albo nic (atomowość)
- konflikt zawsze kończy się `409`; frontend pokazuje komunikat i pozwala odświeżyć dane

## 9. Trwałość i restart

Kryterium E1 „dane przetrwają restart aplikacji” sprawdzamy testami integracyjnymi na realnym
PostgreSQL przez Testcontainers (nie H2). Każdy pionowy wycinek, który zapisuje dane, ma IT
potwierdzający, że dane są trwale zatwierdzone i odczytywalne w niezależnym odczycie/nowej
transakcji. Schematu nie tworzy Hibernate (`ddl-auto=validate`) — wyłącznie Flyway.

## 10. Szybkie dodawanie z telefonu

Kryterium „najwyżej dwa dotknięcia poza wpisaniem treści” rozumiemy jako: dotknięcie pola treści
(fokus) oraz dotknięcie „Dodaj” (wysłanie). To maksymalnie dwa dotknięcia nie licząc samego
pisania. Weryfikuje to test frontendu liczący interakcje poza wpisywaniem treści (T-013).

## 11. Podział na taski i zależności

Siedem małych, pionowych tasków (backend + API + UI + testy), wszystkie `manual_review: true`:

- **T-013** — model żartu, szybkie dodawanie i lista. `depends_on: [T-006]`
- **T-014** — szczegóły, edycja treści, usuwanie z kontrolą wersji. `depends_on: [T-013]`
- **T-015** — statusy z dowolnymi przejściami. `depends_on: [T-014]`
- **T-016** — tagi: normalizacja, unikalność, UI. `depends_on: [T-014]`
- **T-017** — łączne filtry statusu i tagu oraz paginacja. `depends_on: [T-015, T-016]`
- **T-018** — puenty alternatywne: dodawanie i pierwsza wybrana. `depends_on: [T-014]`
- **T-019** — puenty: jawny wybór zastępczej i usuwanie (niezmienniki). `depends_on: [T-018]`

Graf zależności (DAG, bez cykli):

```
T-006 ─▶ T-013 ─▶ T-014 ─┬─▶ T-015 ─┐
                         ├─▶ T-016 ─┴─▶ T-017
                         └─▶ T-018 ─▶ T-019
```

Pierwszy task E1 (T-013) zależy od T-006 (logowanie), a nie od T-007 (wdrożenie). Dzięki temu
bramka akceptacyjna wdrożenia na żywo (T-007, `manual_review`) nie blokuje startu prac nad E1.

## 12. Zależność od planu E-RX (PR „docs/reactive-epic-plan”, T-008..T-012)

Osobny, równoległy PR rezerwuje identyfikatory T-008..T-012 dla epiku E-RX (reaktywne
propozycje puent, rozszerzenie E2). Ten plan E1 ich nie dotyka i ich nie modyfikuje.

Istotna przyszła zależność: **T-010** (endpoint SSE propozycji puent) odwołuje się do modelu
żartu, którego w chwili pisania planu E-RX jeszcze nie było. W PR E-RX T-010 ma
`depends_on: [T-009]` oraz notatkę w sekcji „Blokada”, że brakujący warunek to model żartu z E1.
Po osobnym zmergowaniu obu planów do `main` właściciel powinien zaktualizować T-010 tak, aby
jego zależności obejmowały **T-009** (rdzeń reaktywny) oraz **T-014** (szczegóły/odczyt modelu
żartu — pakiet `joke`, encja i repozytorium, do których endpoint się odwołuje).

Tej zmiany nie wprowadzamy tutaj: gałąź i plik T-010 należą do innego PR, a `depends_on` nie
może wskazywać identyfikatora, którego nie ma jeszcze na `main`. To zadanie koordynacyjne dla
właściciela po merge, nie dla tego planu.

**T-011** (edytor konsumujący strumień) pozostaje w E-RX/E2 i jest zablokowany do czasu
powstania edytora E2 — niniejszy plan tego nie zmienia i nie wymyśla tasków E2.

## 13. Czego ten plan nie robi

- nie implementuje E1 — to wyłącznie dokumentacja i backlog
- nie tworzy tasków E2 ani E-RX i nie rozpoczyna ich prac
- nie modyfikuje gałęzi ani plików PR „docs/reactive-epic-plan” (T-008..T-012)
- nie przydziela z góry numerów migracji Flyway
- kolejność epików (0 → 1 → 2 → 3, E-RX po E2) pozostaje bez zmian
