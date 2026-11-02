---
id: T-019
epic: E1
title: Puenty alternatywne — jawny wybór zastępczej i usuwanie
status: todo
depends_on: [T-018]
manual_review: true
---

## Cel
Domknąć niezmienniki wyboru puent przy usuwaniu: usunięcie wybranej nigdy nie może po cichu wybrać innej. Gdy istnieją inne alternatywy, użytkownik musi jawnie wskazać zastępczą, a podmiana jest atomowa.

## Zakres
- `DELETE /api/jokes/{id}/punchlines/{pid}` z wersją → usuwa alternatywę
- gdy usuwana jest WYBRANA i istnieją inne alternatywy, żądanie MUSI zawierać jawnie wskazaną puentę zastępczą (`replacementId` + jej `version`); serwer w jednej transakcji usuwa wskazaną i ustawia zastępczą jako wybraną — bez automatycznego wyboru (np. najstarszej)
- brak wskazania zastępczej przy usuwaniu wybranej, gdy istnieją inne → odrzucenie (`ProblemDetail`, `409`/`400`), bez zmian stanu
- usunięcie ostatniej alternatywy pozostawia zero alternatyw (żart bez wybranej, oryginał nienaruszony)
- zastępcza musi należeć do tego samego żartu i być różna od usuwanej; niezgodność wersji usuwanej lub zastępczej → `409`
- frontend: usuwanie alternatywy; przy usuwaniu wybranej z istniejącymi innymi UI wymusza wybór zastępczej przed potwierdzeniem; teksty po polsku

## Poza zakresem
- dodawanie i pierwszy wybór alternatywy (T-018)
- propozycje AI (E2 / E-RX)

## Kryteria akceptacji
- [ ] usunięcie niewybranej alternatywy nie zmienia wybranej
- [ ] usunięcie wybranej, gdy istnieją inne, bez wskazania zastępczej jest odrzucone (`ProblemDetail`), a stan pozostaje bez zmian
- [ ] usunięcie wybranej z jawnie wskazaną zastępczą jest atomowe: usunięta znika, a zastępcza staje się wybraną
- [ ] nie ma automatycznego wyboru zastępczej (np. najstarszej) — wybór jest wyłącznie jawną decyzją użytkownika
- [ ] usunięcie ostatniej alternatywy pozostawia zero alternatyw, a oryginalna treść żartu jest nienaruszona
- [ ] zastępcza spoza żartu albo nieaktualna wersja (usuwanej lub zastępczej) → `ProblemDetail` (`400`/`409`), bez zmian stanu

## Testy
- IT (`*IT`): usunięcie niewybranej bez zmiany wybranej; odrzucenie usunięcia wybranej bez zastępczej; atomowe usunięcie z podmianą; brak automatycznego wyboru; usunięcie ostatniej → zero alternatyw; zastępcza z innego żartu odrzucona; `409` przy konflikcie wersji; przy błędzie stan niezmieniony (atomowość)
- frontend (Vitest + RTL): UI wymusza wybór zastępczej przy usuwaniu wybranej; usunięcie ostatniej zostawia pusty stan

## Blokada
