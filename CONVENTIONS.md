# Konwencje

Obowiązują programistę i reviewera. Zmiana konwencji to osobny task zatwierdzony przez właściciela.

## Stack

- Java 21, Spring Boot (aktualna stabilna wersja, przypięta w `pom.xml` w tasku T-001), Maven z wrapperem (`./mvnw`)
- PostgreSQL, migracje Flyway, Spring Data JPA
- React, TypeScript, Vite, npm
- Testy: JUnit 5, AssertJ, Testcontainers, Vitest, React Testing Library

## Struktura repozytorium

```
backend/      aplikacja Spring Boot
frontend/     aplikacja React
tasks/        taski (jeden plik na task)
docs/         dokumentacja uzupełniająca
Makefile      komendy wspólne
Dockerfile    obraz produkcyjny (jedna usługa)
```

Na produkcji działa jedna usługa: Spring Boot serwuje zbudowany frontend jako zasoby statyczne.

## Komendy

Agenci i CI używają wyłącznie tych komend. Jeśli task zmienia sposób budowania, aktualizuje `Makefile`, a nie tę listę.

- `make run`: uruchamia bazę i aplikację lokalnie
- `make test-fast`: testy jednostkowe backendu i frontendu, bez Dockera
- `make test`: pełny zestaw, w tym testy integracyjne na Testcontainers

## Backend

- Pakiet główny `pl.bankzartow`, podział według funkcji, nie według warstw: `joke`, `post`, `schedule`, `auth`, `common`.
- W pakiecie funkcji: kontroler, serwis, repozytorium, encje, DTO. Kontroler nie zawiera logiki, serwis nie zna HTTP.
- API pod `/api`, JSON. Kontrolery przyjmują i zwracają DTO (rekordy), nigdy encje.
- Walidacja wejścia przez Bean Validation. Błędy w formacie `ProblemDetail`.
- Konfiguracja przez zmienne środowiskowe. Brak wartości domyślnych dla sekretów.
- Czas w UTC (`Instant`), daty planu jako `LocalDate`.

## Baza danych

- Każda zmiana schematu to nowy plik `backend/src/main/resources/db/migration/V<numer>__opis.sql`.
- Istniejących migracji się nie edytuje ani nie usuwa.
- `spring.jpa.hibernate.ddl-auto=validate`. Schemat tworzy wyłącznie Flyway.
- Nazwy tabel i kolumn w `snake_case`, tabele w liczbie pojedynczej.
- Migracja nie może usuwać ani nadpisywać danych bez osobnej zgody właściciela (to jest wyjątek).

## Frontend

- Komponenty funkcyjne, TypeScript w trybie `strict`.
- Układ projektowany najpierw pod telefon (szerokość 360 px), potem pod komputer.
- Komunikacja z backendem przez jeden moduł klienta API, nie przez `fetch` rozsiany po komponentach.
- Teksty interfejsu po polsku.

## Testy

- Testy jednostkowe: `*Test`, bez Springa i bez bazy tam, gdzie to możliwe.
- Testy integracyjne: `*IT`, prawdziwy PostgreSQL przez Testcontainers. Nie używamy H2.
- Każde kryterium akceptacji taska ma co najmniej jeden test.
- Wywołania zewnętrznych usług (w tym AI) są w testach zamockowane.
- Test nazywa zachowanie, np. `rejectsJokeWithoutContent`.

## Język

- Kod, identyfikatory, commity i opisy PR po angielsku.
- Dokumentacja, taski i teksty interfejsu po polsku.

## Git

- Branch: `task/T-XXX-krotki-opis`.
- Commit: tryb rozkazujący, np. `Add joke tag filter`. Tytuł PR: `T-XXX: tytuł taska`.
- Jeden PR realizuje jeden task. Zmiany niezwiązane z taskiem są podstawą do odrzucenia.
- Merge przez squash.

## Sekrety

- Sekrety istnieją tylko w zmiennych środowiskowych Railway i w lokalnym pliku `.env`, który jest w `.gitignore`.
- `.env.example` zawiera nazwy zmiennych bez wartości i jest aktualizowany razem z kodem, który ich używa.
