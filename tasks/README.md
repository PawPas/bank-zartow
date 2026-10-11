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
