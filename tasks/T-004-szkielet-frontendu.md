---
id: T-004
epic: E0
title: Szkielet frontendu
status: done
depends_on: [T-002]
manual_review: true
---

## Cel
Aplikacja React gotowa do rozbudowy, z testami wpiętymi we wspólne komendy.

## Zakres
- projekt Vite, React i TypeScript (`strict`) w `frontend/`
- pusta strona startowa z nazwą aplikacji, układ pod telefon
- moduł klienta API z jedną funkcją odpytującą healthcheck
- Vitest i React Testing Library
- `make test-fast` i `make test` obejmują także frontend, CI instaluje Node

## Poza zakresem
- widoki aplikacji, routing, stylowanie ponad minimum

## Kryteria akceptacji
- [ ] `make test-fast` uruchamia testy backendu i frontendu
- [ ] strona startowa renderuje się bez błędów w konsoli
- [ ] build produkcyjny frontendu kończy się sukcesem

## Testy
- test renderowania strony startowej
- test klienta API z zamockowaną odpowiedzią

## Blokada
