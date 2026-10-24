# Taski

Jeden plik to jeden task. Nazwa pliku: `T-XXX-krotki-opis.md`.

## Format

```markdown
---
id: T-001
epic: E0
title: Krótki tytuł
status: todo
depends_on: []
manual_review: false
---

## Cel
Jedno lub dwa zdania: po co ten task istnieje.

## Zakres
- co dokładnie ma powstać

## Poza zakresem
- czego ten task nie obejmuje

## Kryteria akceptacji
- [ ] sprawdzalne stwierdzenie

## Testy
- jakie testy mają powstać

## Blokada
Pusta, dopóki status nie jest `blocked`.
```

## Pola

- `status`: `todo`, `done` albo `blocked`. Task w toku rozpoznaje się po otwartym pull requeście dla jego brancha, a nie po statusie.
- `depends_on`: lista id tasków, które muszą mieć `status: done` na `main`.
- `manual_review`: `true` oznacza, że merge wymaga zgody właściciela, nawet przy pozytywnym review.

## Wybór następnego taska

Bierz task o najniższym id, który spełnia wszystkie warunki:

1. ma `status: todo`
2. wszystkie taski z `depends_on` mają `status: done` na `main`
3. nie ma otwartego pull requesta dla brancha `task/T-XXX-*`

Jeśli żaden task nie spełnia warunków, zakończ pracę bez zmian.

## Zasady

- Task ma być mały: jedna spójna zmiana, którą da się przejrzeć w jednym podejściu.
- Programista zmienia w pliku taska tylko `status` i sekcję „Blokada”. Cel, zakres i kryteria zmienia właściciel.
- `status: done` ustawia się w tym samym pull requeście, który realizuje task. Merge oznacza zakończenie.
- Epiki i ich kryteria są w `SCOPE.md`, sekcja 4.

## Indeks statusów i selektywny odczyt

- Przy wyborze taska odczytuj metadane (`id`, `status`, `depends_on`) zamiast pełnej treści wszystkich tasków. Następnie przeczytaj cały wybrany task.
- Opcjonalny indeks generuj z plików tasków; nie utrzymuj ręcznie drugiej listy statusów. Może zawierać `id`, `status`, `depends_on`, ścieżkę i aktualny PR.
- Źródłem prawdy dla statusów i zależności są pliki na aktualnym `main`; task w toku ustala się osobno na podstawie otwartych PR. Indeks nie zastępuje tej weryfikacji.

## Drafty i archiwizacja

- Propozycje epików i tasków przechowuj poza `tasks/`, dopóki właściciel nie zatwierdzi ich jako backlogu i nie zostaną przydzielone identyfikatory `T-XXX`. Draft nie uczestniczy w wyborze następnego taska.
- Przy obecnej wielkości backlogu nie archiwizuj tasków automatycznie. Archiwizację wykonuj jako osobną, uzgodnioną zmianę porządkową.
- Archiwizować wolno dopiero po merge do `main`, nie po samym ustawieniu `status: done` na branchu.
- Przenoś pliki do `tasks/archive/` z zachowaniem nazw, identyfikatorów i treści; nie usuwaj ani nie renumeruj tasków.
- Sprawdzanie `depends_on` na `main` obejmuje zarówno `tasks/`, jak i `tasks/archive/`. Zarchiwizowany task `done` nadal spełnia zależność; kandydatów wybiera się spośród aktywnych tasków.
- Archiwizacja nie może zmienić wyniku reguły wyboru następnego taska.
