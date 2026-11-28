---
id: T-025
epic: E-ML
title: Adapter realnego dostawcy e-mail
status: blocked
depends_on: [T-021, T-024]
manual_review: true
---

## Cel
Podłączyć wybranego przez właściciela dostawcę pod port `MagicLinkMailer`, nie
zmieniając żadnego zachowania widocznego dla użytkownika i nie osłabiając zasady,
że produkcyjne włączenie funkcji jest możliwe dopiero po rate limitingu.

## Zakres
- implementacja portu dla dostawcy wskazanego w decyzji D-ML3 (API dostawcy
  weryfikowane z jego aktualną dokumentacją dopiero w tym tasku); realny adapter
  jako jedyny zgłasza w konfiguracji produkcyjnej zdolność `wysylka` do bramki
  `MagicLinkGate` z T-020 — dopiero jego obecność (razem ze zdolnością `limiter`
  z T-024) może otworzyć bramkę w produkcji
- zachowanie wobec puli tokenów spójne z polityką z T-021 (D-ML6): pewna porażka
  wysyłki → unieważnienie wydanego tokenu, wynik niepewny → token zostaje do TTL;
  identycznie w mocku i adapterze realnym
- konfiguracja (klucz, nadawca) wyłącznie ze zmiennych środowiskowych;
  `.env.example` zaktualizowany o nazwy bez wartości; wybór implementacji wysyłki
  sterowany konfiguracją, ścieżka testowa zostaje na mocku (`CONVENTIONS.md`:
  zewnętrzne usługi w testach zamockowane); mock nadal nie istnieje w konfiguracji
  produkcyjnej (test gwardowy z T-021 pozostaje w mocy)
- co najwyżej jedna ponowna próba wysyłki wewnątrz adaptera; każda porażka: log bez
  tokenu/URL/adresata, odpowiedź HTTP bez zmian (I5, I6)
- `docs/DEPLOY.md`: kroki właściciela (konto, klucz, nadawca/domena, zmienne na
  Railway)

## Poza zakresem
- wybór dostawcy, zakładanie kont i domen, jakiekolwiek koszty (decyzje D-ML2, D-ML3)
- kolejkowanie, gwarancje dostarczenia, webhooki zwrotek

## Kryteria akceptacji
- [ ] sekrety dostawcy wyłącznie w zmiennych środowiskowych; repo bez wartości
      sekretów
- [ ] testy nie wołają realnego dostawcy (mock/zaślepka HTTP)
- [ ] porażka dostawcy: identyczna odpowiedź 202, log bez tokenu, URL i adresata
- [ ] brak konfiguracji dostawcy → brak zdolności `wysylka`, bramka zamknięta,
      oba endpointy wyłączone (404), aplikacja działa bez crasha
- [ ] test gwardowy konfiguracji produkcyjnej (brak mocka) nadal przechodzi

## Testy
- test adaptera na zaślepce HTTP: sukces, porażka, pojedynczy retry, brak sekretu
  i tokenu w logach
- IT konfiguracji: wybór implementacji portu wg zmiennych środowiskowych; brak
  konfiguracji → endpointy nieaktywne

## Blokada
Dwie niezależne blokady:
1. Bramka zakresu: epik E-ML nie jest częścią `SCOPE.md`; odblokowanie przez
   akceptację `docs/MAGIC-LINK-SCOPE-PROPOSAL.md` (D-ML1).
2. Decyzje D-ML2 (adres/nadawca/domena) i D-ML3 (dostawca i miesięczny limit
   kosztu). Nie zakładać kont ani nie generować kosztów bez zgody właściciela.
Status na `todo` zmienia właściciel po obu rozstrzygnięciach.
