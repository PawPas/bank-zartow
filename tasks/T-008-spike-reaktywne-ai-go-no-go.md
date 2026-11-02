---
id: T-008
epic: E-RX
title: Spike reaktywnego AI — decyzja go/no-go (techniczna i prawna)
status: todo
depends_on: []
manual_review: true
---

## Cel
Zanim powstanie jakikolwiek adapter realnego dostawcy AI, rozstrzygnąć na podstawie oficjalnej dokumentacji, czy reaktywne strumieniowanie propozycji puent jest wykonalne technicznie i dopuszczalne prawnie. Spike kończy się rekomendacją go/no-go dla właściciela.

## Zakres
- `docs/RX-SPIKE.md`: zapis decyzji oparty wyłącznie na oficjalnej dokumentacji i warunkach dostawcy, z linkami i datą sprawdzenia
- część techniczna: czy dostawca udostępnia API strumieniowe (np. SSE/stream) nadające się do konsumpcji przez `WebClient` i `Flux`, jakie są limity i jak zgłaszane są błędy
- część prawna: czy regulamin i licencja wybranej ścieżki dostępu pozwalają na użycie w tym projekcie; jeśli jakikolwiek punkt jest niepotwierdzony, wynik to no-go
- rekomendacja go/no-go dla konkretnej ścieżki dostępu (D3 w `SCOPE.md`) wraz z proponowanym miesięcznym limitem kosztu
- lista warunków, które właściciel musi zatwierdzić, zanim ruszy T-012

## Poza zakresem
- jakiekolwiek wywołania realnego dostawcy, klucze API, kod adaptera (to jest T-012)
- przesądzanie, że konkretna subskrypcja, SDK czy hostowany dostęp są dozwolone — to ustala spike, domyślnie fail-closed
- implementacja rdzenia reaktywnego (to jest T-009)

## Kryteria akceptacji
- [ ] `docs/RX-SPIKE.md` zawiera oddzielnie ustalenia techniczne i prawne, każde z odwołaniem do oficjalnego źródła i datą sprawdzenia
- [ ] dokument kończy się jednoznaczną rekomendacją `go` albo `no-go`; przy jakimkolwiek niepotwierdzonym punkcie rekomendacja to `no-go` (fail-closed)
- [ ] dokument nie zawiera sekretów ani nie zakłada bez dowodu, że dana ścieżka dostępu jest dozwolona
- [ ] dokument wymienia warunki, których spełnienie i zatwierdzenie przez właściciela jest wymagane przed T-012

## Testy
- brak nowych testów aplikacji; task jest dokumentacyjny, weryfikacją jest przegląd `docs/RX-SPIKE.md` przez właściciela

## Blokada
