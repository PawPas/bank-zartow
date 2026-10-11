---
id: T-002
epic: E0
title: CI na GitHub Actions
status: done
depends_on: [T-001]
manual_review: true
---

## Cel
Każdy pull request ma automatycznie uruchamiane testy, bo zielony wynik jest warunkiem merge'a.

## Zakres
- workflow `.github/workflows/ci.yml` uruchamiany dla pull requestów i dla `main`
- workflow wywołuje wyłącznie `make test`
- cache zależności Mavena
- `docs/CI.md`: jak ustawić ochronę brancha `main` z wymaganym checkiem (wykonuje właściciel)

## Poza zakresem
- wdrożenie, ustawienia repozytorium (agent nie ma do nich uprawnień)

## Kryteria akceptacji
- [ ] pull request tego taska ma zielony check CI
- [ ] celowo zepsuty test powoduje czerwony check (opisane w PR, bez zostawiania zepsutego testu)
- [ ] workflow nie zawiera żadnych sekretów

## Testy
- brak nowych testów aplikacji, weryfikacją jest sam przebieg CI

## Blokada
