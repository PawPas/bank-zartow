---
id: T-009
epic: E-RX
title: Reaktywny rdzeń propozycji puent z implementacją mock
status: todo
depends_on: []
manual_review: true
---

## Cel
Dostarczyć niezależny od dostawcy rdzeń reaktywny, który generuje strumień propozycji puent, tak aby endpoint i adapter mogły powstać później. Rdzeń działa i jest testowany wyłącznie na implementacji mock, bez żadnego realnego dostawcy.

## Zakres
- port (interfejs) w pakiecie `post`, np. `AlternateEndingsGenerator`, zwracający `Flux` propozycji dla podanego wprowadzenia i oryginalnej puenty
- implementacja mock portu emitująca deterministyczny strumień propozycji (Reactor `Flux`), domyślna w testach i w profilu lokalnym
- reguły wspólne w rdzeniu: limit liczby propozycji, limit długości pojedynczej propozycji, timeout generowania, mapowanie błędu źródła na błąd domenowy
- rdzeń pozostaje w modelu Spring MVC; użyte są tylko typy Reactor (`Mono`/`Flux`), bez migracji serwera na WebFlux i bez R2DBC
- brak automatycznego przełączania na płatnego dostawcę: wyczerpanie limitu lub błąd kończy strumień błędem, nie podmienia źródła

## Poza zakresem
- endpoint HTTP/SSE (to jest T-010)
- realny adapter dostawcy AI i `WebClient` do niego (to jest T-012, zależny od go/no-go z T-008)
- zapis propozycji do bazy i zmiany modelu żartu (należą do E1/E2)

## Kryteria akceptacji
- [ ] port zwraca `Flux` propozycji, a implementacja mock działa bez żadnego klucza API i bez sieci
- [ ] rdzeń wymusza limit liczby propozycji i limit długości; przekroczenie jest obsłużone zgodnie z regułą, nie urwaniem w pół
- [ ] przekroczenie timeoutu kończy strumień zdefiniowanym błędem domenowym, a nie zawieszeniem
- [ ] anulowanie subskrypcji strumienia zatrzymuje dalsze generowanie (brak emisji po anulowaniu)
- [ ] rdzeń nie przełącza się automatycznie na innego/płatnego dostawcę przy błędzie lub limicie

## Testy
- testy jednostkowe z `StepVerifier`: poprawny strumień propozycji z mocka
- test: anulowanie (`cancel`) zatrzymuje emisję
- test: timeout kończy strumień błędem
- test: przekroczenie limitu liczby i długości propozycji jest obsłużone zgodnie z regułą
- test: błąd źródła jest mapowany na błąd domenowy, bez automatycznego fallbacku

## Blokada
