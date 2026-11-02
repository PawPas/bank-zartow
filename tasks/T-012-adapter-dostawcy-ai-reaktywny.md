---
id: T-012
epic: E-RX
title: Reaktywny adapter realnego dostawcy AI (za bramką go/no-go)
status: blocked
depends_on: [T-008, T-009]
manual_review: true
---

## Cel
Podłączyć realnego dostawcę AI pod port z T-009 przy użyciu reaktywnego `WebClient`, dopiero po pozytywnej decyzji go z T-008 i osobnym zatwierdzeniu właściciela.

## Zakres
- implementacja portu `AlternateEndingsGenerator` wołająca realnego dostawcę przez `WebClient`, konsumująca jego strumień do `Flux`
- konfiguracja dostawcy (adres, klucz) wyłącznie ze zmiennych środowiskowych; brak sekretów w repo i `.env.example` zaktualizowany o nazwy zmiennych
- zachowanie reguł rdzenia: limit liczby i długości, timeout, anulowanie, mapowanie błędów
- przy błędzie, przekroczeniu limitu lub niedostępności dostawcy strumień kończy się błędem; brak automatycznego przełączenia na płatną ścieżkę lub innego dostawcę
- wybór implementacji portu (mock vs realny) sterowany profilem/konfiguracją; ścieżka testowa pozostaje na mocku

## Poza zakresem
- zmiana zakresu z T-008 (to decyzja i warunki) oraz rdzenia z T-009
- jakiekolwiek obejście braku decyzji go lub braku zgody właściciela

## Kryteria akceptacji
- [ ] adapter woła dostawcę przez `WebClient` i mapuje jego strumień na `Flux` propozycji
- [ ] konfiguracja dostawcy pochodzi tylko ze zmiennych środowiskowych; repo nie zawiera sekretów
- [ ] błąd/limit/niedostępność dostawcy kończy strumień błędem bez automatycznego fallbacku
- [ ] reguły timeoutu i anulowania działają tak samo jak dla implementacji mock
- [ ] testy nie wymagają realnego dostawcy — ścieżka testowa używa mocka/zaślepki HTTP

## Testy
- test jednostkowy/integracyjny z zaślepką HTTP (bez realnego dostawcy): mapowanie strumienia na `Flux`
- test: błąd/limit/niedostępność → strumień kończy się błędem, brak fallbacku
- test: timeout i anulowanie zachowują się jak w rdzeniu

## Blokada
Task jest zablokowany do czasu: (1) rekomendacji `go` w `docs/RX-SPIKE.md` z T-008 oraz (2) osobnego, jawnego zatwierdzenia przez właściciela warunków z tego spike'u, w tym miesięcznego limitu kosztu i dopuszczalnej ścieżki dostępu (D3). Domyślny stan jest fail-closed: bez obu powyższych task pozostaje `blocked`. `depends_on` obejmuje realne taski T-008 i T-009; decyzja właściciela nie jest taskiem, więc jest tu, a nie jako zależność. Nie wolno zakładać, że subskrypcja, SDK ani hostowany dostęp dowolnego dostawcy są dozwolone, zanim potwierdzi to T-008 i zaakceptuje właściciel.
