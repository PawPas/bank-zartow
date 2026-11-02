---
id: T-010
epic: E-RX
title: Endpoint SSE strumieniujący propozycje puent
status: blocked
depends_on: [T-009]
manual_review: true
---

## Cel
Wystawić endpoint, który strumieniuje propozycje puent do przeglądarki przez SSE, oparty na reaktywnym rdzeniu z T-009 i pozostający w modelu Spring MVC.

## Zakres
- endpoint pod `/api`, np. `GET /api/jokes/{id}/alternate-endings/stream`, zwracający `text/event-stream`
- most między `Flux` z rdzenia (T-009) a odpowiedzią SSE w kontrolerze MVC (bez migracji serwera na WebFlux)
- każda propozycja jako osobne zdarzenie SSE; błąd jako zdarzenie błędu, nie ciche zakończenie
- rozłączenie klienta anuluje subskrypcję strumienia (propagacja `cancel` do rdzenia)
- oryginalny żart i wybrana puenta pozostają nienaruszone; endpoint niczego nie zapisuje samoczynnie

## Poza zakresem
- warstwa UI konsumująca strumień (to jest T-011)
- realny adapter dostawcy (to jest T-012); endpoint działa na implementacji mock z T-009
- utrwalanie propozycji w bazie (zapis należy do E2, wyłącznie z decyzji użytkownika)

## Kryteria akceptacji
- [ ] endpoint zwraca `text/event-stream` z kolejnymi propozycjami jako osobnymi zdarzeniami
- [ ] rozłączenie klienta anuluje strumień i zatrzymuje generowanie
- [ ] timeout i błąd źródła są zgłaszane klientowi jako zdarzenie błędu, a nie ciche zamknięcie
- [ ] wywołanie dla nieistniejącego żartu zwraca błąd w formacie `ProblemDetail`
- [ ] endpoint nie modyfikuje oryginalnego żartu ani wybranej puenty

## Testy
- test integracyjny (`*IT`): strumień SSE zawiera oczekiwane zdarzenia z propozycjami (źródło zamockowane)
- test: rozłączenie klienta skutkuje anulowaniem strumienia
- test: timeout/błąd źródła jest zwracany jako zdarzenie błędu
- test: żart nie istnieje → `ProblemDetail`

## Blokada
Brak w `SCOPE.md` i w taskach modelu żartu z E1 (pakiet `joke`, encja i repozytorium), do którego endpoint się odwołuje — nie istnieje żaden ukończony task dostarczający tego modelu. Dlatego `depends_on` wskazuje tylko realny task T-009 (rdzeń), a brakujący warunek E1 jest tu, a nie jako zależność od nieistniejącego identyfikatora. Odblokowanie: właściciel dopisuje i zatwierdza taski E1 dostarczające model żartu, po czym ten task przechodzi w `todo`.
