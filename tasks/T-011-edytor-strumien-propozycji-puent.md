---
id: T-011
epic: E-RX
title: Edytor konsumuje strumień propozycji puent
status: blocked
depends_on: [T-010]
manual_review: true
---

## Cel
W edytorze posta pokazywać na żywo propozycje puent przychodzące ze strumienia SSE i pozwolić użytkownikowi ręcznie zapisać wybrane jako alternatywne puenty, bez nadpisywania oryginału.

## Zakres
- przycisk „zaproponuj 3 inne puenty” w edytorze E2 uruchamia nasłuch strumienia z T-010
- propozycje pojawiają się stopniowo w miarę napływania zdarzeń SSE
- każdą propozycję można ręcznie zapisać jako alternatywną puentę; nic nie jest zapisywane automatycznie
- oryginalna i wybrana puenta pozostają nienaruszone
- możliwość przerwania nasłuchu przez użytkownika (anulowanie strumienia); obsługa błędu i timeoutu komunikatem w interfejsie
- komunikacja przez jeden moduł klienta API, teksty interfejsu po polsku

## Poza zakresem
- backend strumienia (to jest T-010) i realny dostawca (to jest T-012)
- pozostałe elementy edytora E2 (szablony, eksport) spoza tej funkcji

## Kryteria akceptacji
- [ ] propozycje pojawiają się stopniowo, w miarę napływania zdarzeń SSE
- [ ] zapis propozycji jako alternatywnej puenty następuje wyłącznie po akcji użytkownika
- [ ] oryginalna i wybrana puenta nie są nadpisywane
- [ ] użytkownik może przerwać nasłuch, a błąd/timeout jest pokazany komunikatem
- [ ] wywołania strumienia są w testach zamockowane

## Testy
- test komponentu (Vitest + React Testing Library): propozycje renderują się w miarę napływania zdarzeń
- test: zapis następuje tylko po akcji użytkownika, oryginał bez zmian
- test: przerwanie nasłuchu zatrzymuje aktualizacje
- test: błąd/timeout strumienia pokazuje komunikat

## Blokada
Brak w `SCOPE.md` i w taskach edytora posta z E2 (komponent edycji planszy i model alternatywnych puent), do którego ta funkcja się podłącza — nie istnieje żaden ukończony task dostarczający edytora. Dlatego `depends_on` wskazuje tylko realny task T-010, a brakujący warunek E2 jest opisany tutaj, nie jako zależność od nieistniejącego identyfikatora. Odblokowanie: właściciel dopisuje i zatwierdza taski E2 dostarczające edytor, po czym ten task przechodzi w `todo`.
